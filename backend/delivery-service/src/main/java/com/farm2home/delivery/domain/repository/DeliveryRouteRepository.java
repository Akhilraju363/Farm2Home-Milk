package com.farm2home.delivery.domain.repository;

import com.farm2home.delivery.domain.entity.DeliveryRoute;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeliveryRouteRepository extends JpaRepository<DeliveryRoute, UUID> {

    Page<DeliveryRoute> findAllByDeletedFalse(Pageable pageable);
    Optional<DeliveryRoute> findByIdAndDeletedFalse(UUID id);
    Optional<DeliveryRoute> findByRouteCodeAndDeletedFalse(String routeCode);
    List<DeliveryRoute> findAllByActiveTrueAndDeletedFalse();
    boolean existsByRouteCodeAndDeletedFalse(String routeCode);
}
