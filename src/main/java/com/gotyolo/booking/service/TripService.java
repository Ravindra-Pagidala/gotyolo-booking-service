package com.gotyolo.booking.service;

import com.gotyolo.booking.dto.*;
import com.gotyolo.booking.entity.Trip;
import com.gotyolo.booking.enums.TripStatus;
import com.gotyolo.booking.exception.ResourceNotFoundException;
import com.gotyolo.booking.repository.TripRepository;
import com.gotyolo.booking.utils.NullSafeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TripService {

    private final TripRepository tripRepository;

    @Value("${app.at-risk.days-before-departure:7}")
    private Integer atRiskDaysBefore;

    @Value("${app.occupancy.low-threshold-percent:50}")
    private Integer lowOccupancyThreshold;

    public List<TripResponse> getPublishedTrips() {
        log.debug("Fetching all published trips");
        return tripRepository.findAllByStatus(TripStatus.PUBLISHED).stream()  // FIXED: findAllByStatus
                .map(this::mapToTripResponse)
                .collect(Collectors.toList());
    }

    public TripResponse getTripDetails(UUID tripId) {
        log.debug("Fetching trip details for ID: {}", NullSafeUtils.safeToString(tripId));
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found: " + NullSafeUtils.safeToString(tripId)));
        return mapToTripResponse(trip);
    }

    public Trip getTripForBookingWithLock(UUID tripId) {
        Trip trip = tripRepository.findByIdForUpdate(tripId)  // FIXED: Correct method
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found: " + NullSafeUtils.safeToString(tripId)));

        if (!TripStatus.PUBLISHED.equals(trip.getStatus())) {
            throw new ResourceNotFoundException("Trip must be PUBLISHED: " + NullSafeUtils.safeToString(tripId));
        }
        return trip;
    }

    public Trip getTripById(UUID tripId) {
        return tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found: " + NullSafeUtils.safeToString(tripId)));
    }

    @Transactional
    public TripResponse createTrip(CreateTripRequest request) {
        log.info("Creating new trip: {}", NullSafeUtils.safeToString(request.title()));

        Trip trip = Trip.builder()
                .title(NullSafeUtils.safeToString(request.title()))
                .destination(NullSafeUtils.safeToString(request.destination()))
                .startDate(request.startDate())
                .endDate(request.endDate())
                .price(NullSafeUtils.safeGetBigDecimal(request.price()))
                .maxCapacity(NullSafeUtils.safeToInt(request.maxCapacity()))
                .availableSeats(NullSafeUtils.safeToInt(request.maxCapacity()))
                .status(TripStatus.DRAFT)
                .refundableUntilDaysBefore(NullSafeUtils.safeToInt(request.refundableUntilDaysBefore()))
                .cancellationFeePercent(NullSafeUtils.safeToInt(request.cancellationFeePercent()))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Trip savedTrip = tripRepository.save(trip);
        log.info("Trip created successfully: {}", NullSafeUtils.safeToString(savedTrip.getId()));
        return mapToTripResponse(savedTrip);
    }

    @Transactional
    public void saveTrip(Trip trip) {
        tripRepository.save(trip);
    }

    public TripMetricsResponse getTripMetrics(UUID tripId) {
        log.debug("Calculating metrics for trip: {}", NullSafeUtils.safeToString(tripId));
        Trip trip = getTripById(tripId);

        // TODO: Integrate with BookingService for real counts
        Integer bookedSeats = NullSafeUtils.safeSubtract(trip.getMaxCapacity(), trip.getAvailableSeats());
        Double occupancyPercent = calculateOccupancyPercent(trip.getMaxCapacity(), bookedSeats);

        return new TripMetricsResponse(
                trip.getId(),
                trip.getTitle(),
                occupancyPercent,
                trip.getMaxCapacity(),
                bookedSeats,
                trip.getAvailableSeats(),
                new TripMetricsResponse.BookingSummary(0, 0, 0, 0),
                new TripMetricsResponse.FinancialSummary(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
        );
    }

    private TripResponse mapToTripResponse(Trip trip) {
        return new TripResponse(
                NullSafeUtils.safeGetUUID(trip.getId()),
                NullSafeUtils.safeToString(trip.getTitle()),
                NullSafeUtils.safeToString(trip.getDestination()),
                NullSafeUtils.safeGetLocalDateTime(trip.getStartDate()),
                NullSafeUtils.safeGetLocalDateTime(trip.getEndDate()),
                NullSafeUtils.safeGetBigDecimal(trip.getPrice()),
                NullSafeUtils.safeToInt(trip.getMaxCapacity()),
                NullSafeUtils.safeToInt(trip.getAvailableSeats()),
                NullSafeUtils.safeGetTripStatus(trip.getStatus()),
                NullSafeUtils.safeToInt(trip.getRefundableUntilDaysBefore()),
                NullSafeUtils.safeToInt(trip.getCancellationFeePercent()),
                NullSafeUtils.safeGetLocalDateTime(trip.getCreatedAt())
        );
    }

    private Double calculateOccupancyPercent(Integer totalSeats, Integer bookedSeats) {
        if (totalSeats == null || totalSeats == 0) return 0.0;
        return Math.min(100.0, (double) bookedSeats / totalSeats * 100);
    }
}
