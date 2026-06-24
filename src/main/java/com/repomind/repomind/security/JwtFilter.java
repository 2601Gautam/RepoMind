package com.repomind.repomind.security;


import com.repomind.repomind.model.entity.User;
import com.repomind.repomind.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

// JwtFilter runs once per request — it reads the JWT cookie,
// validates it, loads the user, and sets their identity in Spring's SecurityContext
// After this filter runs, any controller can call SecurityContextHolder
// to get the currently authenticated user
//
// OncePerRequestFilter guarantees this runs exactly once per HTTP request
// (Spring's filter chain can sometimes trigger filters multiple times without this)
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        //step1 : extract JWT from cookie named "jwt"
        String token = extractTokenFromCookie(request);

        if(token != null && jwtUtil.validateToken(token)){

            // Step 2: Extract userId from validated token
            var userId = jwtUtil.extractUserId(token);

            // Step 3: Load the actual User object from database
            // We load from DB to ensure the user still exists and is active
            User user = userRepository.findById(userId).orElse(null);

            if(user != null)
            {
                // Step 4: Create Spring Security authentication object
                // List.of() = no roles/authorities for now (add in Tier 3 if needed)
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                user, // principal — the authenticated user object
                                null,// credentials — null because JWT already proved identity
                                List.of() // authorities-empty for now
                        );

                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );

                // Step 5: Put the authentication in SecurityContext
                // This is what makes the user "logged in" for this request
                // Any code in this request can now call:
                // SecurityContextHolder.getContext().getAuthentication().getPrincipal()
                // to get the User object
                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("Autheticated user: {}",user.getEmail());
            }
        }
        // Step 6: Continue the filter chain regardless
        // If token was invalid, SecurityContext stays empty = unauthenticated
        // Spring Security config will then return 401 for protected endpoints
        filterChain.doFilter(request, response);

    }
    private String extractTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;

        return Arrays.stream(cookies)
                .filter(c->"jwt".equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }



}
