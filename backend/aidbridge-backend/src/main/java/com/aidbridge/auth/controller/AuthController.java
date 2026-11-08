package com.aidbridge.auth.controller;

import com.aidbridge.auth.dto.request.LoginRequest;
import com.aidbridge.auth.dto.response.AuthResponse;
import com.aidbridge.auth.dto.response.MeResponse;
import com.aidbridge.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextRepository securityContextRepository;

    @GetMapping("/csrf")
    public ResponseEntity<Map<String, String>> csrf(CsrfToken csrfToken) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of(
                "token", csrfToken.getToken(),
                "headerName", csrfToken.getHeaderName()
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        httpResponse.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        try {
            AuthResponse result = authService.login(loginRequest);
            SecurityContext context = SecurityContextHolder.getContext();
            SecurityContextHolder.clearContext();
            sessionAuthenticationStrategy.onAuthentication(
                    context.getAuthentication(),
                    httpRequest,
                    httpResponse
            );
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(
                    context,
                    httpRequest,
                    httpResponse
            );
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
        } catch (AuthenticationException exception) {
            SecurityContextHolder.clearContext();
            throw exception;
        }
    }

    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(
            Authentication authentication,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        httpResponse.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        try {
            MeResponse result = authService.me(authentication);
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
        } catch (AuthenticationException exception){
            SecurityContextHolder.clearContext();
            var session = httpRequest.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            throw exception;
        }
    }
}
