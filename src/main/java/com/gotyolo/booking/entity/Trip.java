package com.gotyolo.booking.entity;

import com.gotyolo.booking.enums.TripStatus;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "trips")
@Data
@Builder
public class Trip {
    @Id
    @GeneratedValue
    private UUID id;
    
    @Column(nullable = false)
    private String title;
    
    private String destination;

    @Column(nullable = false)
    private LocalDateTime startDate;

    private LocalDateTime endDate;

    @Column(nullable = false)
    private BigDecimal price;
    
    @Column(nullable = false)
    private Integer maxCapacity;
    
    @Column(nullable = false)
    private Integer availableSeats;

    @Column(nullable = false)
    private TripStatus status;
    
    @Column(nullable = false)
    private Integer refundableUntilDaysBefore;
    
    private Integer cancellationFeePercent;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
