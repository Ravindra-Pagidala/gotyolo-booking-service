package com.gotyolo.booking.interfaces;

import java.util.UUID;

public interface ExpiredBookingInfo {

    UUID getBookingId();

    UUID getTripId();

    Integer getNumSeats();
}
