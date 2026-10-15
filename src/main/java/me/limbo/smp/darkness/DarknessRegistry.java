package me.limbo.smp.darkness;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DarknessRegistry {
    /** 6 darknesses total; 2 slots are intentionally left open for now. */
    public static final int MAX_SLOTS = 6;

    private final Map<String, Darkness> darknesses = new LinkedHashMap<>();

    public void register(Darkness d) {
        if (darknesses.size() >= MAX_SLOTS) throw new IllegalStateException("Darkness registry is full (" + MAX_SLOTS + ")");
        if (darknesses.putIfAbsent(d.id(), d) != null) throw new IllegalStateException("Duplicate darkness id: " + d.id());
    }

    public Darkness get(String id) { return id == null ? null : darknesses.get(id.toLowerCase()); }
    public List<Darkness> all() { return new ArrayList<>(darknesses.values()); }
    public List<String> ids() { return new ArrayList<>(darknesses.keySet()); }
    public int freeSlots() { return MAX_SLOTS - darknesses.size(); }
}
