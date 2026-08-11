package com.farm2home.inventory.domain.repository;

import com.farm2home.inventory.domain.entity.ProductCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductCategoryRepository extends JpaRepository<ProductCategory, UUID> {

    Optional<ProductCategory> findByIdAndDeletedFalse(UUID id);

    /** Used to validate a Product's categoryId at create/update time - the category must exist,
     *  not be deleted, AND be active (an inactive category shouldn't accept new products). */
    Optional<ProductCategory> findByIdAndActiveTrueAndDeletedFalse(UUID id);

    Page<ProductCategory> findAllByDeletedFalse(Pageable pageable);

    Page<ProductCategory> findAllByActiveTrueAndDeletedFalse(Pageable pageable);

    boolean existsByNameIgnoreCaseAndDeletedFalse(String name);
}
