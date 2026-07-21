package com.hoang.worknest.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.hoang.worknest.exception.ServiceUnavailableException;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FileStorageService {
    private static final long AVATAR_LIMIT = 2L * 1024 * 1024;
    private static final long ATTACHMENT_LIMIT = 10L * 1024 * 1024;
    private static final long OOXML_EXPANDED_LIMIT = 50L * 1024 * 1024;
    private static final Set<String> OFFICE_EXTENSIONS = Set.of("docx", "xlsx", "pptx");

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final ClamAvService clamAvService;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    public String uploadAvatar(MultipartFile file, String folder) throws IOException {
        byte[] original = readWithinLimit(file, AVATAR_LIMIT, "Avatar exceeds 2 MiB");
        ensureImageSignature(original);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(original));
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
            throw new IllegalArgumentException("Avatar is not a decodable PNG, JPEG, or WebP image");
        }
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", encoded)) {
            throw new IllegalArgumentException("Avatar could not be safely re-encoded");
        }
        byte[] cleanBytes = encoded.toByteArray();
        if (cleanBytes.length > AVATAR_LIMIT) {
            throw new IllegalArgumentException("Re-encoded avatar exceeds 2 MiB");
        }
        return quarantineScanAndPromote(cleanBytes, "image/png", folder, "png");
    }

    public StoredFile uploadAttachment(MultipartFile file, String folder) throws IOException {
        byte[] bytes = readWithinLimit(file, ATTACHMENT_LIMIT, "Attachment exceeds 10 MiB");
        DetectedFile detected = detectAttachment(bytes, file.getOriginalFilename());
        String objectKey = quarantineScanAndPromote(bytes, detected.contentType(), folder, detected.extension());
        return new StoredFile(objectKey, detected.contentType(), bytes.length);
    }

    /** Kept for compatibility; all generic uploads now use the restrictive attachment policy. */
    public String uploadFile(MultipartFile file, String folder) throws IOException {
        return uploadAttachment(file, folder).objectKey();
    }

    public String generatePresignedUrl(String objectKey, Duration duration) {
        Duration safeDuration = duration.compareTo(Duration.ofMinutes(10)) > 0
            ? Duration.ofMinutes(10)
            : duration;
        GetObjectRequest request = GetObjectRequest.builder()
            .bucket(bucketName)
            .key(objectKey)
            .responseContentDisposition("attachment")
            .build();
        return s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(safeDuration)
                .getObjectRequest(request)
                .build())
            .url().toString();
    }

    public String generateAvatarPresignedUrl(String objectKey) {
        GetObjectRequest request = GetObjectRequest.builder()
            .bucket(bucketName)
            .key(objectKey)
            .responseContentDisposition("inline")
            .build();
        return s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(10))
                .getObjectRequest(request)
                .build())
            .url().toString();
    }

    public String getBucketName() {
        return bucketName;
    }

    private String quarantineScanAndPromote(
        byte[] bytes,
        String contentType,
        String folder,
        String extension
    ) {
        String id = UUID.randomUUID().toString();
        String quarantineKey = "quarantine/" + id;
        String cleanKey = "clean/" + sanitizeFolder(folder) + "/" + id + "." + extension;
        put(quarantineKey, bytes, "application/octet-stream");
        try {
            clamAvService.scan(bytes);
            s3Client.copyObject(CopyObjectRequest.builder()
                .sourceBucket(bucketName)
                .sourceKey(quarantineKey)
                .destinationBucket(bucketName)
                .destinationKey(cleanKey)
                .contentType(contentType)
                .metadataDirective("REPLACE")
                .build());
        } catch (RuntimeException primaryFailure) {
            try {
                s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(quarantineKey).build());
            } catch (RuntimeException cleanupFailure) {
                primaryFailure.addSuppressed(cleanupFailure);
            }
            throw primaryFailure;
        }
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(quarantineKey).build());
        } catch (RuntimeException cleanupFailure) {
            throw new ServiceUnavailableException("Unable to finalize quarantined upload", cleanupFailure);
        }
        return cleanKey;
    }

    private void put(String key, byte[] bytes, String contentType) {
        s3Client.putObject(PutObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .contentType(contentType)
            .build(), RequestBody.fromBytes(bytes));
    }

    private byte[] readWithinLimit(MultipartFile file, long limit, String message) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        if (file.getSize() > limit) {
            throw new IllegalArgumentException(message);
        }
        byte[] bytes = file.getBytes();
        if (bytes.length > limit) {
            throw new IllegalArgumentException(message);
        }
        return bytes;
    }

    private DetectedFile detectAttachment(byte[] bytes, String originalFilename) {
        if (isPng(bytes)) return new DetectedFile("image/png", "png");
        if (isJpeg(bytes)) return new DetectedFile("image/jpeg", "jpg");
        if (isWebp(bytes)) return new DetectedFile("image/webp", "webp");
        if (startsWith(bytes, "%PDF-".getBytes(StandardCharsets.US_ASCII))) {
            return new DetectedFile("application/pdf", "pdf");
        }
        String extension = extension(originalFilename);
        if (isZip(bytes) && OFFICE_EXTENSIONS.contains(extension) && isValidOoxml(bytes, extension)) {
            return new DetectedFile(switch (extension) {
                case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                default -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            }, extension);
        }
        if (("txt".equals(extension) || "csv".equals(extension)) && isUtf8Text(bytes)) {
            return new DetectedFile("csv".equals(extension) ? "text/csv" : "text/plain", extension);
        }
        throw new IllegalArgumentException(
            "Unsupported attachment. Allowed: PDF, PNG, JPEG, WebP, TXT, CSV, DOCX, XLSX, PPTX"
        );
    }

    private void ensureImageSignature(byte[] bytes) {
        if (!isPng(bytes) && !isJpeg(bytes) && !isWebp(bytes)) {
            throw new IllegalArgumentException("Avatar must be PNG, JPEG, or WebP");
        }
    }

    private boolean isValidOoxml(byte[] bytes, String extension) {
        String requiredPrefix = switch (extension) {
            case "docx" -> "word/";
            case "xlsx" -> "xl/";
            default -> "ppt/";
        };
        boolean contentTypes = false;
        boolean expectedFolder = false;
        int entries = 0;
        long expandedBytes = 0;
        byte[] buffer = new byte[8192];
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null && entries++ < 10_000) {
                String name = entry.getName();
                if (name.startsWith("/") || name.contains("../") || name.contains("..\\")) return false;
                contentTypes |= "[Content_Types].xml".equals(name);
                expectedFolder |= name.startsWith(requiredPrefix);
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    expandedBytes += read;
                    if (expandedBytes > OOXML_EXPANDED_LIMIT) return false;
                }
            }
        } catch (IOException ignored) {
            return false;
        }
        return entries <= 10_000 && contentTypes && expectedFolder;
    }

    private boolean isUtf8Text(byte[] bytes) {
        for (byte value : bytes) {
            if (value == 0) return false;
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (CharacterCodingException ex) {
            return false;
        }
    }

    private boolean isPng(byte[] b) {
        return b.length >= 8 && (b[0] & 0xff) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
            && b[4] == 0x0d && b[5] == 0x0a && b[6] == 0x1a && b[7] == 0x0a;
    }

    private boolean isJpeg(byte[] b) {
        return b.length >= 3 && (b[0] & 0xff) == 0xff && (b[1] & 0xff) == 0xd8 && (b[2] & 0xff) == 0xff;
    }

    private boolean isWebp(byte[] b) {
        return b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
            && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
    }

    private boolean isZip(byte[] b) {
        return b.length >= 4 && b[0] == 'P' && b[1] == 'K' && (b[2] == 3 || b[2] == 5 || b[2] == 7)
            && (b[3] == 4 || b[3] == 6 || b[3] == 8);
    }

    private boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) return false;
        }
        return true;
    }

    private String extension(String name) {
        if (name == null) return "";
        int separator = name.lastIndexOf('.');
        return separator < 0 ? "" : name.substring(separator + 1).toLowerCase(Locale.ROOT);
    }

    private String sanitizeFolder(String folder) {
        String sanitized = folder == null ? "uploads" : folder.replaceAll("[^A-Za-z0-9/_-]", "");
        return sanitized.isBlank() ? "uploads" : sanitized;
    }

    public record StoredFile(String objectKey, String contentType, long size) {}

    private record DetectedFile(String contentType, String extension) {}
}
