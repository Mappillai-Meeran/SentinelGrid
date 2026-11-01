package com.sentragrid.repository;

import com.sentragrid.entity.Medicine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MedicineRepository extends JpaRepository<Medicine, Long> {
    List<Medicine> findByNameContainingIgnoreCase(String name);
    List<Medicine> findByCategoryIgnoreCase(String category);

    @org.springframework.data.jpa.repository.Query("SELECT m FROM Medicine m WHERE LOWER(m.name) LIKE LOWER(CONCAT('%', :term, '%')) OR (m.brand IS NOT NULL AND LOWER(m.brand) LIKE LOWER(CONCAT('%', :term, '%')))")
    List<Medicine> searchByNameOrBrand(@org.springframework.data.repository.query.Param("term") String term);

    @org.springframework.data.jpa.repository.Query("SELECT m FROM Medicine m WHERE LOWER(m.name) = LOWER(:term) OR (m.brand IS NOT NULL AND LOWER(m.brand) = LOWER(:term))")
    List<Medicine> findExactByNameOrBrand(@org.springframework.data.repository.query.Param("term") String term);
}
