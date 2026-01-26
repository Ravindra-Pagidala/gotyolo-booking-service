package com.gotyolo.booking.service;

import com.gotyolo.booking.repository.BookingRepository;
import com.gotyolo.booking.utils.NullSafeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingExpiryScheduler {
    
    private final BookingRepository bookingRepository;
    
    /**
     * CRITICAL: Auto-expire PENDING_PAYMENT bookings after 15 minutes
     * Runs every 1 minute to check expiresAt < now()
     */
    @Scheduled(fixedRate = 60000) // Every 1 minute
    public void expirePendingBookings() {
        log.debug("Running booking expiry scheduler...");
        
        int expiredCount = bookingRepository.expirePendingBookings();
        
        if (expiredCount > 0) {
            log.info("Expired {} pending bookings automatically", expiredCount);
        }
        
        log.debug("Booking expiry scheduler completed. Processed: {}", expiredCount);
    }
}
