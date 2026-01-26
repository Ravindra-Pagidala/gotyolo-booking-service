package com.gotyolo.booking.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateTripRequest(
    @NotBlank(message = "Title is required")
    String title,
    
    @NotBlank(message = "Destination is required")
    String destination,
    
    @NotNull(message = "Start date is required")
    LocalDateTime startDate,
    
    @NotNull(message = "End date is required")
    LocalDateTime endDate,
    
    @NotNull(message = "Price per seat is required")
    @DecimalMin(value = "0.01", message = "Price must be positive")
    BigDecimal price,
    
    @NotNull(message = "Max capacity is required")
    @Min(value = 1, message = "Max capacity must be at least 1")
    Integer maxCapacity,
    
    @NotNull(message = "Refundable until days before is required")
    @Min(value = 0, message = "Must be 0 or positive")
    Integer refundableUntilDaysBefore,
    
    @Min(value = 0, message = "Fee percent must be 0-100")
    @Max(value = 100, message = "Fee percent must be 0-100")
    Integer cancellationFeePercent,

    Boolean publishNow
) {}
