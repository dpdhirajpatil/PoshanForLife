package com.poshanforlife.api.dto;

/** One histogram bar: label is one of "Underweight"/"Normal"/"Overweight"/"Obese". */
public record BmiBucketDto(String label, long count) {
}
