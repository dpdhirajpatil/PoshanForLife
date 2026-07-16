package com.poshanforlife.api.dto;

import com.poshanforlife.api.entity.Role;

public record UserDto(String id, String name, String email, Role role) {
}
