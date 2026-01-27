package com.gotyolo.booking.service;

import com.gotyolo.booking.interfaces.ExpiredBookingInfo;
import com.gotyolo.booking.enums.BookingState;
import com.gotyolo.booking.interfaces.ExpiredBookingInfo;
import com.gotyolo.booking.repository.BookingRepository;
import com.gotyolo.booking.repository.TripRepository;
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

        log.debug("Running booking expiry scheduler...");

        // Step 1: Get ALL expired bookings in ONE query
        List<ExpiredBookingInfo> expiredBookings =
                bookingRepository.findExpiredBookings(BookingState.PENDING_PAYMENT);

        if (expiredBookings.isEmpty()) {
            log.debug("No expired bookings found");
            return;
        }

        int processedCount = 0;

        for (ExpiredBookingInfo info : expiredBookings) {
            try {
                processSingleExpiredBooking(info);
                processedCount++;
            } catch (Exception e) {
                log.error("Failed to process expired booking {}: {}",
                        info.getBookingId(), e.getMessage(), e);

                // IMPORTANT: force rollback for true atomicity
                throw e;
            }
        }

        log.info("Expired {} pending bookings + released seats successfully", processedCount);
        log.debug("Booking expiry scheduler completed. Processed: {}", processedCount);
    }

    private void processSingleExpiredBooking(ExpiredBookingInfo info) {

        // 1. Update booking state to EXPIRED
        int updated = bookingRepository.updateBookingState(
                info.getBookingId(),
                BookingState.PENDING_PAYMENT,
                BookingState.EXPIRED
        );

        if (updated == 0) {
            throw new IllegalStateException(
                    "Booking state update failed for bookingId=" + info.getBookingId());
        }

        // 2. Release seats back to trip
        int released = tripRepository.releaseSeatsForExpiredBooking(
                info.getTripId(),
                info.getNumSeats()
        );

        if (released == 0) {
            throw new IllegalStateException(
                    "Seat release failed for tripId=" + info.getTripId());
        }

        log.debug("Expired booking {}: released {} seats for trip {}",
                info.getBookingId(), info.getNumSeats(), info.getTripId());
    }
}
