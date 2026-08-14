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
}
