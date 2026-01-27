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

    @PostMapping("/payments/webhook")
    public ResponseEntity<ApiResponse<WebhookRequest>> handlePaymentWebhook(@RequestBody WebhookRequest webhookRequest) {

        String bookingId = NullSafeUtils.safeToString(webhookRequest != null ? webhookRequest.bookingId() : null);
        String status = NullSafeUtils.safeToString(webhookRequest != null ? webhookRequest.status() : null);

        log.info("Incoming Payment Webhook");
        log.info("Booking ID   : {}", bookingId);
        log.info("Payment Status: {}", status);
        log.debug("Full Webhook Payload: {}", NullSafeUtils.safeToString(webhookRequest));

        try {
            webhookService.processWebhook(webhookRequest);
            log.info("Webhook processing triggered successfully for bookingId={}", bookingId);
        } catch (Exception e) {
            log.error("Unexpected error while processing webhook for bookingId={}", bookingId, e);
        }

        return ResponseEntity.ok(ApiResponse.success("Processed webhook successfully", webhookRequest));
    }

    @GetMapping("/admin/trips/{tripId}/metrics")
    public ResponseEntity<ApiResponse<TripMetricsResponse>> getTripAnalytics(@PathVariable UUID tripId) {

        String safeTripId = NullSafeUtils.safeToString(tripId);
        log.info("Admin requested trip analytics");
        log.info("Trip ID: {}", safeTripId);

        TripMetricsResponse metrics = tripService.getTripMetrics(tripId);

        log.debug("Trip Metrics Response: {}", NullSafeUtils.safeToString(metrics));
        log.info("Trip analytics returned successfully for tripId={}", safeTripId);

        return ResponseEntity.ok(ApiResponse.success("Trip metrics retrieved", metrics));
    }

    @GetMapping("/admin/trips/at-risk")
    public ResponseEntity<ApiResponse<AtRiskTripsResponse>> listAtRiskTrips() {

        log.info("Admin requested at-risk trips list");

        AtRiskTripsResponse atRiskTrips = tripService.getAtRiskTrips();

        log.debug("At-Risk Trips Response: {}", NullSafeUtils.safeToString(atRiskTrips));
        log.info("At-risk trips list returned successfully");

        return ResponseEntity.ok(ApiResponse.success("At-risk trips retrieved", atRiskTrips));
    }
}
