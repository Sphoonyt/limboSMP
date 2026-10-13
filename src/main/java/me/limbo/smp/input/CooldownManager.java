package me.limbo.smp.input;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CooldownManager {
    private final Map<UUID, Map<String, Long>> expiries = new HashMap<>();

    public long remaining(UUID id, String key) {
        Map<String, Long> m = expiries.get(id);
        if (m == null) return 0;
        Long until = m.get(key);
        if (until == null) return 0;
        long left = until - System.currentTimeMillis();
        if (left <= 0) { m.remove(key); return 0; }
        return left;
    }

    public void set(UUID id, String key, long millis) {
        expiries.computeIfAbsent(id, k -> new HashMap<>()).put(key, System.currentTimeMillis() + millis);
    }

    public void clear(UUID id) { expiries.remove(id); }
}
