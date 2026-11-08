package com.aidbridge.auth.service;

import com.aidbridge.auth.dto.request.LoginRequest;
import com.aidbridge.auth.dto.request.RegisterRequest;
import com.aidbridge.auth.dto.response.AuthResponse;
import com.aidbridge.auth.dto.response.MeResponse;
import com.aidbridge.auth.dto.response.RegisterResponse;
import com.aidbridge.auth.mapper.AuthMapper;
import com.aidbridge.organization.entity.Organization;
import com.aidbridge.organization.entity.OrganizationMembership;
import com.aidbridge.organization.repository.OrganizationMembershipRepository;
import com.aidbridge.user.entity.User;
import com.aidbridge.user.mapper.UserMapper;
import com.aidbridge.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final AuthenticationManager authenticationManager;
    private final OrganizationMembershipRepository membershipRepository;
    private final AuthMapper authMapper;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            UserMapper userMapper,
            AuthenticationManager authenticationManager,
            OrganizationMembershipRepository membershipRepository,
            AuthMapper authMapper
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
        this.authenticationManager = authenticationManager;
        this.membershipRepository = membershipRepository;
        this.authMapper = authMapper;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Email is already registered");
        }
        User user = new User(request.fullName(), email, passwordEncoder.encode(request.password()));
        User savedUser = userRepository.saveAndFlush(user);
        return userMapper.toRegisterResponse(savedUser);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        email,
                        request.password()
                )
        );
        User user = userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (user.getStatus() != User.Status.ACTIVE) {
            throw new BadCredentialsException("Invalid credentials");
        }
        AuthResponse response = new AuthResponse(userMapper.toResponse(user));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        return response;
    }

    @Transactional(readOnly = true)
    public MeResponse me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            throw new BadCredentialsException("Invalid credentials");
        }
        User user = userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (user.getStatus() != User.Status.ACTIVE) {
            throw new BadCredentialsException(("Invalid credentials"));
        }
        List<OrganizationMembership> memberships = membershipRepository
                .findByUser_IdAndStatusAndOrganization_Status(
                        user.getId(),
                        OrganizationMembership.Status.ACTIVE,
                        Organization.Status.APPROVED
                );
        return authMapper.toMeResponse(user, memberships, false);
    }
}
