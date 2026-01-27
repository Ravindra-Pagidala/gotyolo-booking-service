package com.gotyolo.booking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private String status;           // SUCCESS, ERROR, NOT_FOUND, VALIDATION_ERROR
    private String message;          // User-friendly message
    private T data;                  // Payload (null for errors)
    private Map<String, Object> metadata;  // Timestamps, pagination, etc.
    private LocalDateTime timestamp;
    
    // Static factories for convenience
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
            .status("SUCCESS")
            .message("Request completed successfully")
            .data(data)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
            .status("SUCCESS")
            .message(message)
            .data(data)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
            .status("ERROR")
            .message(message)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    public static <T> ApiResponse<T> notFound(String message) {
        return ApiResponse.<T>builder()
            .status("NOT_FOUND")
            .message(message)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    public static <T> ApiResponse<T> validationError(Map<String, String> errors) {
        return ApiResponse.<T>builder()
            .status("VALIDATION_ERROR")
            .message("Validation failed")
            .metadata(Map.of("errors", errors))
            .timestamp(LocalDateTime.now())
            .build();
    }
}
