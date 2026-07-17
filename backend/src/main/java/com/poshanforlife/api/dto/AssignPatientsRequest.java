package com.poshanforlife.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/** Replaces ALL patient assignments of a doctor. An empty list clears them. */
public record AssignPatientsRequest(@NotNull List<UUID> patientIds) {
}
