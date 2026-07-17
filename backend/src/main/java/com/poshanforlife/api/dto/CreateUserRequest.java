package com.poshanforlife.api.dto;

import com.poshanforlife.api.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Size(min = 2, max = 255) String name,
        @NotBlank @Email String email,
        @NotBlank
        @Pattern(regexp = "^(?=.*\\d).{8,}$", message = "must be at least 8 characters and contain a digit")
        String password,
        @NotNull Role role,
        @Size(max = 32) String phone) {
}
