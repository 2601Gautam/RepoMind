    package com.repomind.repomind.controller;

    import com.repomind.repomind.annotation.RateLimit;
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

    import java.util.HashMap;
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

        // Public endpoint (see SecurityConfig) — no authenticated user exists
        // yet, so RateLimitAspect falls back to limiting by caller IP instead
        // of user ID. Without this, registration was completely unthrottled:
        // a script could create unlimited accounts, each triggering a real SES
        // email and each getting a fresh per-user ingestion bucket — defeating
        // the "2 ingestions/hour" cost control on IngestionController entirely.
        // 5 registrations/hour per IP is generous for a real user, restrictive
        // for a script.
        @RateLimit(requests = 5, windowSeconds = 3600)
        @PostMapping("/register")
        public ResponseEntity<?> register(
                @Valid @RequestBody RegisterRequest request,
                HttpServletResponse response)
        {
            try{
                AuthResponse auth = authService.register(request);
                setJwtCookie(response,auth.getToken());
                // Return user info but NOT the token — token is in the cookie
                Map<String, Object> responseBody = new HashMap<>();
                responseBody.put("userId", auth.getUserId());
                responseBody.put("email", auth.getEmail());
                responseBody.put("name", auth.getName());
                responseBody.put("provider", auth.getProvider());

                return ResponseEntity.ok(responseBody);
            }catch (RuntimeException e){
                return ResponseEntity.badRequest().body(Map.of("error",e.getMessage()));
            }
        }

        // Public endpoint, same IP-based fallback as /register. This is the
        // brute-force guard: without it, nothing throttled repeated password
        // guesses against any account.
        @RateLimit(requests = 10, windowSeconds = 900)
        @PostMapping("/login")
        public ResponseEntity<?> login(
                @Valid @RequestBody LoginRequest request,
                HttpServletResponse response){
            try{
                AuthResponse auth = authService.login(request);
                setJwtCookie(response,auth.getToken());

                Map<String, Object> responseBody = new HashMap<>();
                responseBody.put("userId", auth.getUserId());
                responseBody.put("email", auth.getEmail());
                responseBody.put("name", auth.getName());
                responseBody.put("provider", auth.getProvider());

                return ResponseEntity.ok(responseBody);
            }catch (RuntimeException e){
                return ResponseEntity.badRequest().body(Map.of("error",e.getMessage()));
            }
        }

        @GetMapping("/me")
        public ResponseEntity<?> me(@AuthenticationPrincipal User user){
            if(user == null) return ResponseEntity.status(401).build();

            Map<String, Object> response = new HashMap<>();
            response.put("userId", user.getId());
            response.put("email", user.getEmail());
            response.put("name", user.getName());
            response.put("provider", user.getProvider());

            return ResponseEntity.ok(response);
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

            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("userId", user.getId());
            responseBody.put("email", user.getEmail());
            responseBody.put("name", user.getName());
            responseBody.put("provider", user.getProvider());
            responseBody.put("reposAnalyzed", repoCount);
            responseBody.put("conversationsStarted", conversationCount);
            responseBody.put("memberSince", user.getCreatedAt());

            return ResponseEntity.ok(responseBody);
        }
    }