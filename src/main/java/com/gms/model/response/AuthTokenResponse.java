package com.gms.model.response;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter
@NoArgsConstructor
public class AuthTokenResponse {
    private String accessToken;
    private String tokenType = "Bearer";
    private long expiresIn;
    private boolean requirePasswordChange;

    public AuthTokenResponse(String accessToken, long expiresIn, boolean requirePasswordChange) {
        this.accessToken = accessToken;
        this.expiresIn = expiresIn;
        this.requirePasswordChange = requirePasswordChange;
    }
}