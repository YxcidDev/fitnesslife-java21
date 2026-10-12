package com.fitnesslife.gym.mapper;

import com.fitnesslife.gym.dto.UserResponseDTO;
import com.fitnesslife.gym.model.User;
import org.mapstruct.Mapper;
import java.util.List;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponseDTO toResponseDTO(User user);

    List<UserResponseDTO> toResponseDTOList(List<User> users);
}