package com.mark43.loyalty.interfaces.exception;

import com.mark43.loyalty.interfaces.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Catches Jakarta Bean Validation (@Valid) failures automatically.
     * Triggers when a request body fails constraints like @NotBlank or @NotEmpty.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.toList());

        ErrorResponse errorPayload = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request - Validation Failed",
                "The incoming request payload contains invalid parameters.",
                details
        );

        return new ResponseEntity<>(errorPayload, HttpStatus.BAD_REQUEST);
    }

    /**
     * Catches business logic violations (like duplicate emails or missing resources).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBusinessLogicErrors(IllegalArgumentException ex) {
        ErrorResponse errorPayload = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request - Business Rule Violation",
                ex.getMessage(),
                List.of()
        );

        return new ResponseEntity<>(errorPayload, HttpStatus.BAD_REQUEST);
    }
}