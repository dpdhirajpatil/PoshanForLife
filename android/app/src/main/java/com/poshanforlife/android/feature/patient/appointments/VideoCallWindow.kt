package com.poshanforlife.android.feature.patient.appointments

import com.poshanforlife.android.core.network.AppointmentDto
import java.time.Duration
import java.time.Instant

/**
 * AN-13's join window: a video appointment's "Join call" action is live from 5
 * minutes before its scheduled time until 30 minutes after. Kept in one place so
 * the patient's appointment card and the practitioner's schedule agree exactly —
 * a call one side can join and the other can't is the obvious failure mode here.
 */
private val JOIN_OPENS_BEFORE: Duration = Duration.ofMinutes(5)
private val JOIN_CLOSES_AFTER: Duration = Duration.ofMinutes(30)

fun AppointmentDto.canJoinVideoCall(now: Instant = Instant.now()): Boolean {
    if (!isVideo || status != "scheduled") return false
    val startsAt = runCatching { Instant.parse(scheduledAt) }.getOrNull() ?: return false
    return now.isAfter(startsAt.minus(JOIN_OPENS_BEFORE)) && now.isBefore(startsAt.plus(JOIN_CLOSES_AFTER))
}
