package com.sentragrid.dashboard.dto;

import java.time.LocalDate;

public record DailyReservationDto(LocalDate date, Long count) {}
