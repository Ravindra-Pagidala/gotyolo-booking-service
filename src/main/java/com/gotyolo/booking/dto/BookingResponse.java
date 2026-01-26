package com.gotyolo.booking.dto;

import com.gotyolo.booking.enums.BookingState;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record BookingResponse(
    UUID id,
    UUID tripId,
    String userId,
    Integer numSeats,
    BookingState state,
    BigDecimal priceAtBooking,
    String paymentReference,
    String idempotencyKey,
    LocalDateTime createdAt,
    LocalDateTime expiresAt,
    LocalDateTime cancelledAt,
    BigDecimal refundAmount
) {}
