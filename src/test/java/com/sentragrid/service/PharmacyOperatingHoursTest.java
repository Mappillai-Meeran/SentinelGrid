package com.sentragrid.service;

import com.sentragrid.entity.Pharmacy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

public class PharmacyOperatingHoursTest {

    @Test
    @DisplayName("TEST 1: 24x7 pharmacy -> OPEN")
    void test24SevenPharmacy() {
        Pharmacy pharmacy = Pharmacy.builder()
                .is24Seven(true)
                .timezone("Asia/Kolkata")
                .build();

        MedicineService.OperatingHoursInfo info = MedicineService.calculateOperatingHoursInfo(pharmacy, LocalTime.of(12, 0));
        assertEquals("OPEN", info.getStatus());
        assertEquals(Boolean.TRUE, info.getIsOpen());
        assertEquals("24x7", info.getOperatingHours());
    }

    @Test
    @DisplayName("TEST 2: Normal schedule, inside hours -> OPEN")
    void testNormalScheduleInsideHours() {
        Pharmacy pharmacy = Pharmacy.builder()
                .openingTime(LocalTime.of(8, 0))
                .closingTime(LocalTime.of(22, 0))
                .is24Seven(false)
                .timezone("Asia/Kolkata")
                .build();

        MedicineService.OperatingHoursInfo info = MedicineService.calculateOperatingHoursInfo(pharmacy, LocalTime.of(14, 30));
        assertEquals("OPEN", info.getStatus());
        assertEquals(Boolean.TRUE, info.getIsOpen());
        assertEquals("08:00 AM - 10:00 PM", info.getOperatingHours());
    }

    @Test
    @DisplayName("TEST 3: Normal schedule, outside hours -> CLOSED")
    void testNormalScheduleOutsideHours() {
        Pharmacy pharmacy = Pharmacy.builder()
                .openingTime(LocalTime.of(8, 0))
                .closingTime(LocalTime.of(22, 0))
                .is24Seven(false)
                .timezone("Asia/Kolkata")
                .build();

        MedicineService.OperatingHoursInfo info = MedicineService.calculateOperatingHoursInfo(pharmacy, LocalTime.of(23, 15));
        assertEquals("CLOSED", info.getStatus());
        assertEquals(Boolean.FALSE, info.getIsOpen());
        assertEquals("08:00 AM - 10:00 PM", info.getOperatingHours());
    }

    @Test
    @DisplayName("TEST 4: Overnight schedule, before midnight -> OPEN")
    void testOvernightScheduleBeforeMidnight() {
        Pharmacy pharmacy = Pharmacy.builder()
                .openingTime(LocalTime.of(20, 0))
                .closingTime(LocalTime.of(4, 0))
                .is24Seven(false)
                .timezone("Asia/Kolkata")
                .build();

        MedicineService.OperatingHoursInfo info = MedicineService.calculateOperatingHoursInfo(pharmacy, LocalTime.of(22, 0));
        assertEquals("OPEN", info.getStatus());
        assertEquals(Boolean.TRUE, info.getIsOpen());
        assertEquals("08:00 PM - 04:00 AM", info.getOperatingHours());
    }

    @Test
    @DisplayName("TEST 5: Overnight schedule, after midnight but before closing -> OPEN")
    void testOvernightScheduleAfterMidnightBeforeClosing() {
        Pharmacy pharmacy = Pharmacy.builder()
                .openingTime(LocalTime.of(20, 0))
                .closingTime(LocalTime.of(4, 0))
                .is24Seven(false)
                .timezone("Asia/Kolkata")
                .build();

        MedicineService.OperatingHoursInfo info = MedicineService.calculateOperatingHoursInfo(pharmacy, LocalTime.of(2, 30));
        assertEquals("OPEN", info.getStatus());
        assertEquals(Boolean.TRUE, info.getIsOpen());
        assertEquals("08:00 PM - 04:00 AM", info.getOperatingHours());
    }

    @Test
    @DisplayName("TEST 6: Overnight schedule, after closing -> CLOSED")
    void testOvernightScheduleAfterClosing() {
        Pharmacy pharmacy = Pharmacy.builder()
                .openingTime(LocalTime.of(20, 0))
                .closingTime(LocalTime.of(4, 0))
                .is24Seven(false)
                .timezone("Asia/Kolkata")
                .build();

        MedicineService.OperatingHoursInfo info = MedicineService.calculateOperatingHoursInfo(pharmacy, LocalTime.of(5, 0));
        assertEquals("CLOSED", info.getStatus());
        assertEquals(Boolean.FALSE, info.getIsOpen());
        assertEquals("08:00 PM - 04:00 AM", info.getOperatingHours());
    }

    @Test
    @DisplayName("TEST 7: Missing schedule -> UNKNOWN / HOURS_UNAVAILABLE")
    void testMissingSchedule() {
        Pharmacy pharmacy = Pharmacy.builder()
                .is24Seven(false)
                .timezone("Asia/Kolkata")
                .build();

        MedicineService.OperatingHoursInfo info = MedicineService.calculateOperatingHoursInfo(pharmacy, LocalTime.of(12, 0));
        assertEquals("UNKNOWN", info.getStatus());
        assertNull(info.getIsOpen());
        assertEquals("Hours unavailable", info.getOperatingHours());
    }

    @Test
    @DisplayName("TEST 8: Invalid timezone -> safe UNKNOWN behavior")
    void testInvalidTimezone() {
        Pharmacy pharmacy = Pharmacy.builder()
                .openingTime(LocalTime.of(8, 0))
                .closingTime(LocalTime.of(22, 0))
                .is24Seven(false)
                .timezone("Invalid/Timezone_Name")
                .build();

        MedicineService.OperatingHoursInfo info = MedicineService.calculateOperatingHoursInfo(pharmacy, LocalTime.of(12, 0));
        assertEquals("UNKNOWN", info.getStatus());
        assertNull(info.getIsOpen());
        assertEquals("Hours unavailable", info.getOperatingHours());
    }
}
