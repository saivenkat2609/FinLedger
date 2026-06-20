package com.ledger.auth.dto;

public class AuthResponse {

    private String accessToken;
    private String refreshToken;
    private String tokenType = "Bearer";
    private String username;

    public AuthResponse(String accessToken, String refreshToken, String username) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.username = username;
    }

    public String getAccessToken() { return accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public String getTokenType() { return tokenType; }
    public String getUsername() { return username; }
}
