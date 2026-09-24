package com.mrms.shared.security;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Clock whose instants are truncated to microseconds, the precision that
 * PostgreSQL and H2 store. (Clock.tick cannot be used here: its millis()
 * divides by zero for ticks shorter than a millisecond.)
 */
final class MicrosecondClock extends Clock {

    private final Clock delegate;

    MicrosecondClock(Clock delegate) {
        this.delegate = delegate;
    }

    @Override
    public ZoneId getZone() {
        return delegate.getZone();
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MicrosecondClock(delegate.withZone(zone));
    }

    @Override
    public Instant instant() {
        return delegate.instant().truncatedTo(ChronoUnit.MICROS);
    }

    @Override
    public long millis() {
        return delegate.millis();
    }
}
