package com.farm2home.inventory.domain.repository;

import com.farm2home.inventory.domain.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

    Optional<Product> findByIdAndDeletedFalse(UUID id);

    Page<Product> findAllByDeletedFalse(Pageable pageable);

    Page<Product> findAllByActiveTrueAndDeletedFalse(Pageable pageable);
}
