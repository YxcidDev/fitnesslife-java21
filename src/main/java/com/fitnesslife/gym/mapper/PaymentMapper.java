package com.fitnesslife.gym.mapper;

import com.fitnesslife.gym.dto.PaymentResponseDTO;
import com.fitnesslife.gym.model.Payment;
import com.fitnesslife.gym.model.Plan;
import com.fitnesslife.gym.model.User;
import org.mapstruct.Mapper;
import java.util.List;

@Mapper(componentModel = "spring")
public interface PaymentMapper {

    PaymentResponseDTO toResponseDTO(Payment payment);

    List<PaymentResponseDTO> toResponseDTOList(List<Payment> payments);

    PaymentResponseDTO.UserSummary toUserSummary(User user);

    PaymentResponseDTO.PlanSummary toPlanSummary(Plan plan);
}