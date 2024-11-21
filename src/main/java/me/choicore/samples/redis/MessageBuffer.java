package me.choicore.samples.redis;

import java.util.List;

public record MessageBuffer<T>(
        String key,
        List<T> values
) {
    public void add(T value) {
        values.add(value);
    }
}
