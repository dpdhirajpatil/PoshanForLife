package com.poshanforlife.api.mapper;

import com.poshanforlife.api.dto.UserDetailDto;
import com.poshanforlife.api.dto.UserDto;
import com.poshanforlife.api.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface UserMapper {

    UserDto toDto(User user);

    // Lombok exposes the boolean field `isActive` as bean property `active`
    @Mapping(target = "isActive", source = "active")
    UserDetailDto toDetailDto(User user);
}
