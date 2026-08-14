package com.sentragrid.repository;

import com.sentragrid.entity.Reservation;
import com.sentragrid.entity.enums.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findByPatientId(Long patientId);

    List<Reservation> findByPatientIdAndStatus(Long patientId, ReservationStatus status);

    List<Reservation> findByPatientUsernameAndStatusOrderByCreatedAtDesc(String username, ReservationStatus status);

    List<Reservation> findByPatientUsernameOrderByCreatedAtDesc(String username);

    List<Reservation> findByStatus(ReservationStatus status);

    @Query("SELECT r FROM Reservation r " +
           "WHERE r.status = :status AND r.expiresAt < :now")
    List<Reservation> findExpiredReservations(
            @Param("status") ReservationStatus status,
            @Param("now") LocalDateTime now);

    @Query("SELECT COUNT(r) > 0 FROM Reservation r " +
           "WHERE r.patient.id = :patientId " +
           "AND r.medicine.category = :category " +
           "AND r.status = 'PENDING'")
    boolean existsActiveReservationForPatientAndCategory(
            @Param("patientId") Long patientId,
            @Param("category") String category);

    long countByStatus(ReservationStatus status);

    @Query("SELECT new com.sentragrid.dashboard.dto.TopMedicineDto(r.medicine.name, COUNT(r)) " +
           "FROM Reservation r " +
           "GROUP BY r.medicine.name " +
           "ORDER BY COUNT(r) DESC")
    List<com.sentragrid.dashboard.dto.TopMedicineDto> findTopReservedMedicines(org.springframework.data.domain.Pageable pageable);

    @Query("SELECT new com.sentragrid.dashboard.dto.TopPharmacyDto(r.pharmacy.name, COUNT(r)) " +
           "FROM Reservation r " +
           "GROUP BY r.pharmacy.name " +
           "ORDER BY COUNT(r) DESC")
    List<com.sentragrid.dashboard.dto.TopPharmacyDto> findTopPharmaciesByReservations(org.springframework.data.domain.Pageable pageable);

    @Query("SELECT CAST(r.createdAt AS date) as date, COUNT(r) as count " +
           "FROM Reservation r " +
           "WHERE r.createdAt >= :startDate " +
           "GROUP BY CAST(r.createdAt AS date) " +
           "ORDER BY CAST(r.createdAt AS date) ASC")
    List<Object[]> findDailyReservationCounts(@Param("startDate") LocalDateTime startDate);
}
