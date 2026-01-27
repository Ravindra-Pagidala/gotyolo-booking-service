package com.gotyolo.booking.service;

import com.gotyolo.booking.dto.AtRiskTripsResponse;
import com.gotyolo.booking.dto.CreateTripRequest;
import com.gotyolo.booking.dto.TripMetricsResponse;
import com.gotyolo.booking.dto.TripResponse;
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
        log.info("Fetching all PUBLISHED trips from database");

        List<TripResponse> trips = tripRepository.findAllByStatus(TripStatus.PUBLISHED).stream()
                .map(this::mapToTripResponse)
                .collect(Collectors.toList());

        log.info("Fetched {} published trips", trips.size());
        return trips;
    }

    public TripResponse getTripDetails(UUID tripId) {
        log.info("Fetching trip details for tripId={}",
                NullSafeUtils.safeToString(tripId));

        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Trip not found: " + NullSafeUtils.safeToString(tripId)));

        log.info("Trip found successfully for tripId={}",
                NullSafeUtils.safeToString(tripId));

        return mapToTripResponse(trip);
    }

    public Trip getTripForBookingWithLock(UUID tripId) {
        log.info("Fetching trip with PESSIMISTIC LOCK for tripId={}",
                NullSafeUtils.safeToString(tripId));

        Trip trip = tripRepository.findByIdForUpdate(tripId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Trip not found: " + NullSafeUtils.safeToString(tripId)));

        log.info("Fetched trip {} with status={}", trip.getId(), trip.getStatus());

        if (!TripStatus.PUBLISHED.equals(trip.getStatus())) {
            log.info("Trip is not in PUBLISHED state. Current state={}", trip.getStatus());
            throw new ResourceNotFoundException(
                    "Trip must be PUBLISHED: " + NullSafeUtils.safeToString(tripId));
        }

        return trip;
    }

    public Trip getTripById(UUID tripId) {
        log.info("Fetching trip by ID tripId={}",
                NullSafeUtils.safeToString(tripId));

        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Trip not found: " + NullSafeUtils.safeToString(tripId)));

        log.info("Trip fetched successfully tripId={}",
                NullSafeUtils.safeToString(tripId));

        return trip;
    }

    @Transactional
    public TripResponse createTrip(CreateTripRequest request) {
        log.info("Creating new trip: {}",
                NullSafeUtils.safeToString(request.title()));

        log.info("Building Trip entity...");
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

        log.info("Saving trip entity to database...");
        Trip savedTrip = tripRepository.save(trip);

        log.info("Trip created successfully: {}",
                NullSafeUtils.safeToString(savedTrip.getId()));

        return mapToTripResponse(savedTrip);
    }

    @Transactional
    public void saveTrip(Trip trip) {
        log.info("Saving trip {} to database",
                NullSafeUtils.safeToString(trip.getId()));

        tripRepository.save(trip);

        log.info("Trip saved successfully tripId={}",
                NullSafeUtils.safeToString(trip.getId()));
    }

    public TripMetricsResponse getTripMetrics(UUID tripId) {
        log.info("Calculating metrics for tripId={}",
                NullSafeUtils.safeToString(tripId));

        Trip trip = getTripById(tripId);

        log.info("Calculating booking summary...");
        TripMetricsResponse.BookingSummary summary = calculateBookingSummary(tripId);

        log.debug("Fetching confirmed seats count from DB...");

        Integer confirmedSeatsRaw = bookingRepository.countTotalSeatsByTripIdAndState(tripId, BookingState.CONFIRMED);
        int confirmedSeats = confirmedSeatsRaw != null ? confirmedSeatsRaw : 0;

        log.info("Confirmed seats = {}", confirmedSeats);

        double occupancyPercent = trip.getMaxCapacity() > 0
                ? (double) confirmedSeats / trip.getMaxCapacity() * 100
                : 0.0;

        log.info("Calculated occupancyPercent={}", occupancyPercent);

        log.info("Calculating financial metrics...");
        TripMetricsResponse.FinancialSummary finances =
                calculateFinancialMetrics(tripId);

        return new TripMetricsResponse(
                trip.getId(),
                trip.getTitle(),
                Math.round(occupancyPercent * 100.0) / 100.0,
                trip.getMaxCapacity(),
                confirmedSeats,
                trip.getAvailableSeats(),
                summary,
                finances
        );
    }

    private TripMetricsResponse.BookingSummary calculateBookingSummary(UUID tripId) {
        log.info("Calculating booking summary for tripId={}",
                NullSafeUtils.safeToString(tripId));

        int confirmed = bookingRepository.findByTripIdAndState(tripId, BookingState.CONFIRMED).size();
        int pending = bookingRepository.findByTripIdAndState(tripId, BookingState.PENDING_PAYMENT).size();
        int cancelled = bookingRepository.findByTripIdAndState(tripId, BookingState.CANCELLED).size();
        int expired = bookingRepository.findByTripIdAndState(tripId, BookingState.EXPIRED).size();

        log.info("Booking summary -> confirmed={}, pending={}, cancelled={}, expired={}",
                confirmed, pending, cancelled, expired);

        return new TripMetricsResponse.BookingSummary(
                confirmed, pending, cancelled, expired);
    }

    private TripMetricsResponse.FinancialSummary calculateFinancialMetrics(UUID tripId) {
        log.info("Calculating financial metrics for tripId={}",
                NullSafeUtils.safeToString(tripId));

        BigDecimal grossRevenue = bookingRepository.calculateGrossRevenue(tripId, BookingState.CONFIRMED);

        BigDecimal refundsIssued = bookingRepository.calculateTotalRefunds(tripId);

        BigDecimal netRevenue = grossRevenue.subtract(refundsIssued.abs()).max(BigDecimal.ZERO);

        log.info("Financials -> grossRevenue={}, refundsIssued={}, netRevenue={}",
                grossRevenue, refundsIssued, netRevenue);

        return new TripMetricsResponse.FinancialSummary(
                grossRevenue, refundsIssued, netRevenue);
    }

    private TripResponse mapToTripResponse(Trip trip) {
        log.info("Mapping Trip entity to TripResponse. tripId={}",
                NullSafeUtils.safeToString(trip.getId()));

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
        log.info("Fetching at-risk trips configuration: daysBefore={}, lowThreshold={}",
                atRiskDaysBefore, lowOccupancyThreshold);

        LocalDateTime cutoffDate = LocalDateTime.now().plusDays(atRiskDaysBefore);
        log.info("Calculated cutoffDate for at-risk trips={}", cutoffDate);

        List<Trip> upcomingTrips = tripRepository.findAllByStartDateBeforeAndStatus(cutoffDate, TripStatus.PUBLISHED);

        log.info("Fetched {} upcoming published trips", upcomingTrips.size());

        List<AtRiskTripsResponse.AtRiskTrip> atRiskTrips = upcomingTrips.stream()
                .filter(trip -> calculateOccupancyPercent(
                        trip.getMaxCapacity(),
                        trip.getMaxCapacity() - trip.getAvailableSeats())
                        < lowOccupancyThreshold)
                .map(trip -> {
                    Double occupancy = calculateOccupancyPercent(
                            trip.getMaxCapacity(),
                            trip.getMaxCapacity() - trip.getAvailableSeats());

                    log.info("Trip {} occupancy={}%, marked as at-risk",
                            trip.getId(), occupancy);

                    return new AtRiskTripsResponse.AtRiskTrip(
                            trip.getId(),
                            trip.getTitle(),
                            trip.getStartDate().toLocalDate(),
                            occupancy,
                            "Low occupancy with imminent departure"
                    );
                })
                .toList();

        log.info("Total at-risk trips identified={}", atRiskTrips.size());
        return new AtRiskTripsResponse(atRiskTrips);
    }

    private Double calculateOccupancyPercent(Integer totalSeats, Integer bookedSeats) {
        log.info("Calculating occupancy percent. totalSeats={}, bookedSeats={}",
                totalSeats, bookedSeats);

        if (totalSeats == null || totalSeats == 0) {
            log.info("Total seats is null or zero. Returning 0%");
            return 0.0;
        }

        Double percent = Math.min(100.0,
                (double) bookedSeats / totalSeats * 100);

        log.info("Calculated occupancy percent={}", percent);
        return percent;
    }
}
