package com.repomind.repomind.security;


import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

// JwtUtil is a utility class — it knows nothing about Spring Security
// It only knows how to create, validate, and read JWT tokens
// Think of it as a token factory and validator
@Component
@Slf4j
public class JwtUtil {

    private final SecretKey secretKey;
    private final long expiration;

    public JwtUtil(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration}") long expiration
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
    }

    // Creates a new JWT token for a user
    // The token contains: userId (subject), email, and expiry time
    // It is signed with your secret key — cannot be faked without the key

    public String generateToken(UUID userId, String email)
    {
        return Jwts.builder()
                .subject(userId.toString())      // userId stored as the "subject" claim
                .claim("email", email)           // email stored as a custom claim
                .issuedAt(new Date())            // when the token was created
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(secretKey)             // sign with your secret key
                .compact();
    }

    // Extracts the userId from a valid token
    // Called by JwtFilter after validation to identify who is making the request
    public UUID extractUserId(String token){
        String subject  = getClaims(token).getSubject();
        return UUID.fromString(subject);
    }

    public String extractEmail(String token){
        return getClaims(token).get("email",String.class);
    }

    // Validates the token: checks signature and expiry
    // Returns true if valid, false if tampered or expired
    public boolean validateToken(String token)
    {
        try{
            getClaims(token);//throws exception if invalid
            return true;
        }catch(ExpiredJwtException e)
        {
            log.warn("JWT token expired");
        }catch(JwtException e)
        {
            log.warn("Invalid JWT token: {}",e.getMessage());
        }
        return false;
    }

    // Parses the token and returns all claims
    // Throws JwtException if signature is invalid or token is expired
    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)   // verify using same secret key used to sign
                .build()
                .parseSignedClaims(token)
                .getPayload();

    }
}
