package com.gotyolo.booking.controller;

import com.gotyolo.booking.dto.ApiResponse;
import com.gotyolo.booking.dto.AtRiskTripsResponse;
import com.gotyolo.booking.dto.TripMetricsResponse;
import com.gotyolo.booking.dto.WebhookRequest;
import com.gotyolo.booking.service.TripService;
import com.gotyolo.booking.service.WebhookService;
import com.gotyolo.booking.utils.NullSafeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PaymentAndAdminController {

    private final WebhookService webhookService;
    private final TripService tripService;

    /**
     * Payment provider webhook endpoint (ALWAYS returns 200)
     * POST /api/v1/payments/webhook
     */
    @PostMapping("/payments/webhook")
    public ResponseEntity<Void> handlePaymentWebhook(@RequestBody WebhookRequest webhookRequest) {
        String bookingId = NullSafeUtils.safeToString(webhookRequest.bookingId());
        String status = NullSafeUtils.safeToString(webhookRequest.status());
        log.info("Payment webhook received - Booking: {} Status: {}", bookingId, status);

        webhookService.processWebhook(webhookRequest);
        return ResponseEntity.ok().build(); // ALWAYS 200 for payment provider ✅
    }

    /**
     * Admin: Get comprehensive trip metrics and analytics
     * GET /api/v1/admin/trips/{tripId}/metrics
     */
    @GetMapping("/admin/trips/{tripId}/metrics")
    public ResponseEntity<ApiResponse<TripMetricsResponse>> getTripAnalytics(@PathVariable UUID tripId) {
        log.debug("Admin fetching analytics for trip: {}", NullSafeUtils.safeToString(tripId));
        TripMetricsResponse metrics = tripService.getTripMetrics(tripId);
        return ResponseEntity.ok(ApiResponse.success("Trip metrics retrieved", metrics));
    }

    /**
     * Admin: List trips at risk of low occupancy
     * GET /api/v1/admin/trips/at-risk
     */
    @GetMapping("/admin/trips/at-risk")
    public ResponseEntity<ApiResponse<AtRiskTripsResponse>> listAtRiskTrips() {
        log.debug("Admin fetching at-risk trips");
        AtRiskTripsResponse atRiskTrips = tripService.getAtRiskTrips();
        return ResponseEntity.ok(ApiResponse.success("At-risk trips retrieved", atRiskTrips));
    }
}
