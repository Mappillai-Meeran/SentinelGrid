package com.sentragrid.repository;

import com.sentragrid.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByPharmacyIdAndMedicineId(Long pharmacyId, Long medicineId);

    List<Inventory> findByPharmacyId(Long pharmacyId);

    @Query("SELECT COALESCE(SUM(i.quantity), 0) FROM Inventory i")
    long sumTotalQuantity();

    @Query("SELECT i FROM Inventory i " +
           "JOIN FETCH i.pharmacy p " +
           "JOIN FETCH i.medicine m " +
           "WHERE (i.quantity - i.reservedQuantity) < :threshold")
    List<Inventory> findLowStockInventory(@Param("threshold") int threshold);

    @Query("SELECT i FROM Inventory i " +
           "JOIN i.medicine m " +
           "JOIN i.pharmacy p " +
           "WHERE (CAST(:name AS string) IS NULL OR LOWER(m.name) LIKE LOWER(CONCAT('%', CAST(:name AS string), '%'))) " +
           "AND (CAST(:city AS string) IS NULL OR LOWER(p.city) = LOWER(CAST(:city AS string))) " +
           "AND (i.quantity - i.reservedQuantity) > 0")
    List<Inventory> searchAvailableInventory(
            @Param("name") String name,
            @Param("city") String city);
}
