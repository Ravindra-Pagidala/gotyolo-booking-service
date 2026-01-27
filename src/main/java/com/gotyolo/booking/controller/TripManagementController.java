package com.gotyolo.booking.controller;

import com.gotyolo.booking.dto.ApiResponse;
import com.gotyolo.booking.dto.CreateTripRequest;
import com.gotyolo.booking.dto.TripResponse;
import com.gotyolo.booking.service.TripService;
import com.gotyolo.booking.utils.NullSafeUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<ApiResponse<List<TripResponse>>> listAvailableTrips() {
        log.info("Received request to list all available trips");

        List<TripResponse> trips = tripService.getPublishedTrips();

        log.info("Retrieved {} published trips", trips != null ? trips.size() : 0);
        return ResponseEntity.ok(ApiResponse.success("Trips retrieved successfully", trips));
    }

    /**
     * Get detailed information for specific trip
     * GET /api/v1/trips/{tripId}
     */
    @GetMapping("/{tripId}")
    public ResponseEntity<ApiResponse<TripResponse>> getTripDetails(@PathVariable UUID tripId) {
        log.info("Received request to fetch trip details for tripId={}",
                NullSafeUtils.safeToString(tripId));

        TripResponse trip = tripService.getTripDetails(tripId);

        log.info("Successfully fetched trip details for tripId={}",
                NullSafeUtils.safeToString(tripId));

        return ResponseEntity.ok(ApiResponse.success("Trip details retrieved", trip));
    }

    /**
     * Create new trip (Admin operation)
     * POST /api/v1/trips
     */
    @PostMapping
    public ResponseEntity<ApiResponse<TripResponse>> createNewTrip(
            @Valid @RequestBody CreateTripRequest request) {

        log.info("Received request to create trip with title={}",
                NullSafeUtils.safeToString(request.title()));

        TripResponse createdTrip = tripService.createTrip(request);

        log.info("Trip creation completed successfully. tripId={}",
                NullSafeUtils.safeToString(createdTrip.id()));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Trip created successfully", createdTrip));
    }
}
