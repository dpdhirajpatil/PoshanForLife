package com.poshanforlife.api.dto;

import com.poshanforlife.api.entity.DocumentStatus;
import jakarta.validation.constraints.NotNull;

/** No side effects beyond the status change itself — see DocumentService.updateStatus. */
public record UpdateDocumentStatusRequest(@NotNull DocumentStatus status) {
}
