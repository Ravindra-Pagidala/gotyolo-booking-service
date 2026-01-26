package com.gotyolo.booking.controller;

import com.gotyolo.booking.dto.*;
import com.gotyolo.booking.service.TripService;
import com.gotyolo.booking.utils.NullSafeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/trips")
@RequiredArgsConstructor
public class TripManagementController {

    private final TripService tripService;

    /**
     * List all PUBLISHED trips available for booking
     * GET /api/v1/trips
     */
    @GetMapping
    public ResponseEntity<List<TripResponse>> listAvailableTrips() {
        log.debug("Listing all available trips");
        List<TripResponse> trips = tripService.getPublishedTrips();
        return ResponseEntity.ok(trips);
    }

    /**
     * Get detailed information for specific trip
     * GET /api/v1/trips/{tripId}
     */
    @GetMapping("/{tripId}")
    public ResponseEntity<TripResponse> getTripDetails(@PathVariable UUID tripId) {
        log.debug("Fetching trip details: {}", NullSafeUtils.safeToString(tripId));
        TripResponse trip = tripService.getTripDetails(tripId);
        return ResponseEntity.ok(trip);
    }

    /**
     * Create new trip (Admin operation)
     * POST /api/v1/trips
     */
    @PostMapping
    public ResponseEntity<TripResponse> createNewTrip(@Valid @RequestBody CreateTripRequest request) {
        log.info("Creating trip: {}", NullSafeUtils.safeToString(request.title()));
        TripResponse createdTrip = tripService.createTrip(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdTrip);
    }
}
