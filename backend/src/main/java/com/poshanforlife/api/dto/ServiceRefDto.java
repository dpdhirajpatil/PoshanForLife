package com.poshanforlife.api.dto;

/**
 * Minimal reference to the catalogue item behind an assignment. Null-able as
 * a whole: an archived catalogue item may have been deleted after assignment.
 */
public record ServiceRefDto(String id, String name, String serviceCode) {
}
