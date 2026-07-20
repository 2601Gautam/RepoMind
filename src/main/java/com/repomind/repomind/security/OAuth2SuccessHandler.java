package com.repomind.repomind.security;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.repomind.repomind.model.entity.User;
import com.repomind.repomind.repository.UserRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    @Value("${app.cors.allowed-origin:http://localhost:5173}")
    private String frontendUrl;

    @Value("${app.jwt.expiration:604800000}")
    private int jwtExpiration;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException{

        // OAuth2User contains the data Google returned about the user
        // attributes include: sub (Google's user ID), email, name, picture
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        // OAuth2AuthenticationToken tells us WHICH provider was used
        // This is how we know whether to extract Google's fields or GitHub's fields
        // Without this, you would have no idea which format the attributes are in
        String provider = ((OAuth2AuthenticationToken) authentication)
                .getAuthorizedClientRegistrationId()
                .toUpperCase();
        // provider is now "GOOGLE" or "GITHUB"

        String email;
        String name;
        String providerId;

        if("GITHUB".equals(provider)){
            // GitHub attribute mapping:
            // "id"    → GitHub's unique numeric user ID (returned as Integer)
            // "login" → GitHub username (e.g. "john-doe") — used as name fallback
            // "name"  → User's display name (can be null if not set on GitHub profile)
            // "email" → Can be null if user has set email to private on GitHub

            Integer githubId = oAuth2User.getAttribute("id");
            providerId = githubId != null ? githubId.toString() : null;

            // Use display name if set, fall back to GitHUb username
            name = oAuth2User.getAttribute("name");
            if(name == null || name.isBlank()){
                name = oAuth2User.getAttribute("login");
            }

            email = oAuth2User.getAttribute("email");

            // GitHub email can be null for users with private emails
            // In this case, construct a placeholder email from their GitHub ID
            // This is a common pattern — the user can update it later in settings
            if (email == null || email.isBlank()) {
                email = providerId + "@github.repomind.user";
                log.warn("GitHub user {} has private email, using placeholder: {}",
                        oAuth2User.getAttribute("login"), email);
            }
        }else{

            // Google attribute mapping
            providerId = oAuth2User.getAttribute("sub");
            name = oAuth2User.getAttribute("name");
            email = oAuth2User.getAttribute("email");
        }

        // Find or create user - same logic for both provider
        final String finalEmail = email;
        final String finalName = name;
        final String finalProviderId = providerId;
        final String finalProvider = provider;

        // We look up by email first — if user previously registered with email/password
        // using the same email, we link the accounts

        User user = userRepository.findByEmail(email).orElseGet(()->{
            //New User - Create account automatically from Google data
            User newUser = User.builder()
                    .email(finalEmail)
                    .name(finalName)
                    .provider(finalProvider)
                    .providerId(finalProviderId)
                    .build();
            return userRepository.save(newUser);
        });

        // If existing user, update provider info if not set
        if (user.getProviderId() == null && !user.getProvider().equals("LOCAL")) {
            user.setProviderId(finalProviderId);
            user.setProvider(finalProvider);
            userRepository.save(user);
        }else{
            //throw actual error
            throw new IllegalStateException("User already exists with provider: " + user.getProvider());
        }

        String token = jwtUtil.generateToken(user.getId(),user.getEmail());

        response.addHeader("Set-Cookie",
                String.format("jwt=%s; HttpOnly; Secure; SameSite=None; Path=/; Max-Age=%d",token,jwtExpiration/1000));

        log.info("{} OAuth2 login successful: {}", provider, finalEmail);

        // Redirect to frontend after successful login
        // Frontend reads /api/auth/me to get user details
        response.sendRedirect(frontendUrl + "/auth/callback?oauth=success");

    }


}
