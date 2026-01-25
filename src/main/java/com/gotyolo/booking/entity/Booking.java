package com.gotyolo.booking.entity;

import com.gotyolo.booking.enums.BookingState;
import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "bookings")
@Data
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
    private BigDecimal priceAtBooking;
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private BookingState state;
    
    private LocalDateTime expiresAt;
    
    private String idempotencyKey;
    
    private BigDecimal refundAmount;
}
