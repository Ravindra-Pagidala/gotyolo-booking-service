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

-- Helpful indexes
CREATE INDEX IF NOT EXISTS idx_trips_status ON trips(status);
CREATE INDEX IF NOT EXISTS idx_trips_start_date ON trips(start_date);


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

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_bookings_trip_id ON bookings(trip_id);
CREATE INDEX IF NOT EXISTS idx_bookings_user_id ON bookings(user_id);
CREATE INDEX IF NOT EXISTS idx_bookings_state ON bookings(state);
CREATE INDEX IF NOT EXISTS idx_bookings_expires_at ON bookings(expires_at);

-- Idempotency safety (partial unique index)
CREATE UNIQUE INDEX IF NOT EXISTS idx_bookings_idempotency_key
    ON bookings(idempotency_key)
    WHERE idempotency_key IS NOT NULL;
