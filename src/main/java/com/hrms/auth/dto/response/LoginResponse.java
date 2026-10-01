package com.hrms.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

/**
 * Matches Angular: LoginResponse { accessToken, refreshToken, tokenType, expiresIn, user }
 * Nested UserInfoResponse record removed — uses standalone UserInfoResponse class.
 */
@Builder
public record LoginResponse(
    @JsonProperty("accessToken")  String           accessToken,
    @JsonProperty("refreshToken") String           refreshToken,
    @JsonProperty("tokenType")    String           tokenType,
    @JsonProperty("expiresIn")    long             expiresIn,
    @JsonProperty("user")         UserInfoResponse user
) {
    /** The same response without the refresh token, which is delivered in an HttpOnly cookie instead. */
    public LoginResponse withoutRefreshToken() {
        return new LoginResponse(accessToken, null, tokenType, expiresIn, user);
    }
}
