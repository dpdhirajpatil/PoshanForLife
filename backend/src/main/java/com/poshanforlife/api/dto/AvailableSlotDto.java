package com.poshanforlife.api.dto;

import java.time.LocalTime;

/** One bookable slot in a practitioner's working day, wire time as "HH:mm". */
public record AvailableSlotDto(LocalTime time, boolean available) {
}
