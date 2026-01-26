package com.gotyolo.booking.service;

import com.gotyolo.booking.dto.WebhookRequest;
import com.gotyolo.booking.utils.NullSafeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {
    
    private final BookingService bookingService;
    
    public void processWebhook(WebhookRequest request) {
        String bookingId = NullSafeUtils.safeToString(request.bookingId());
        String status = NullSafeUtils.safeToString(request.status());
        
        log.info("Received webhook - Booking: {}, Status: {}", bookingId, status);
        
        try {
            bookingService.processPaymentWebhook(request);
            log.info("Webhook processed successfully");
        } catch (Exception e) {
            log.error("Webhook processing failed: {}", e.getMessage(), e);
            // Always succeed for payment provider (idempotent)
        }
    }
}
