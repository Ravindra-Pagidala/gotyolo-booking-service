package com.gotyolo.booking.repository;

import com.gotyolo.booking.entity.Trip;
import com.gotyolo.booking.enums.TripStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripRepository extends JpaRepository<Trip, UUID> {

    // GET /trips (published only)
    List<Trip> findAllByStatus(TripStatus status);

    // Concurrency safety (SELECT FOR UPDATE)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Trip t WHERE t.id = :id")
    Optional<Trip> findByIdForUpdate(@Param("id") UUID id);

    // Admin metrics (at-risk trips)
    List<Trip> findAllByStartDateBeforeAndStatus(LocalDateTime date, TripStatus status);
}
