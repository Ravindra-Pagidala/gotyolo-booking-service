package com.gotyolo.booking.repository;

import com.gotyolo.booking.entity.Booking;
import com.gotyolo.booking.enums.BookingState;
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
    @Query("UPDATE Booking b SET b.state = 'EXPIRED', b.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE b.state = 'PENDING_PAYMENT' AND b.expiresAt < CURRENT_TIMESTAMP")
    int expirePendingBookings();

    // Idempotency key only update
    @Modifying
    @Query("UPDATE Booking b SET b.idempotencyKey = :key WHERE b.idempotencyKey = :key")
    void saveIdempotencyKeyOnly(@Param("key") String key);

    // Total booked seats by state (SUM numSeats)
    @Query("SELECT SUM(b.numSeats) FROM Booking b WHERE b.tripId = :tripId AND b.state = :state")
    Integer countTotalSeatsByTripIdAndState(@Param("tripId") UUID tripId, @Param("state") BookingState state);

    //  Gross revenue (SUM priceAtBooking)
    @Query("SELECT COALESCE(SUM(b.priceAtBooking), 0) FROM Booking b WHERE b.tripId = :tripId AND b.state = 'CONFIRMED'")
    BigDecimal calculateGrossRevenue(@Param("tripId") UUID tripId);

    // Total refunds
    @Query("SELECT COALESCE(SUM(b.refundAmount), 0) FROM Booking b WHERE b.tripId = :tripId AND b.refundAmount IS NOT NULL")
    BigDecimal calculateTotalRefunds(@Param("tripId") UUID tripId);

}
