package com.aidbridge.exception;

import com.aidbridge.user.exception.UserNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Comparator;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler({org.springframework.dao.DataAccessResourceFailureException.class,
            org.springframework.transaction.CannotCreateTransactionException.class})
    public ResponseEntity<ProblemDetail> handleDatabaseUnavailable(Exception exception, HttpServletRequest request) {
        return response(problem(HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE",
                "A required service is unavailable", request.getRequestURI()));
    }

    @ExceptionHandler(com.aidbridge.shared.exception.BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusiness(
            com.aidbridge.shared.exception.BusinessException exception, HttpServletRequest request) {
        return response(problem(exception.status(), exception.code(), exception.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler({org.springframework.orm.ObjectOptimisticLockingFailureException.class,
            org.springframework.dao.CannotAcquireLockException.class})
    public ResponseEntity<ProblemDetail> handleConcurrentEdit(Exception exception, HttpServletRequest request) {
        return response(problem(HttpStatus.CONFLICT, "VERSION_CONFLICT",
                "The case has changed; refresh and try again", request.getRequestURI()));
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleUserNotFound(
            UserNotFoundException exception, HttpServletRequest request) {
        return response(problem(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND",
                "Resource not found", request.getRequestURI()));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        var fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), validationCode(error.getCode())))
                .distinct()
                .sorted(Comparator.comparing(FieldViolation::field).thenComparing(FieldViolation::code))
                .toList();

        ProblemDetail problem = problem(status, "VALIDATION_ERROR",
                "Request validation failed", path(request));
        problem.setProperty("fieldErrors", fieldErrors);
        return new ResponseEntity<>(problem, safeHeaders(headers), status);
    }

    /** Sanitize framework errors, including malformed JSON and unsupported methods. */
    @Override
    protected ResponseEntity<Object> createResponseEntity(
            Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String code = switch (status.value()) {
            case 400 -> "VALIDATION_ERROR";
            case 401 -> "UNAUTHENTICATED";
            case 403 -> "FORBIDDEN";
            case 404 -> "RESOURCE_NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 406 -> "NOT_ACCEPTABLE";
            case 409 -> "CONFLICT";
            case 413 -> "PAYLOAD_TOO_LARGE";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            default -> status.is5xxServerError() ? "INTERNAL_SERVER_ERROR" : "HTTP_ERROR";
        };
        String detail = switch (status.value()) {
            case 400 -> "Request validation failed";
            case 401 -> "Authentication is required";
            case 403 -> "Access is denied";
            case 404 -> "Resource not found";
            case 405 -> "HTTP method is not allowed";
            case 406 -> "Requested response format is not supported";
            case 409 -> "Request conflicts with the current resource state";
            case 413 -> "Request payload is too large";
            case 415 -> "Request content type is not supported";
            default -> status.is5xxServerError()
                    ? "An unexpected error occurred" : "Request could not be completed";
        };
        return new ResponseEntity<>(problem(status, code, detail, path(request)), safeHeaders(headers), status);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthenticationFailure(
            AuthenticationException exception,
            HttpServletRequest request
    ) {
        return response(problem(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                "Invalid credentials", request.getRequestURI()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(
            AccessDeniedException exception, HttpServletRequest request) {
        return response(problem(HttpStatus.FORBIDDEN, "FORBIDDEN",
                "Access is denied", request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpectedException(
            Exception exception, HttpServletRequest request) {
        return response(problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred", request.getRequestURI()));
    }

    private static ProblemDetail problem(HttpStatusCode status, String code, String detail, String path) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        HttpStatus resolved = HttpStatus.resolve(status.value());
        problem.setTitle(resolved == null ? "Request failed" : resolved.getReasonPhrase());
        problem.setType(URI.create("urn:aidbridge:error:"
                + code.toLowerCase(Locale.ROOT).replace('_', '-')));
        problem.setInstance(URI.create(path));
        problem.setProperty("code", code);
        problem.setProperty("traceId", UUID.randomUUID().toString());
        return problem;
    }

    private static HttpHeaders safeHeaders(HttpHeaders original) {
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(original);
        headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        headers.setCacheControl(CacheControl.noStore());
        return headers;
    }

    private static ResponseEntity<ProblemDetail> response(ProblemDetail problem) {
        return new ResponseEntity<>(problem, safeHeaders(new HttpHeaders()),
                HttpStatusCode.valueOf(problem.getStatus()));
    }

    private static String path(WebRequest request) {
        return ((ServletWebRequest) request).getRequest().getRequestURI();
    }

    private static String validationCode(String constraint) {
        if (constraint == null) return "INVALID_VALUE";
        return switch (constraint) {
            case "NotBlank" -> "NOT_BLANK";
            case "NotNull" -> "NOT_NULL";
            case "Email" -> "INVALID_EMAIL";
            case "Size" -> "INVALID_SIZE";
            case "Min", "DecimalMin", "Positive", "PositiveOrZero" -> "BELOW_MINIMUM";
            case "Max", "DecimalMax" -> "ABOVE_MAXIMUM";
            case "Pattern" -> "INVALID_FORMAT";
            default -> "INVALID_VALUE";
        };
    }

    public record FieldViolation(String field, String code) { }
}
