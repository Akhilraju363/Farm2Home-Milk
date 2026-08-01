package com.farm2home.customer.domain.repository;

import com.farm2home.customer.domain.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID>, JpaSpecificationExecutor<Customer> {

    Optional<Customer> findByIdAndDeletedFalse(UUID id);

    Page<Customer> findAllByDeletedFalse(Pageable pageable);

    boolean existsByIdAndDeletedFalse(UUID id);

    long countByDeletedFalse();

    @Query(value = "SELECT nextval('customer.customer_code_seq')", nativeQuery = true)
    Long nextCustomerCodeSeq();

    /** Customer Growth: COUNT(*) of non-deleted customers bucketed by {@code unit} (day/week/month/year,
     *  bound as a plain text argument to Postgres' own date_trunc - never string-concatenated).
     *  The GROUP BY/COUNT runs entirely in the database; the result set is at most one row per
     *  period in the requested range, never one row per customer. */
    @Query(value = """
            SELECT date_trunc(:unit, c.created_at)::date AS period, COUNT(*) AS newCustomers
            FROM customer.customers c
            WHERE c.is_deleted = false
              AND (CAST(:start AS timestamp) IS NULL OR c.created_at >= :start)
              AND (CAST(:endExclusive AS timestamp) IS NULL OR c.created_at < :endExclusive)
            GROUP BY period
            ORDER BY period
            """, nativeQuery = true)
    List<CustomerGrowthRow> findCustomerGrowth(@Param("unit") String unit,
                                                @Param("start") LocalDateTime start,
                                                @Param("endExclusive") LocalDateTime endExclusive);

    interface CustomerGrowthRow {
        LocalDate getPeriod();
        long getNewCustomers();
    }
}
