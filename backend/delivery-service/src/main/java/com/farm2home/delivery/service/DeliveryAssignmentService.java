package com.farm2home.delivery.service;

import com.farm2home.delivery.dto.request.ManualAssignRequest;
import com.farm2home.delivery.dto.request.UpdateAssignmentStatusRequest;
import com.farm2home.delivery.dto.response.AssignmentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface DeliveryAssignmentService {

    AssignmentResponse manualAssign(ManualAssignRequest request);

    AssignmentResponse updateStatus(UUID id, UpdateAssignmentStatusRequest request,
                                    UUID callerId, boolean isAdmin);

    AssignmentResponse findById(UUID id, UUID callerId, boolean isAdmin);

    Page<AssignmentResponse> findAll(UUID partnerId, boolean isAdmin, Pageable pageable);

    List<AssignmentResponse> findByOrderId(UUID orderId);
}
