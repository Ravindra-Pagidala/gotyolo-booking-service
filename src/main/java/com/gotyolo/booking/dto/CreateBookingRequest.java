package com.gotyolo.booking.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateBookingRequest(
    @NotNull(message = "User ID is required")
    String userId,
    
    @NotNull(message = "Number of seats is required")
    @Min(value = 1, message = "Minimum 1 seat required")
    Integer numSeats
) {}
