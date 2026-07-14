package com.repomind.repomind.controller;

import com.repomind.repomind.dto.request.LoginRequest;
import com.repomind.repomind.dto.request.RegisterRequest;
import com.repomind.repomind.dto.response.AuthResponse;
import com.repomind.repomind.model.entity.User;
import com.repomind.repomind.repository.ConversationRepository;
import com.repomind.repomind.repository.UserRepoRepository;
import com.repomind.repomind.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final UserRepoRepository userRepoRepository;
    private final ConversationRepository conversationRepository;

    @Value("${app.jwt.expiration:604800000}")
    private int jwtExpiration;

    @PostMapping("/register")
    public ResponseEntity<?> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletResponse response)
    {
        try{
            AuthResponse auth = authService.register(request);
            setJwtCookie(response,auth.getToken());
            // Return user info but NOT the token — token is in the cookie
            return ResponseEntity.ok(Map.of(
                    "userId",auth.getUserId(),
                    "email",auth.getEmail(),
                    "name",auth.getName()
            ));
        }catch (RuntimeException e){
            return ResponseEntity.badRequest().body(Map.of("error",e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response){
        try{
            AuthResponse auth = authService.login(request);
            setJwtCookie(response,auth.getToken());

            return ResponseEntity.ok(Map.of(
                    "userId",auth.getUserId(),
                    "email",auth.getEmail(),
                    "name",auth.getName()
            ));
        }catch (RuntimeException e){
            return ResponseEntity.badRequest().body(Map.of("error",e.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal User user){
        if(user == null) return ResponseEntity.status(401).build();

        return  ResponseEntity.ok(Map.of(
                "userId", user.getId(),
                "email", user.getEmail(),
                "name", user.getName(),
                "provider", user.getProvider() // Add this line!
        ));
    }

    //Clears the JWT cookie - user is now logged out
    // Simply setting an expired cookie with the same name removes it from browser
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletResponse response){
        response.addHeader(
                "Set-Cookie",
                "jwt=; HttpOnly; Secure; SameSite=None; Path=/; Max-Age=0"
        );

        return ResponseEntity.ok(Map.of("message", "Logged out"));
    }

    // Creates and sets the httpOnly cookie containing the JWT
    // HttpOnly = JavaScript cannot read this cookie
    // Secure = only sent over HTTPS (required in production)
    // SameSite=None = required for cross-origin requests (Vercel → Render)
    // MaxAge = cookie expiry in seconds

    private void setJwtCookie(HttpServletResponse response,String token){
        response.addHeader(
                "Set-Cookie",
                String.format(
                        "jwt=%s; HttpOnly; Secure; SameSite=None; Path=/; Max-Age=%d",
                        token,
                        jwtExpiration / 1000
                )
        );
    }

    @GetMapping("/profile/stats")
    public ResponseEntity<?> getProfileStats(@AuthenticationPrincipal User user){
        if(user == null) return ResponseEntity.status(401).build();

        //Count user's repos,conversations, and chunks
        long repoCount = userRepoRepository.countByUserId(user.getId());
        long conversationCount = conversationRepository.countByUserId(user.getId());

        return ResponseEntity.ok(Map.of(
                "userId", user.getId(),
                "email", user.getEmail(),
                "name", user.getName(),
                "provider", user.getProvider(),
                "reposAnalyzed", repoCount,
                "conversationsStarted", conversationCount,
                "memberSince", user.getCreatedAt()
        ));
    }
}
