package com.fitnesslife.gym.mapper;

import com.fitnesslife.gym.dto.PlanResponseDTO;
import com.fitnesslife.gym.model.Plan;
import org.mapstruct.Mapper;
import java.util.List;

@Mapper(componentModel = "spring")
public interface PlanMapper {

    PlanResponseDTO toResponseDTO(Plan plan);

    List<PlanResponseDTO> toResponseDTOList(List<Plan> plans);
}