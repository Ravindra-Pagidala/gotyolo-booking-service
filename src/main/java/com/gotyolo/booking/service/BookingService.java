package com.gotyolo.booking.service;

import com.gotyolo.booking.dto.*;
import com.gotyolo.booking.entity.Booking;
import com.gotyolo.booking.entity.Trip;
import com.gotyolo.booking.enums.BookingState;
import com.gotyolo.booking.exception.ConflictException;
import com.gotyolo.booking.exception.ResourceNotFoundException;
import com.gotyolo.booking.exception.ValidationException;
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

        validateCreateBookingRequest(request);

        // FIXED: Use PESSIMISTIC lock for concurrency safety
        Trip trip = tripService.getTripForBookingWithLock(tripId);
        validateSeatsAvailability(trip, request.numSeats());

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

        // Atomic seat reservation
        trip.setAvailableSeats(NullSafeUtils.safeSubtract(trip.getAvailableSeats(), request.numSeats()));
        trip.setUpdatedAt(LocalDateTime.now());

        // Save BOTH in transaction
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

        //  Proper idempotency check
        if (bookingRepository.existsByIdempotencyKey(idempotencyKey)) {
            log.info("Duplicate webhook ignored: {}", idempotencyKey);
            return;
        }

        UUID bookingId = NullSafeUtils.safeParseUUID(bookingIdStr);
        if (bookingId == null) {
            log.warn("Invalid booking ID format: {}", bookingIdStr);
            return;
        }

        Booking booking = bookingRepository.findById(bookingId).orElse(null);
        if (!isValidForWebhook(booking)) {
            log.warn("Webhook ignored: invalid booking state or null: {}", bookingIdStr);
            return;
        }

        if ("success".equalsIgnoreCase(status)) {
            booking.setState(BookingState.CONFIRMED);
            booking.setPaymentReference(idempotencyKey);  // Use idempotencyKey
        } else {
            booking.setState(BookingState.EXPIRED);
            // Release seats for failed payments
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
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + NullSafeUtils.safeToString(bookingId)));

        validateCancellation(booking);
        Trip trip = tripService.getTripById(booking.getTripId());

        LocalDateTime cutoffDate = calculateCutoff(trip);
        BigDecimal refundAmount = calculateRefundAmount(booking, trip, cutoffDate);
        boolean releaseSeats = shouldReleaseSeats(cutoffDate);

        booking.setState(BookingState.CANCELLED);
        booking.setRefundAmount(refundAmount);
        booking.setCancelledAt(LocalDateTime.now());
        booking.setUpdatedAt(LocalDateTime.now());

        if (releaseSeats) {
            releaseSeatsForBooking(booking);
        }

        Booking saved = bookingRepository.save(booking);
        log.info("Booking cancelled: {} refund: {}", NullSafeUtils.safeToString(bookingId), NullSafeUtils.safeToString(refundAmount));
        return mapToBookingResponse(saved, booking.getTripId());
    }

    public BookingResponse getBooking(UUID bookingId) {
        log.debug("Fetching booking: {}", NullSafeUtils.safeToString(bookingId));
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + NullSafeUtils.safeToString(bookingId)));
        return mapToBookingResponse(booking, booking.getTripId());
    }

    private BookingResponse mapToBookingResponse(Booking booking, UUID tripId) {
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
        if (request == null) throw new ValidationException("Booking request cannot be null");
        if (NullSafeUtils.isNullOrEmpty(request.userId())) {
            throw new ValidationException("User ID cannot be null or empty");
        }
        if (NullSafeUtils.safeToInt(request.numSeats()) == null ||
                NullSafeUtils.safeToInt(request.numSeats()) <= 0) {
            throw new ValidationException("Number of seats must be positive");
        }
    }

    private void validateSeatsAvailability(Trip trip, Integer numSeats) {
        Integer available = NullSafeUtils.safeToInt(trip.getAvailableSeats());
        Integer requested = NullSafeUtils.safeToInt(numSeats);
        if (available == null || requested == null || available < requested) {
            throw new ConflictException("Not enough seats available: " + NullSafeUtils.safeToString(available));
        }
    }

    private boolean isValidForWebhook(Booking booking) {
        return booking != null && BookingState.PENDING_PAYMENT.equals(booking.getState());
    }

    private void validateCancellation(Booking booking) {
        BookingState state = NullSafeUtils.safeGetBookingState(booking.getState());
        if (state == BookingState.CANCELLED || state == BookingState.EXPIRED) {
            throw new ConflictException("Cannot cancel booking in state: " + state);
        }
    }

    private LocalDateTime calculateCutoff(Trip trip) {
        if (trip == null || trip.getStartDate() == null || trip.getRefundableUntilDaysBefore() == null) {
            return LocalDateTime.now();
        }
        return trip.getStartDate().minusDays(trip.getRefundableUntilDaysBefore());
    }

    private BigDecimal calculateRefundAmount(Booking booking, Trip trip, LocalDateTime cutoffDate) {
        if (LocalDateTime.now().isBefore(cutoffDate) && BookingState.CONFIRMED.equals(booking.getState())) {
            Integer feePercent = NullSafeUtils.safeToInt(trip.getCancellationFeePercent());
            if (feePercent != null && feePercent > 0) {
                BigDecimal fee = BigDecimal.valueOf(feePercent)
                        .divide(BigDecimal.valueOf(100), 2, BigDecimal.ROUND_HALF_UP);
                return NullSafeUtils.safeGetBigDecimal(booking.getPriceAtBooking())
                        .multiply(BigDecimal.ONE.subtract(fee));
            }
        }
        return BigDecimal.ZERO;
    }

    private boolean shouldReleaseSeats(LocalDateTime cutoffDate) {
        return cutoffDate != null && LocalDateTime.now().isBefore(cutoffDate);
    }

    private void releaseSeatsForBooking(Booking booking) {
        Trip trip = tripService.getTripById(booking.getTripId());
        Integer newAvailable = NullSafeUtils.safeAdd(trip.getAvailableSeats(), booking.getNumSeats());
        trip.setAvailableSeats(Math.min(newAvailable, trip.getMaxCapacity()));
        trip.setUpdatedAt(LocalDateTime.now());
        tripService.saveTrip(trip);
    }
}
