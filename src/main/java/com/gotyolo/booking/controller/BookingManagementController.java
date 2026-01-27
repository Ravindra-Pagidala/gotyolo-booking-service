package com.gotyolo.booking.controller;

import com.gotyolo.booking.dto.ApiResponse;
import com.gotyolo.booking.dto.BookingResponse;
import com.gotyolo.booking.dto.CreateBookingRequest;
import com.gotyolo.booking.service.BookingService;
import jakarta.validation.Valid;
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
public class BookingManagementController {

    private final BookingService bookingService;

    /**
     * Create booking for trip (reserves seats, starts payment flow)
     * POST /api/v1/trips/{tripId}/bookings
     */
    @PostMapping("/trips/{tripId}/book")
    public ResponseEntity<ApiResponse<BookingResponse>> createTripBooking(
            @PathVariable UUID tripId,
            @Valid @RequestBody CreateBookingRequest request) {
        log.info("Creating booking for trip {} with {} seats", tripId, request.numSeats());
        BookingResponse booking = bookingService.createBooking(tripId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Booking created successfully", booking));
    }

    /**
     * Cancel existing booking (handles refund policy)
     * POST /api/v1/bookings/{bookingId}/cancel
     */
    @PostMapping("/bookings/{bookingId}/cancel")
    public ResponseEntity<ApiResponse<BookingResponse>> cancelUserBooking(@PathVariable UUID bookingId) {
        log.info("Cancelling booking: {}", bookingId);
        BookingResponse cancelledBooking = bookingService.cancelBooking(bookingId);
        return ResponseEntity.ok(
                ApiResponse.success("Booking cancelled successfully", cancelledBooking)
        );
    }

    /**
     * Get booking status and details
     * GET /api/v1/bookings/{bookingId}
     */
    @GetMapping("/bookings/{bookingId}")
    public ResponseEntity<ApiResponse<BookingResponse>> getBookingStatus(@PathVariable UUID bookingId) {
        log.debug("Fetching booking status: {}", bookingId);
        BookingResponse booking = bookingService.getBooking(bookingId);
        return ResponseEntity.ok(ApiResponse.success(booking));
    }
}
