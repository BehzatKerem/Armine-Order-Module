package com.example.armineordermodule;

public class LoginResult {

    public final String token;
    public final int expiresInMinutes;
    public final long expiresAtMillis;
    public final String authSource;
    public final String userId;
    public final String userName;
    public final String email;
    public final String defaultDatabase;
    public final String companyCode;

    public LoginResult(String token, int expiresInMinutes, long expiresAtMillis,
                        String authSource, String userId, String userName, String email,
                        String defaultDatabase, String companyCode) {
        this.token = token;
        this.expiresInMinutes = expiresInMinutes;
        this.expiresAtMillis = expiresAtMillis;
        this.authSource = authSource;
        this.userId = userId;
        this.userName = userName;
        this.email = email;
        this.defaultDatabase = defaultDatabase;
        this.companyCode = companyCode;
    }
}
