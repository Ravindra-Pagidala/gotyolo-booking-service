package com.gotyolo.booking.dto;

import com.gotyolo.booking.enums.TripStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TripResponse(
    UUID id,
    String title,
    String destination,
    LocalDateTime startDate,
    LocalDateTime endDate,
    BigDecimal price,
    Integer maxCapacity,
    Integer availableSeats,
    TripStatus status,
    Integer refundableUntilDaysBefore,
    Integer cancellationFeePercent,
    LocalDateTime createdAt
) {}
