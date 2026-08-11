package com.farm2home.customer.domain.repository;

import com.farm2home.customer.domain.entity.CustomerAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, UUID> {

    List<CustomerAddress> findAllByCustomerIdAndDeletedFalse(UUID customerId);

    /** Scopes the address lookup to the given customer, not just the address id, so an address
     *  belonging to a different customer can never be read/edited/deleted via a guessed id -
     *  same IDOR-prevention shape as CustomerServiceImpl.getCustomerScoped. */
    Optional<CustomerAddress> findByIdAndCustomerIdAndDeletedFalse(UUID id, UUID customerId);

    boolean existsByCustomerIdAndDeletedFalse(UUID customerId);
}
