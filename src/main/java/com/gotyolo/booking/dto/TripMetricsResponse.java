package com.gotyolo.booking.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record TripMetricsResponse(
    UUID tripId,
    String title,
    Double occupancyPercent,
    Integer totalSeats,
    Integer bookedSeats,
    Integer availableSeats,
    BookingSummary bookingSummary,
    FinancialSummary financial
) {
    public record BookingSummary(
        Integer confirmed,
        Integer pendingPayment,
        Integer cancelled,
        Integer expired
    ) {}
    
    public record FinancialSummary(
        BigDecimal grossRevenue,
        BigDecimal refundsIssued,
        BigDecimal netRevenue
    ) {}
}
