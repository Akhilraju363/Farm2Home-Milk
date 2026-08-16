package com.farm2home.inventory.domain.repository;

import com.farm2home.inventory.domain.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

    Optional<Product> findByIdAndDeletedFalse(UUID id);

    Page<Product> findAllByDeletedFalse(Pageable pageable);

    Page<Product> findAllByActiveTrueAndDeletedFalse(Pageable pageable);

    /** Used to block deleting a ProductCategory that's still assigned to at least one live
     *  product - see ProductCategoryServiceImpl.delete(). */
    boolean existsByCategory_IdAndDeletedFalse(UUID categoryId);

    /** Single atomic UPDATE, not a read-then-write - the WHERE stock_quantity >= :quantity clause
     *  is what actually prevents a stock race between two concurrent checkouts (Postgres
     *  serializes concurrent UPDATEs to the same row; whichever commits first "wins" the
     *  remaining stock, and the second sees the already-decremented value and its WHERE clause
     *  simply doesn't match, returning 0). Returns the number of rows updated: 1 on success, 0 if
     *  the product doesn't exist / is deleted / doesn't have enough stock right now - see
     *  ProductServiceImpl.decrementStock, which turns 0 into a real 409, never a silent no-op. */
    @Modifying
    @Query("UPDATE Product p SET p.stockQuantity = p.stockQuantity - :quantity " +
            "WHERE p.id = :id AND p.deleted = false AND p.stockQuantity >= :quantity")
    int decrementStock(@Param("id") UUID id, @Param("quantity") int quantity);
}
