package com.incidentreporter.field.testing

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** A clock the test moves by hand, so "the user edited a second later" is exact. */
class MutableClock(private var now: Instant = Instant.parse("2026-10-22T09:00:00Z")) : Clock() {

    fun advance(millis: Long) {
        now = now.plusMillis(millis)
    }

    override fun instant(): Instant = now

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this
}
