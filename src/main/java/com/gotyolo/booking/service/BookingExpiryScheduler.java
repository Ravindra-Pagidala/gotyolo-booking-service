package com.gotyolo.booking.service;

import com.gotyolo.booking.enums.BookingState;
import com.gotyolo.booking.interfaces.ExpiredBookingInfo;
import com.gotyolo.booking.repository.BookingRepository;
import com.gotyolo.booking.repository.TripRepository;
import com.gotyolo.booking.utils.NullSafeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingExpiryScheduler {

    private final BookingRepository bookingRepository;
    private final TripRepository tripRepository;

    /**
     * CRITICAL: Auto-expire PENDING_PAYMENT + RELEASE SEATS ATOMICALLY
     * 1. Find expired bookings (state=PENDING_PAYMENT, expiresAt < now)
     * 2. For EACH: Update booking.state=EXPIRED + trip.availableSeats += numSeats
     * 3. SINGLE TRANSACTION - Either ALL succeed or NONE
     * Runs every 1 minute
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void expirePendingBookings() {

        log.info("Booking Expiry Scheduler Triggered");
        log.debug("Step 1: Fetching expired bookings with state=PENDING_PAYMENT");

        List<ExpiredBookingInfo> expiredBookings =
                bookingRepository.findExpiredBookings(BookingState.PENDING_PAYMENT);

        if (expiredBookings == null || expiredBookings.isEmpty()) {
            log.info("No expired bookings found at this run");
            return;
        }

        log.info("Found {} expired bookings to process", expiredBookings.size());

        int processedCount = 0;

        for (ExpiredBookingInfo info : expiredBookings) {

            String bookingId = NullSafeUtils.safeToString(info != null ? info.getBookingId() : null);
            String tripId = NullSafeUtils.safeToString(info != null ? info.getTripId() : null);
            int seats = info != null ? info.getNumSeats() : 0;

            log.info("Processing expired booking");
            log.info("Booking ID : {}", bookingId);
            log.info("Trip ID    : {}", tripId);
            log.info("Seats Held : {}", seats);

            try {
                processSingleExpiredBooking(info);
                processedCount++;

                log.info("Booking expired successfully");
                log.info("Booking ID      : {}", bookingId);
                log.info("Seats Released  : {}", seats);
                log.info("Trip ID Updated : {}", tripId);

            } catch (Exception e) {
                log.error("FAILED to process expired booking");
                log.error("Booking ID: {}", bookingId);
                log.error("Trip ID   : {}", tripId);
                log.error("Seats     : {}", seats);
                log.error("Reason    : {}", NullSafeUtils.safeToString(e.getMessage()));
                log.debug("Full Stacktrace:", e);

                throw e; // Preserve atomic rollback
            }
        }

        log.info("Booking Expiry Scheduler Completed");
        log.info("Total Processed : {}", processedCount);
        log.info("Total Found     : {}", expiredBookings.size());
    }

    private void processSingleExpiredBooking(ExpiredBookingInfo info) {

        String bookingId = NullSafeUtils.safeToString(info != null ? info.getBookingId() : null);
        String tripId = NullSafeUtils.safeToString(info != null ? info.getTripId() : null);
        int seats = info != null ? info.getNumSeats() : 0;

        log.debug("Updating booking state to EXPIRED");
        log.debug("Booking ID: {}", bookingId);

        int updated = bookingRepository.updateBookingState(
                info.getBookingId(),
                BookingState.PENDING_PAYMENT,
                BookingState.EXPIRED
        );

        log.debug("Rows Updated in booking table: {}", updated);

        if (updated == 0) {
            log.error("Booking state update FAILED for bookingId={}", bookingId);
            throw new IllegalStateException("Booking state update failed for bookingId=" + bookingId);
        }

        log.debug("Releasing seats back to trip");
        log.debug("Trip ID       : {}", tripId);
        log.debug("Seats to Add  : {}", seats);

        int released = tripRepository.releaseSeatsForExpiredBooking(
                info.getTripId(),
                info.getNumSeats()
        );

        log.debug("Rows Updated in trip table: {}", released);

        if (released == 0) {
            log.error("Seat release FAILED for tripId={}", tripId);
            throw new IllegalStateException("Seat release failed for tripId=" + tripId);
        }

        log.info("Expiry Finalized");
        log.info("Booking ID     : {}", bookingId);
        log.info("Trip ID        : {}", tripId);
        log.info("Seats Released : {}", seats);
    }
}
