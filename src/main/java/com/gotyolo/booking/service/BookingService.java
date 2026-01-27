package com.gotyolo.booking.service;

import com.gotyolo.booking.dto.BookingResponse;
import com.gotyolo.booking.dto.CreateBookingRequest;
import com.gotyolo.booking.dto.WebhookRequest;
import com.gotyolo.booking.entity.Booking;
import com.gotyolo.booking.entity.Trip;
import com.gotyolo.booking.enums.BookingState;
import com.gotyolo.booking.exception.*;
import com.gotyolo.booking.repository.BookingRepository;
import com.gotyolo.booking.utils.NullSafeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final TripService tripService;

    @Value("${app.booking.expiry-minutes:15}")
    private Integer bookingExpiryMinutes;

    @Transactional
    public BookingResponse createBooking(UUID tripId, CreateBookingRequest request) {
        log.info("Creating booking for trip {} with {} seats for user {}",
                NullSafeUtils.safeToString(tripId),
                NullSafeUtils.safeToString(request.numSeats()),
                NullSafeUtils.safeToString(request.userId()));

        log.info("Validating create booking request...");
        validateCreateBookingRequest(request);
        log.info("Create booking request validated successfully");

        log.info("Fetching trip with pessimistic lock for tripId={}", tripId);
        Trip trip = tripService.getTripForBookingWithLock(tripId);
        log.info("Fetched trip {} with availableSeats={}", trip.getId(), trip.getAvailableSeats());

        log.info("Validating seat availability...");
        validateSeatsAvailability(trip, request.numSeats());
        log.info("Seat availability validated successfully");

        log.info("Building booking entity...");
        Booking booking = Booking.builder()
                .tripId(trip.getId())
                .userId(UUID.fromString(NullSafeUtils.safeToString(request.userId())))
                .numSeats(NullSafeUtils.safeToInt(request.numSeats()))
                .state(BookingState.PENDING_PAYMENT)
                .priceAtBooking(NullSafeUtils.safeMultiply(trip.getPrice(), request.numSeats()))
                .expiresAt(LocalDateTime.now().plusMinutes(NullSafeUtils.safeToInt(bookingExpiryMinutes)))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        log.info("Reserving seats atomically. Current availableSeats={}, requested={}",
                trip.getAvailableSeats(), request.numSeats());
        trip.setAvailableSeats(NullSafeUtils.safeSubtract(trip.getAvailableSeats(), request.numSeats()));
        trip.setUpdatedAt(LocalDateTime.now());
        log.info("Seats reserved. New availableSeats={}", trip.getAvailableSeats());

        log.info("Saving trip and booking in transaction...");
        tripService.saveTrip(trip);
        Booking savedBooking = bookingRepository.save(booking);

        log.info("Booking created: {}", NullSafeUtils.safeToString(savedBooking.getId()));
        return mapToBookingResponse(savedBooking, trip.getId());
    }

    @Transactional
    public void processPaymentWebhook(WebhookRequest request) {
        String bookingIdStr = NullSafeUtils.safeToString(request.bookingId());
        String status = NullSafeUtils.safeToString(request.status());
        String idempotencyKey = NullSafeUtils.safeToString(request.idempotencyKey());

        log.info("Processing webhook - Booking: {}, Status: {}, Key: {}", bookingIdStr, status, idempotencyKey);

        if (NullSafeUtils.isNullOrEmpty(bookingIdStr) || NullSafeUtils.isNullOrEmpty(idempotencyKey)) {
            log.warn("Invalid webhook: missing required fields");
            return;
        }

        log.info("Checking idempotency for key={}", idempotencyKey);
        if (bookingRepository.existsByIdempotencyKey(idempotencyKey)) {
            log.info("Duplicate webhook ignored: {}", idempotencyKey);
            return;
        }

        UUID bookingId = NullSafeUtils.safeParseUUID(bookingIdStr);
        if (bookingId == null) {
            log.warn("Invalid booking ID format: {}", bookingIdStr);
            return;
        }

        log.info("Fetching booking for webhook. bookingId={}", bookingId);
        Booking booking = bookingRepository.findById(bookingId).orElse(null);

        if (!isValidForWebhook(booking)) {
            log.warn("Webhook ignored: invalid booking state or null: {}", bookingIdStr);
            return;
        }

        log.info("Webhook is valid. Current booking state={}", booking.getState());

        if ("success".equalsIgnoreCase(status)) {
            log.info("Payment successful. Confirming booking {}", bookingId);
            booking.setState(BookingState.CONFIRMED);
            booking.setPaymentReference(idempotencyKey);
        } else {
            log.info("Payment failed. Expiring booking {} and releasing seats", bookingId);
            booking.setState(BookingState.EXPIRED);
            releaseSeatsForBooking(booking);
        }

        booking.setIdempotencyKey(idempotencyKey);
        booking.setUpdatedAt(LocalDateTime.now());
        bookingRepository.save(booking);

        log.info("Webhook processed successfully: {} -> {}", bookingIdStr, booking.getState());
    }

    @Transactional
    public BookingResponse cancelBooking(UUID bookingId) {
        log.info("Cancelling booking: {}", NullSafeUtils.safeToString(bookingId));

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Booking not found: " + NullSafeUtils.safeToString(bookingId)));

        log.info("Fetched booking {} with state={}", bookingId, booking.getState());

        log.info("Validating cancellation rules...");
        validateCancellation(booking);
        log.info("Cancellation validation passed");

        Trip trip = tripService.getTripById(booking.getTripId());
        log.info("Fetched trip {} for cancellation", trip.getId());

        LocalDateTime cutoffDate = calculateCutoff(trip);
        log.info("Calculated cutoffDate={}", cutoffDate);

        BigDecimal refundAmount = calculateRefundAmount(booking, trip, cutoffDate);
        log.info("Calculated refundAmount={}", refundAmount);

        booking.setState(BookingState.CANCELLED);
        booking.setRefundAmount(refundAmount);
        booking.setCancelledAt(LocalDateTime.now());
        booking.setUpdatedAt(LocalDateTime.now());

        log.info("Releasing seats for cancelled booking {}", bookingId);
        releaseSeatsForBooking(booking);

        Booking saved = bookingRepository.save(booking);
        log.info("Booking cancelled: {} refund: {}", NullSafeUtils.safeToString(bookingId),
                NullSafeUtils.safeToString(refundAmount));

        return mapToBookingResponse(saved, booking.getTripId());
    }

    public BookingResponse getBooking(UUID bookingId) {
        log.info("Fetching booking: {}", NullSafeUtils.safeToString(bookingId));

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Booking not found: " + NullSafeUtils.safeToString(bookingId)));

        log.info("Booking fetched successfully: {}", bookingId);
        return mapToBookingResponse(booking, booking.getTripId());
    }

    private BookingResponse mapToBookingResponse(Booking booking, UUID tripId) {
        log.info("Mapping Booking entity to BookingResponse. bookingId={}", booking.getId());

        return new BookingResponse(
                NullSafeUtils.safeGetUUID(booking.getId()),
                tripId,
                NullSafeUtils.safeToString(booking.getUserId()),
                NullSafeUtils.safeToInt(booking.getNumSeats()),
                NullSafeUtils.safeGetBookingState(booking.getState()),
                NullSafeUtils.safeGetBigDecimal(booking.getPriceAtBooking()),
                NullSafeUtils.safeToString(booking.getPaymentReference()),
                NullSafeUtils.safeToString(booking.getIdempotencyKey()),
                NullSafeUtils.safeGetLocalDateTime(booking.getCreatedAt()),
                NullSafeUtils.safeGetLocalDateTime(booking.getExpiresAt()),
                NullSafeUtils.safeGetLocalDateTime(booking.getCancelledAt()),
                NullSafeUtils.safeGetBigDecimal(booking.getRefundAmount())
        );
    }

    private void validateCreateBookingRequest(CreateBookingRequest request) {
        log.info("Validating CreateBookingRequest");

        if (request == null) throw new ValidationException("Booking request cannot be null");

        if (NullSafeUtils.isNullOrEmpty(request.userId())) {
            throw new ValidationException("User ID cannot be null or empty");
        }

        if (NullSafeUtils.safeToInt(request.numSeats()) == null ||
                NullSafeUtils.safeToInt(request.numSeats()) <= 0) {
            throw new ValidationException("Number of seats must be positive");
        }

        log.info("CreateBookingRequest validation successful");
    }

    private void validateSeatsAvailability(Trip trip, Integer numSeats) {
        Integer available = NullSafeUtils.safeToInt(trip.getAvailableSeats());
        Integer requested = NullSafeUtils.safeToInt(numSeats);

        log.info("Validating seats. available={}, requested={}", available, requested);

        if (available == null || requested == null || available < requested) {
            throw new ConflictException("Not enough seats available: " +
                    NullSafeUtils.safeToString(available));
        }

        log.info("Seat validation successful");
    }

    private boolean isValidForWebhook(Booking booking) {
        boolean valid = booking != null && BookingState.PENDING_PAYMENT.equals(booking.getState());
        log.info("Webhook validation result: {}", valid);
        return valid;
    }

    private void validateCancellation(Booking booking) {
        BookingState state = NullSafeUtils.safeGetBookingState(booking.getState());
        log.info("Validating cancellation. Current state={}", state);

        if (state == BookingState.CANCELLED || state == BookingState.EXPIRED) {
            throw new ConflictException("Cannot cancel booking in state: " + state);
        }
    }

    private LocalDateTime calculateCutoff(Trip trip) {
        if (trip == null || trip.getStartDate() == null || trip.getRefundableUntilDaysBefore() == null) {
            log.info("Cutoff date defaulted to now due to missing trip data");
            return LocalDateTime.now();
        }

        LocalDateTime cutoff = trip.getStartDate()
                .minusDays(trip.getRefundableUntilDaysBefore());

        log.info("Calculated cutoffDate={}", cutoff);
        return cutoff;
    }

    private BigDecimal calculateRefundAmount(Booking booking, Trip trip, LocalDateTime cutoffDate) {
        log.info("Calculating refund. bookingState={}, cutoffDate={}",
                booking.getState(), cutoffDate);

        if (LocalDateTime.now().isBefore(cutoffDate) &&
                BookingState.CONFIRMED.equals(booking.getState())) {

            Integer feePercent = NullSafeUtils.safeToInt(trip.getCancellationFeePercent());
            log.info("Cancellation fee percent={}", feePercent);

            if (feePercent != null && feePercent > 0) {
                BigDecimal fee = BigDecimal.valueOf(feePercent)
                        .divide(BigDecimal.valueOf(100), 2, BigDecimal.ROUND_HALF_UP);

                BigDecimal refund = NullSafeUtils.safeGetBigDecimal(booking.getPriceAtBooking())
                        .multiply(BigDecimal.ONE.subtract(fee));

                log.info("Refund calculated={}", refund);
                return refund;
            }
        }

        log.info("No refund applicable. Returning ZERO");
        return BigDecimal.ZERO;
    }

    private void releaseSeatsForBooking(Booking booking) {
        log.info("Releasing seats for booking {}", booking.getId());

        Trip trip = tripService.getTripById(booking.getTripId());
        log.info("Fetched trip {} with availableSeats={}", trip.getId(), trip.getAvailableSeats());

        Integer newAvailable = NullSafeUtils.safeAdd(trip.getAvailableSeats(), booking.getNumSeats());
        trip.setAvailableSeats(Math.min(newAvailable, trip.getMaxCapacity()));
        trip.setUpdatedAt(LocalDateTime.now());

        log.info("Seats released. New availableSeats={}", trip.getAvailableSeats());

        tripService.saveTrip(trip);
        log.info("Trip saved after seat release. tripId={}", trip.getId());
    }
}
