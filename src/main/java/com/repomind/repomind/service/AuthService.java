package com.repomind.repomind.service;

import com.repomind.repomind.dto.request.LoginRequest;
import com.repomind.repomind.dto.request.RegisterRequest;
import com.repomind.repomind.dto.response.AuthResponse;
import com.repomind.repomind.model.entity.User;
import com.repomind.repomind.repository.UserRepository;
import com.repomind.repomind.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthResponse register(RegisterRequest request)
    {
        //check if email already exists
        userRepository.findByEmail(request.getEmail()).ifPresent(existingUser -> {
            String provider = existingUser.getProvider();
            String message;

            if ("LOCAL".equals(provider)) {
                message = "An account with " + request.getEmail() +
                        " already exists. Please log in with your password instead.";
            } else {
                String providerName = "GOOGLE".equals(provider) ? "Google" : "GitHub";
                message = "An account with " + request.getEmail() +
                        " already exists via " + providerName + ". Please sign in with " + providerName + " instead.";
            }

            log.warn("Registration blocked — email already exists via {}: {}", provider, request.getEmail());
            throw new RuntimeException(message);
        });



        // Create new user with bcrypt-hashed password
        // passwordEncoder.encode() runs bcrypt — irreversible one-way hash

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .provider("LOCAL")
                .build();

        user = userRepository.save(user);
        log.info("New user registered: {}",user.getEmail());

        String token = jwtUtil.generateToken(user.getId(),user.getEmail());
        return new AuthResponse(token,user.getId(),user.getEmail(),user.getName(),user.getProvider());
    }

    public AuthResponse login(LoginRequest request){
        //Find user by email

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow( () -> new RuntimeException("Invalid email or password"));

        // Updated check — covers both OAuth providers
        if (!"LOCAL".equals(user.getProvider())) {
            String providerName = "GOOGLE".equals(user.getProvider()) ? "Google" : "GitHub";
            throw new RuntimeException(
                    "This account uses " + providerName + " login. Please sign in with " + providerName + "."
            );
        }

        // passwordEncoder.matches() hashes the input and compares with stored hash
        // Never compare plain passwords — always use this method
        if(!passwordEncoder.matches(request.getPassword(),user.getPasswordHash())){
            throw new RuntimeException("Invalid email or password");
        }

        String token = jwtUtil.generateToken(user.getId(),user.getEmail());
        log.info("User logged in: {}",user.getEmail());
        return new AuthResponse(token,user.getId(),user.getEmail(),user.getName(),user.getProvider());
    }
}
