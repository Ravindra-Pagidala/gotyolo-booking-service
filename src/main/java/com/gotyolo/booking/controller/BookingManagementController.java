package com.gotyolo.booking.controller;

import com.gotyolo.booking.dto.ApiResponse;
import com.gotyolo.booking.dto.BookingResponse;
import com.gotyolo.booking.dto.CreateBookingRequest;
import com.gotyolo.booking.service.BookingService;
import com.gotyolo.booking.utils.NullSafeUtils;
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

    @PostMapping("/trips/{tripId}/book")
    public ResponseEntity<ApiResponse<BookingResponse>> createTripBooking(
            @PathVariable UUID tripId,
            @Valid @RequestBody CreateBookingRequest request) {

        log.info("Create booking request received | tripId={} | userId={} | seats={}",
                NullSafeUtils.safeToString(tripId),
                NullSafeUtils.safeToString(request.userId()),
                NullSafeUtils.safeToString(request.numSeats()));

        BookingResponse booking = bookingService.createBooking(tripId, request);

        log.info("Booking created successfully | bookingId={} | tripId={} | userId={}",
                NullSafeUtils.safeToString(booking.id()),
                NullSafeUtils.safeToString(tripId),
                NullSafeUtils.safeToString(request.userId()));

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Booking created successfully", booking));
    }

    @PostMapping("/bookings/{bookingId}/cancel")
    public ResponseEntity<ApiResponse<BookingResponse>> cancelUserBooking(@PathVariable UUID bookingId) {

        log.info("Cancel booking request received | bookingId={}", NullSafeUtils.safeToString(bookingId));

        BookingResponse cancelledBooking = bookingService.cancelBooking(bookingId);

        log.info("Booking cancelled successfully | bookingId={} | refundAmount={}",
                NullSafeUtils.safeToString(cancelledBooking.id()),
                NullSafeUtils.safeToString(cancelledBooking.refundAmount()));

        return ResponseEntity.ok(
                ApiResponse.success("Booking cancelled successfully", cancelledBooking)
        );
    }

    @GetMapping("/bookings/{bookingId}")
    public ResponseEntity<ApiResponse<BookingResponse>> getBookingStatus(@PathVariable UUID bookingId) {

        log.info("Get booking request received | bookingId={}", NullSafeUtils.safeToString(bookingId));

        BookingResponse booking = bookingService.getBooking(bookingId);

        log.info("Booking fetched | bookingId={} | state={}", NullSafeUtils.safeToString(booking.id()),
                NullSafeUtils.safeToString(booking.state()));

        return ResponseEntity.ok(ApiResponse.success(booking));
    }
}
