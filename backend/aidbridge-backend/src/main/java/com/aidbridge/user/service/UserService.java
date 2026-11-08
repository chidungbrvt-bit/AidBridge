package com.aidbridge.user.service;

import com.aidbridge.user.dto.response.UserResponse;
import com.aidbridge.user.exception.UserNotFoundException;
import com.aidbridge.user.mapper.UserMapper;
import com.aidbridge.user.repository.UserRepository;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    public UserResponse getUserById(UUID userId) {
        Assert.notNull(userId, "User ID must not be null");
        return userRepository.findById(userId)
                .map(userMapper::toResponse)
                .orElseThrow(UserNotFoundException::new);
    }

    public UserResponse getUserByEmail(String email) {
        Assert.hasText(email, "Email must not be blank");
        String normalizedEmail = email.strip().toLowerCase(Locale.ROOT);
        return userRepository.findByEmailIgnoreCase(normalizedEmail)
                .map(userMapper::toResponse)
                .orElseThrow(UserNotFoundException::new);
    }
}
