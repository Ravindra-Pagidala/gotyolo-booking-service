package com.gotyolo.booking.entity;

import com.gotyolo.booking.enums.BookingState;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "bookings")
@Data
@Builder
public class Booking {
    @Id
    @GeneratedValue
    private UUID id;
    
    @Column(nullable = false)
    private UUID tripId;
    
    @Column(nullable = false)
    private UUID userId;
    
    @Column(nullable = false)
    private Integer numSeats;

    @Column(nullable = false)
    private BookingState state;
    
    @Column(nullable = false)
    private BigDecimal priceAtBooking;

    private String paymentReference;

    private LocalDateTime createdAt;

    private LocalDateTime expiresAt;

    private BigDecimal refundAmount;

    private LocalDateTime cancelledAt;

    private String idempotencyKey;

    private LocalDateTime updatedAt;
}
