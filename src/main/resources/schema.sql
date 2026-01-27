CREATE TABLE IF NOT EXISTS trips (
    id UUID PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    destination VARCHAR(255),
    start_date TIMESTAMP NOT NULL,
    end_date TIMESTAMP,
    price NUMERIC(10,2) NOT NULL,
    max_capacity INTEGER NOT NULL,
    available_seats INTEGER NOT NULL,
    status VARCHAR(50) NOT NULL,
    refundable_until_days_before INTEGER NOT NULL,
    cancellation_fee_percent INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS bookings (
    id UUID PRIMARY KEY,
    trip_id UUID NOT NULL,
    user_id UUID NOT NULL,
    num_seats INTEGER NOT NULL,
    state VARCHAR(50) NOT NULL,
    price_at_booking NUMERIC(10,2) NOT NULL,
    payment_reference VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP,
    refund_amount NUMERIC(10,2),
    cancelled_at TIMESTAMP,
    idempotency_key VARCHAR(255),
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_bookings_trip_id ON bookings(trip_id);
CREATE INDEX IF NOT EXISTS idx_bookings_state ON bookings(state);