package com.aidbridge.auth.dto.response;

import com.aidbridge.user.dto.response.UserResponse;

public record AuthResponse(
        UserResponse user
) {
}