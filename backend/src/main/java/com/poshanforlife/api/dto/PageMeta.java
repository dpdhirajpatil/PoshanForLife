package com.poshanforlife.api.dto;

/** Pagination metadata attached to list responses. */
public record PageMeta(long total, int page, int limit) {
}
