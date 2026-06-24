package com.repomind.repomind.config;

import com.repomind.repomind.security.JwtFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    @Value("${app.cors.allowed-origin:http://localhost:5173}")
    private String allowedOrigin;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF — not needed for stateless JWT APIs
                // CSRF protects against form-based attacks on session-based apps
                // JWT-based apps are not vulnerable to this attack
                .csrf(csrf -> csrf.disable())

                // Configure CORS using our bean below
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // STATELESS session — Spring Security will not create HTTP sessions (which its creates by default)
                // Each request must carry its own JWT — no server-side session storage
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Configure which endpoints need authentication
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints — no token needed
                        .requestMatchers(
                                "/api/auth/**",          // login, register, oauth2 callback
                                "/oauth2/**",            // Google OAuth2 redirect URLs
                                "/login/oauth2/**"       // Spring's OAuth2 callback path
                        ).permitAll()
                        // Everything else requires a valid JWT
                        .anyRequest().authenticated()
                )

                // Add our JWT filter BEFORE Spring's default username/password filter
                // This means JWT validation happens first on every request
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)

                // Disable Spring's default login page — we have our own
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable());

        return http.build();
    }

    // BCryptPasswordEncoder — the industry standard for password hashing
    // strength=10 means 2^10 = 1024 rounds of hashing
    // Higher = more secure but slower. 10 is the standard balance.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    // AuthenticationManager is needed by AuthService to authenticate credentials
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Must be exact origin — wildcards (*) do not work with credentials: true
        config.setAllowedOrigins(List.of(allowedOrigin));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        // allowCredentials = true is required for cookies to be sent cross-origin
        // This is what makes httpOnly cookies work between Vercel and Render
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}