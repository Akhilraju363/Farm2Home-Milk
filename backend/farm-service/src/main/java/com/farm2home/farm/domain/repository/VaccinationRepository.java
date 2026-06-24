package com.farm2home.farm.domain.repository;

import com.farm2home.farm.domain.entity.Vaccination;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VaccinationRepository extends JpaRepository<Vaccination, UUID> {

    Page<Vaccination> findAllByCowIdAndDeletedFalseOrderByAdministeredAtDesc(UUID cowId, Pageable pageable);
    Optional<Vaccination> findByIdAndCowIdAndDeletedFalse(UUID id, UUID cowId);

    @Query("""
            SELECT v FROM Vaccination v
            WHERE v.deleted = false
            AND v.nextDueDate BETWEEN :from AND :to
            ORDER BY v.nextDueDate ASC
            """)
    List<Vaccination> findUpcomingVaccinations(LocalDate from, LocalDate to);
}
