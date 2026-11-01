package com.sentragrid.repository;

import com.sentragrid.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

    @Query("SELECT r FROM IdempotencyRecord r " +
           "JOIN FETCH r.reservation res " +
           "JOIN FETCH res.patient " +
           "JOIN FETCH res.pharmacy " +
           "JOIN FETCH res.medicine " +
           "WHERE r.user.id = :userId AND r.idempotencyKey = :idempotencyKey")
    Optional<IdempotencyRecord> findByUserIdAndIdempotencyKey(@Param("userId") Long userId, @Param("idempotencyKey") String idempotencyKey);
}
