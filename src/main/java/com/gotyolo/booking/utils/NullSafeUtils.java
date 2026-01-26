package com.gotyolo.booking.utils;

import com.gotyolo.booking.enums.BookingState;
import com.gotyolo.booking.enums.TripStatus;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@UtilityClass
public class NullSafeUtils {

    /**
     * Null-safe UUID extraction
     */
    public static UUID safeGetUUID(UUID id) {
        return id != null ? id : null;
    }


    /**
     * Safe string for JSON/DTO - actual null
     */
    public static String safeToString(Object value) {
        return value == null ? null : value.toString();
    }

    /**
     * Safe integer conversion from any number type
     */
    public static Integer safeToInt(Object value) {
        if (value == null) return null;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Safe UUID parsing from string
     */
    public static UUID safeParseUUID(String uuidStr) {
        if (uuidStr == null || uuidStr.trim().isEmpty()) return null;
        try {
            return UUID.fromString(uuidStr.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Null-safe BigDecimal extraction (ZERO fallback)
     */
    public static BigDecimal safeGetBigDecimal(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    /**
     * Null-safe LocalDateTime extraction (NOW fallback)
     */
    public static LocalDateTime safeGetLocalDateTime(LocalDateTime value) {
        return value != null ? value : LocalDateTime.now();
    }

    /**
     * Null-safe enum extraction with default
     */
    public static BookingState safeGetBookingState(BookingState state) {
        return state != null ? state : BookingState.EXPIRED;
    }

    public static TripStatus safeGetTripStatus(TripStatus status) {
        return status != null ? status : TripStatus.DRAFT;
    }

    /**
     * Null/empty string check
     */
    public static boolean isNullOrEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * Safe multiplication for price calculations
     */
    public static BigDecimal safeMultiply(BigDecimal price, Integer numSeats) {
        if (price == null || numSeats == null || numSeats <= 0) return BigDecimal.ZERO;
        return price.multiply(BigDecimal.valueOf(numSeats));
    }

    /**
     * Safe subtraction for seat calculations (never negative)
     */
    public static Integer safeSubtract(Integer available, Integer numSeats) {
        if (available == null) available = 0;
        if (numSeats == null) numSeats = 0;
        return Math.max(0, available - numSeats);
    }

    /**
     * Safe addition for seat restoration
     */
    public static Integer safeAdd(Integer a, Integer b) {
        return (a != null ? a : 0) + (b != null ? b : 0);
    }
}
