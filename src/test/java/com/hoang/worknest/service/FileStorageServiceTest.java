package com.hoang.worknest.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import com.hoang.worknest.exception.ServiceUnavailableException;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class FileStorageServiceTest {
    private S3Client s3Client;
    private ClamAvService clamAvService;
    private FileStorageService storage;

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);
        clamAvService = mock(ClamAvService.class);
        storage = new FileStorageService(s3Client, mock(S3Presigner.class), clamAvService);
        ReflectionTestUtils.setField(storage, "bucketName", "private-test-bucket");
    }

    @Test
    void ignoresClientMimeAndDetectsPdfFromBytes() throws Exception {
        var file = new MockMultipartFile("file", "payload.exe", "application/x-msdownload",
            "%PDF-1.7\nclean".getBytes(StandardCharsets.US_ASCII));
        FileStorageService.StoredFile stored = storage.uploadAttachment(file, "tasks/1");
        assertEquals("application/pdf", stored.contentType());
        assertTrue(stored.objectKey().endsWith(".pdf"));
        verify(clamAvService).scan(any(byte[].class));
    }

    @Test
    void rejectsSvgHtmlExecutableAndOversizedFiles() {
        assertThrows(IllegalArgumentException.class, () -> storage.uploadAttachment(
            new MockMultipartFile("file", "image.svg", "image/svg+xml", "<svg/>".getBytes()), "tasks/1"));
        assertThrows(IllegalArgumentException.class, () -> storage.uploadAttachment(
            new MockMultipartFile("file", "page.html", "text/html", "<script>x</script>".getBytes()), "tasks/1"));
        assertThrows(IllegalArgumentException.class, () -> storage.uploadAttachment(
            new MockMultipartFile("file", "run.exe", "application/octet-stream", new byte[] {'M', 'Z', 0, 0}),
            "tasks/1"));
        assertThrows(IllegalArgumentException.class, () -> storage.uploadAttachment(
            new MockMultipartFile("file", "large.pdf", "application/pdf", new byte[10 * 1024 * 1024 + 1]),
            "tasks/1"));
    }

    @Test
    void avatarMustDecodeAndIsReencodedAsPng() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> storage.uploadAvatar(
            new MockMultipartFile("file", "fake.png", "image/png", "not-an-image".getBytes()), "avatars"));

        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(image, "png", png);
        String key = storage.uploadAvatar(
            new MockMultipartFile("file", "avatar.jpg", "image/jpeg", png.toByteArray()), "avatars");
        assertTrue(key.endsWith(".png"));
    }

    @Test
    void rejectsOversizedAvatarBeforeScanningOrUploading() {
        byte[] png = new byte[24];
        png[0] = (byte) 0x89;
        png[1] = 'P';
        png[2] = 'N';
        png[3] = 'G';
        png[4] = 0x0d;
        png[5] = 0x0a;
        png[6] = 0x1a;
        png[7] = 0x0a;
        png[12] = 'I';
        png[13] = 'H';
        png[14] = 'D';
        png[15] = 'R';
        png[16] = 0x00;
        png[17] = 0x01;
        png[18] = 0x00;
        png[19] = 0x00;
        png[20] = 0x00;
        png[21] = 0x01;

        assertThrows(IllegalArgumentException.class, () -> storage.uploadAvatar(
            new MockMultipartFile("file", "large.png", "image/png", png), "avatars"));

        verify(clamAvService, never()).scan(any(byte[].class));
        verify(s3Client, never()).putObject(
            org.mockito.ArgumentMatchers.any(software.amazon.awssdk.services.s3.model.PutObjectRequest.class),
            org.mockito.ArgumentMatchers.any(software.amazon.awssdk.core.sync.RequestBody.class)
        );
    }

    @Test
    void scannerFailureNeverPromotesQuarantinedObject() {
        doThrow(new ServiceUnavailableException("scanner unavailable"))
            .when(clamAvService).scan(any(byte[].class));
        var file = new MockMultipartFile("file", "doc.pdf", "application/pdf",
            "%PDF-1.7\nclean".getBytes(StandardCharsets.US_ASCII));
        assertThrows(ServiceUnavailableException.class, () -> storage.uploadAttachment(file, "tasks/1"));
        verify(s3Client, never()).copyObject(any(CopyObjectRequest.class));
        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }
}
