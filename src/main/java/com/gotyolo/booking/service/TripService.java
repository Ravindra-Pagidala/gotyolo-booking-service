package com.gotyolo.booking.service;

import com.gotyolo.booking.dto.*;
import com.gotyolo.booking.entity.Booking;
import com.gotyolo.booking.entity.Trip;
import com.gotyolo.booking.enums.BookingState;
import com.gotyolo.booking.enums.TripStatus;
import com.gotyolo.booking.exception.ResourceNotFoundException;
import com.gotyolo.booking.repository.BookingRepository;
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
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TripService {

    private final TripRepository tripRepository;

    private final BookingRepository bookingRepository;

    @Value("${app.at-risk.days-before-departure:7}")
    private Integer atRiskDaysBefore;

    @Value("${app.occupancy.low-threshold-percent:50}")
    private Integer lowOccupancyThreshold;

    public List<TripResponse> getPublishedTrips() {
        log.debug("Fetching all published trips");
        return tripRepository.findAllByStatus(TripStatus.PUBLISHED).stream()
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
        Trip trip = tripRepository.findByIdForUpdate(tripId)
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
                .status(request.publishNow()!=null && request.publishNow() ? TripStatus.PUBLISHED : TripStatus.DRAFT)
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

        // 1. Booking Counts
        TripMetricsResponse.BookingSummary summary = calculateBookingSummary(tripId);

        // 2. Occupancy (CONFIRMED seats only )
        int confirmedSeats = bookingRepository.countTotalSeatsByTripIdAndState(tripId, BookingState.CONFIRMED);
        double occupancyPercent = trip.getMaxCapacity() > 0
                ? (double) confirmedSeats / trip.getMaxCapacity() * 100 : 0.0;

        // 3. CORRECTED REVENUE CALCULATION
        TripMetricsResponse.FinancialSummary finances = calculateFinancialMetrics(tripId);

        return new TripMetricsResponse(
                trip.getId(), trip.getTitle(),
                Math.round(occupancyPercent * 100.0) / 100.0,
                trip.getMaxCapacity(), confirmedSeats, trip.getAvailableSeats(),
                summary, finances
        );
    }

    private TripMetricsResponse.BookingSummary calculateBookingSummary(UUID tripId) {
        int confirmed = bookingRepository.findByTripIdAndState(tripId, BookingState.CONFIRMED).size();
        int pending = bookingRepository.findByTripIdAndState(tripId, BookingState.PENDING_PAYMENT).size();
        int cancelled = bookingRepository.findByTripIdAndState(tripId, BookingState.CANCELLED).size();
        int expired = bookingRepository.findByTripIdAndState(tripId, BookingState.EXPIRED).size();
        return new TripMetricsResponse.BookingSummary(confirmed, pending, cancelled, expired);
    }

    private TripMetricsResponse.FinancialSummary calculateFinancialMetrics(UUID tripId) {
        // GROSS: Only CONFIRMED bookings revenue
        BigDecimal grossRevenue = bookingRepository.calculateGrossRevenue(tripId, BookingState.CONFIRMED);

        // REFUNDS: Only CANCELLED bookings refunds
        BigDecimal refundsIssued = bookingRepository.calculateTotalRefunds(tripId);

        BigDecimal netRevenue = grossRevenue.subtract(refundsIssued.abs()).max(BigDecimal.ZERO);

        return new TripMetricsResponse.FinancialSummary(grossRevenue, refundsIssued, netRevenue);
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

    public AtRiskTripsResponse getAtRiskTrips() {
        LocalDateTime cutoffDate = LocalDateTime.now().plusDays(atRiskDaysBefore);
        List<Trip> upcomingTrips = tripRepository.findAllByStartDateBeforeAndStatus(cutoffDate, TripStatus.PUBLISHED);

        List<AtRiskTripsResponse.AtRiskTrip> atRiskTrips = upcomingTrips.stream()
                .filter(trip -> calculateOccupancyPercent(trip.getMaxCapacity(),
                        trip.getMaxCapacity() - trip.getAvailableSeats()) < lowOccupancyThreshold)
                .map(trip -> new AtRiskTripsResponse.AtRiskTrip(
                        trip.getId(),
                        trip.getTitle(),
                        trip.getStartDate().toLocalDate(),
                        calculateOccupancyPercent(trip.getMaxCapacity(),
                                trip.getMaxCapacity() - trip.getAvailableSeats()),
                        "Low occupancy with imminent departure"
                ))
                .toList();

        return new AtRiskTripsResponse(atRiskTrips);
    }


    private Double calculateOccupancyPercent(Integer totalSeats, Integer bookedSeats) {
        if (totalSeats == null || totalSeats == 0) return 0.0;
        return Math.min(100.0, (double) bookedSeats / totalSeats * 100);
    }
}
