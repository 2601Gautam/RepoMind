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

    public AuthResponse register(RegisterRequest reqest)
    {
        //check if email already exists
        if(userRepository.existsByEmail(reqest.getEmail())){
            throw new RuntimeException("Email already registered");
        }


        // Create new user with bcrypt-hashed password
        // passwordEncoder.encode() runs bcrypt — irreversible one-way hash

        User user = User.builder()
                .email(reqest.getEmail())
                .passwordHash(passwordEncoder.encode(reqest.getPassword()))
                .name(reqest.getName())
                .provider("LOCAL")
                .build();

        user = userRepository.save(user);
        log.info("New user registered: {}",user.getEmail());

        String token = jwtUtil.generateToken(user.getId(),user.getEmail());
        return new AuthResponse(token,user.getId(),user.getEmail(),user.getName());
    }

    public AuthResponse login(LoginRequest request){
        //Find user by email

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow( () -> new RuntimeException("Invalid email or password"));

        //check provider - google users have no password
        if(!"LOCAL".equals(user.getProvider())){
            throw new RuntimeException("This account uses Google login. Please sign with Google.");
        }

        // passwordEncoder.matches() hashes the input and compares with stored hash
        // Never compare plain passwords — always use this method
        if(!passwordEncoder.matches(request.getPassword(),user.getPasswordHash())){
            throw new RuntimeException("Invalid email or password");
        }

        String token = jwtUtil.generateToken(user.getId(),user.getEmail());
        log.info("User logged in: {}",user.getEmail());
        return new AuthResponse(token,user.getId(),user.getEmail(),user.getName());
    }
}
