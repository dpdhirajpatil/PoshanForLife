package com.poshanforlife.api.mapper;

import com.poshanforlife.api.dto.UserDto;
import com.poshanforlife.api.entity.User;
import org.mapstruct.Mapper;

@Mapper
public interface UserMapper {

    UserDto toDto(User user);
}
