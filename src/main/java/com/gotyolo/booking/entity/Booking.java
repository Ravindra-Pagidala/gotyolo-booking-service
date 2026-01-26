package com.gotyolo.booking.entity;

import com.gotyolo.booking.enums.BookingState;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "bookings")
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

    @Enumerated(EnumType.STRING)
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
