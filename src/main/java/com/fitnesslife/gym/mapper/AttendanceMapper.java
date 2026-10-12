package com.fitnesslife.gym.mapper;

import com.fitnesslife.gym.dto.AttendanceResponseDTO;
import com.fitnesslife.gym.model.Attendance;
import org.mapstruct.Mapper;
import java.util.List;

@Mapper(componentModel = "spring")
public interface AttendanceMapper {

    AttendanceResponseDTO toResponseDTO(Attendance attendance);

    List<AttendanceResponseDTO> toResponseDTOList(List<Attendance> attendances);
}