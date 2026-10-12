package com.fitnesslife.gym.mapper;

import com.fitnesslife.gym.dto.FunctionalTrainingResponseDTO;
import com.fitnesslife.gym.model.FunctionalTraining;
import org.mapstruct.Mapper;
import java.util.List;

@Mapper(componentModel = "spring")
public interface FunctionalTrainingMapper {

    FunctionalTrainingResponseDTO toResponseDTO(FunctionalTraining training);

    List<FunctionalTrainingResponseDTO> toResponseDTOList(List<FunctionalTraining> trainings);
}