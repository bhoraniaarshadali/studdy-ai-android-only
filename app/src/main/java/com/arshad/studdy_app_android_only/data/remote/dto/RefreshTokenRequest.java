package com.arshad.studdy_app_android_only.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/**
2:  * Request body for token refresh endpoint.
3:  */
public class RefreshTokenRequest {

    @SerializedName("refresh_token")
    public String refreshToken;

    public RefreshTokenRequest(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
