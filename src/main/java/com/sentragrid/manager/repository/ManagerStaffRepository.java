package com.sentragrid.manager.repository;

import com.sentragrid.manager.entity.ManagerStaff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ManagerStaffRepository extends JpaRepository<ManagerStaff, Long> {
    List<ManagerStaff> findAllByOrderByUsernameAsc();
    List<ManagerStaff> findByPharmacyIdOrderByUsernameAsc(Long pharmacyId);
    long countByPharmacyId(Long pharmacyId);
    Optional<ManagerStaff> findByUsername(String username);
}
