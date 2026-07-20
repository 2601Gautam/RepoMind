package com.repomind.repomind.dto.response;


import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class AuthResponse {
    // Token is NOT returned in the response body
    // It is set as an httpOnly cookie by the controller
    // We include it here so the controller can easily access it
    private String token;
    private UUID userId;
    private String email;
    private String name;
    private String provider;
}
