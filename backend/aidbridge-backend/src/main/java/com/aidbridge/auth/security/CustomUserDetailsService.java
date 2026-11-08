package com.aidbridge.auth.security;

import com.aidbridge.user.entity.User;
import com.aidbridge.user.exception.UserNotFoundException;
import com.aidbridge.user.repository.UserRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomUserDetailsService implements UserDetailsService{
    private final UserRepository userRepository;
    @Override
    public UserDetails loadUserByUsername(String email) throws UserNotFoundException {
        if (email == null || email.isBlank()) {
            throw new UsernameNotFoundException("Invalid credentials");
        }
        String normalizedEmail = email.strip().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .authorities("ROLE_" + user.getPlatformRole().name())
                .disabled(user.getStatus() != User.Status.ACTIVE)
                .build();
    }
}
