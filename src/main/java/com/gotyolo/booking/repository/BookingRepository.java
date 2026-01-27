package com.gotyolo.booking.repository;

import com.gotyolo.booking.entity.Booking;
import com.gotyolo.booking.enums.BookingState;
import com.gotyolo.booking.interfaces.ExpiredBookingInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    // Webhook idempotency
    boolean existsByIdempotencyKey(String idempotencyKey);

    Optional<Booking> findByIdempotencyKey(String idempotencyKey);

    // Booking metrics by trip + state
    @Query("SELECT b FROM Booking b WHERE b.tripId = :tripId AND b.state = :state")
    List<Booking> findByTripIdAndState(@Param("tripId") UUID tripId, @Param("state") BookingState state);

    Optional<Booking> findByIdAndUserId(UUID id, UUID userId);

    @Modifying
    @Query("UPDATE Booking b SET b.state = :expiredState, b.updatedAt = CURRENT_TIMESTAMP() " +
            "WHERE b.state = :pendingState AND b.expiresAt < CURRENT_TIMESTAMP()")
    int expirePendingBookings(@Param("pendingState") BookingState pendingState,
                              @Param("expiredState") BookingState expiredState);
    // Idempotency key only update
    @Modifying
    @Query("UPDATE Booking b SET b.idempotencyKey = :key WHERE b.idempotencyKey = :key")
    void saveIdempotencyKeyOnly(@Param("key") String key);

    // Total booked seats by state (SUM numSeats)
    @Query("SELECT SUM(b.numSeats) FROM Booking b WHERE b.tripId = :tripId AND b.state = :state")
    Integer countTotalSeatsByTripIdAndState(@Param("tripId") UUID tripId, @Param("state") BookingState state);

    @Query("SELECT COALESCE(SUM(b.priceAtBooking), 0) FROM Booking b " +
            "WHERE b.tripId = :tripId AND b.state = :state")
    BigDecimal calculateGrossRevenue(@Param("tripId") UUID tripId,
                                     @Param("state") BookingState state);

    // Total refunds
    @Query("SELECT COALESCE(SUM(b.refundAmount), 0) FROM Booking b WHERE b.tripId = :tripId AND b.refundAmount IS NOT NULL")
    BigDecimal calculateTotalRefunds(@Param("tripId") UUID tripId);

    @Query("""
       SELECT 
         b.id AS bookingId,
         b.tripId AS tripId,
         b.numSeats AS numSeats
       FROM Booking b
       WHERE b.state = :pendingState
         AND b.expiresAt < CURRENT_TIMESTAMP
    """)
    List<ExpiredBookingInfo> findExpiredBookings(
            @Param("pendingState") BookingState pendingState
    );

    @Modifying
    @Query("""
       UPDATE Booking b
       SET b.state = :newState
       WHERE b.id = :bookingId
         AND b.state = :oldState
    """)
    int updateBookingState(
            @Param("bookingId") UUID bookingId,
            @Param("oldState") BookingState oldState,
            @Param("newState") BookingState newState
    );

}
