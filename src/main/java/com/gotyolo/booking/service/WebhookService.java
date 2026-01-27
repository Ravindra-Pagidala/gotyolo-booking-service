package com.gotyolo.booking.service;

import com.gotyolo.booking.dto.WebhookRequest;
import com.gotyolo.booking.service.BookingService;
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

        log.info("Processing payment webhook");
        log.info("Booking ID: {}", bookingId);
        log.info("Status    : {}", status);

        if (request == null) {
            log.warn("Webhook request is NULL — skipping processing");
            return;
        }

        try {
            log.debug("Calling bookingService.processPaymentWebhook() for bookingId={}", bookingId);

            bookingService.processPaymentWebhook(request);

            log.info("Webhook processed successfully for bookingId={}", bookingId);
        } catch (Exception e) {
            log.error("Webhook processing FAILED for bookingId={}", bookingId);
            log.error("Error Message: {}", NullSafeUtils.safeToString(e.getMessage()));
            log.debug("Full Stacktrace:", e);
            // Payment provider must always receive 200 (idempotent contract)
        }
    }
}
