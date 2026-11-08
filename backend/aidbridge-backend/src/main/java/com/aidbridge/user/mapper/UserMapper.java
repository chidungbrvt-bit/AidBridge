package com.aidbridge.user.mapper;

import com.aidbridge.auth.dto.response.RegisterResponse;
import com.aidbridge.user.dto.response.UserResponse;
import com.aidbridge.user.entity.User;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {
    public UserResponse toResponse(User user) {
        Objects.requireNonNull(user, "User must not be null");
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getStatus(),
                user.getPlatformRole(),
                user.getCreatedAt()
        );
    }

    public RegisterResponse toRegisterResponse(User user) {
        Objects.requireNonNull(user, "User must not be null");
        return new RegisterResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
