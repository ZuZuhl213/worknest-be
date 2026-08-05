package com.hoang.worknest.security;

import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import com.hoang.worknest.exception.GoogleAuthenticationException;
import com.hoang.worknest.exception.ServiceUnavailableException;

@Component
public class GoogleIdentityVerifier {
    private static final String GOOGLE_JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> VALID_ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");

    private final String clientId;
    private final NimbusJwtDecoder jwtDecoder;

    public GoogleIdentityVerifier(@Value("${app.google.client-id:}") String clientId) {
        this.clientId = clientId == null ? "" : clientId.trim();
        if (this.clientId.isEmpty()) {
            this.jwtDecoder = null;
        } else {
            this.jwtDecoder = NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWK_SET_URI).build();
            this.jwtDecoder.setJwtValidator(new JwtTimestampValidator());
        }
    }

    public GoogleIdentity verify(String credential) {
        if (jwtDecoder == null) {
            throw new ServiceUnavailableException("Google sign-in is not configured");
        }

        try {
            Jwt jwt = jwtDecoder.decode(credential);
            String issuer = jwt.getIssuer() == null ? null : jwt.getIssuer().toString();
            String subject = jwt.getSubject();
            String email = jwt.getClaimAsString("email");
            Boolean emailVerified = jwt.getClaimAsBoolean("email_verified");
            if (!VALID_ISSUERS.contains(issuer)
                || jwt.getAudience() == null || !jwt.getAudience().contains(clientId)
                || subject == null || subject.isBlank()
                || email == null || email.isBlank()
                || !Boolean.TRUE.equals(emailVerified)) {
                throw new GoogleAuthenticationException();
            }

            return new GoogleIdentity(
                subject,
                email,
                jwt.getClaimAsString("name"),
                jwt.getClaimAsString("picture")
            );
        } catch (JwtException ex) {
            throw new GoogleAuthenticationException();
        }
    }

    public record GoogleIdentity(String subject, String email, String fullName, String pictureUrl) {
    }
}
