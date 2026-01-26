package com.gotyolo.booking.utils;

import com.gotyolo.booking.enums.BookingState;
import com.gotyolo.booking.enums.TripStatus;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@UtilityClass
public class NullSafeUtils {
    
    public static String safeToString(Object value) {
        return value != null ? value.toString() : null;
    }
    
    public static Integer safeToInt(Integer value) {
        return value != null ? value : null;
    }
    
    public static UUID safeGetUUID(UUID id) {
        return id;
    }
    
    public static UUID safeParseUUID(String uuidStr) {
        try {
            return uuidStr != null ? UUID.fromString(uuidStr) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    
    public static BigDecimal safeGetBigDecimal(BigDecimal value) {
        return value;
    }
    
    public static LocalDateTime safeGetLocalDateTime(LocalDateTime value) {
        return value;
    }
    
    public static BookingState safeGetBookingState(BookingState state) {
        return state;
    }
    
    public static TripStatus safeGetTripStatus(TripStatus status) {
        return status;
    }
    
    public static boolean isNullOrEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
    
    public static BigDecimal safeMultiply(BigDecimal price, Integer numSeats) {
        if (price == null || numSeats == null) return BigDecimal.ZERO;
        return price.multiply(BigDecimal.valueOf(numSeats));
    }
    
    public static Integer safeSubtract(Integer available, Integer numSeats) {
        if (available == null || numSeats == null) return 0;
        return Math.max(0, available - numSeats);
    }
    
    public static Integer safeAdd(Integer a, Integer b) {
        return (a != null ? a : 0) + (b != null ? b : 0);
    }
}
