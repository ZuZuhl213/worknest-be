package com.hoang.worknest.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hoang.worknest.entity.User;

@Service
public class JwtService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private final ObjectMapper objectMapper;
    private final byte[] signingKey;
    private final long accessTokenExpirationMs;
    private final String issuer;
    private final String audience;

    public JwtService(
        ObjectMapper objectMapper,
        @Value("${app.jwt.secret}") String secret,
        @Value("${app.jwt.access-token-expiration}") long accessTokenExpirationMs,
        @Value("${app.jwt.issuer:worknest}") String issuer,
        @Value("${app.jwt.audience:worknest-web}") String audience
    ) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT secret must contain at least 32 bytes");
        }
        this.objectMapper = objectMapper;
        this.signingKey = secret.getBytes(StandardCharsets.UTF_8);
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.issuer = issuer;
        this.audience = audience;
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(accessTokenExpirationMs);

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", user.getId().toString());
        claims.put("type", "access");
        claims.put("iss", issuer);
        claims.put("aud", audience);
        claims.put("jti", UUID.randomUUID().toString());
        claims.put("tokenVersion", user.getTokenVersion());
        claims.put("iat", now.getEpochSecond());
        claims.put("exp", expiresAt.getEpochSecond());

        return encode(claims);
    }

    public Instant getAccessTokenExpiresAt() {
        return Instant.now().plusMillis(accessTokenExpirationMs);
    }

    public Long extractUserId(String token) {
        return Long.valueOf((String) decodePayload(token).get("sub"));
    }

    public boolean isAccessTokenValid(String token, User user) {
        Map<String, Object> payload = decodePayload(token);
        String subject = (String) payload.get("sub");
        String type = (String) payload.get("type");
        long exp = readLong(payload.get("exp"));
        long issuedAt = readLong(payload.get("iat"));
        long tokenVersion = readLong(payload.get("tokenVersion"));

        return verifySignature(token)
            && "access".equals(type)
            && issuer.equals(payload.get("iss"))
            && audience.equals(payload.get("aud"))
            && subject != null
            && subject.equals(user.getId().toString())
            && Boolean.TRUE.equals(user.getIsActive())
            && Boolean.TRUE.equals(user.getEmailVerified())
            && tokenVersion == user.getTokenVersion()
            && issuedAt <= Instant.now().plusSeconds(30).getEpochSecond()
            && exp > Instant.now().getEpochSecond();
    }

    private String encode(Map<String, Object> claims) {
        try {
            String header = URL_ENCODER.encodeToString(
                objectMapper.writeValueAsBytes(Map.of("alg", "HS256", "typ", "JWT"))
            );
            String payload = URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(claims));
            String content = header + "." + payload;
            String signature = URL_ENCODER.encodeToString(sign(content));
            return content + "." + signature;
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to generate JWT", ex);
        }
    }

    private Map<String, Object> decodePayload(String token) {
        if (!verifySignature(token) || !verifyHeader(token)) {
            throw new IllegalArgumentException("Invalid token signature");
        }

        try {
            String[] segments = token.split("\\.");
            if (segments.length != 3) {
                throw new IllegalArgumentException("Invalid token format");
            }
            byte[] payloadBytes = URL_DECODER.decode(segments[1]);
            return objectMapper.readValue(payloadBytes, new TypeReference<>() {
            });
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid token payload", ex);
        }
    }

    private boolean verifyHeader(String token) {
        try {
            String[] segments = token.split("\\.");
            if (segments.length != 3) {
                return false;
            }
            Map<String, Object> header = objectMapper.readValue(
                URL_DECODER.decode(segments[0]),
                new TypeReference<>() { }
            );
            return "HS256".equals(header.get("alg")) && "JWT".equals(header.get("typ"));
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean verifySignature(String token) {
        try {
            String[] segments = token.split("\\.");
            if (segments.length != 3) {
                return false;
            }

            String content = segments[0] + "." + segments[1];
            byte[] expected = sign(content);
            byte[] actual = URL_DECODER.decode(segments[2]);
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception ex) {
            return false;
        }
    }

    private byte[] sign(String content) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(signingKey, HMAC_ALGORITHM));
        return mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
    }

    private long readLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}
