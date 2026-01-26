package com.gotyolo.booking.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AtRiskTripsResponse(
    List<AtRiskTrip> atRiskTrips
) {
    public record AtRiskTrip(
        UUID tripId,
        String title,
        LocalDate departureDate,
        Double occupancyPercent,
        String reason
    ) {}
}
