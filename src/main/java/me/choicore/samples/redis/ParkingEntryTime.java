package me.choicore.samples.redis;

import java.time.LocalDateTime;

public record ParkingEntryTime(
        String licensePlate,
        LocalDateTime entryTime
) {
}
