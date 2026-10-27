package me.limbo.smp.status;

import me.limbo.smp.darkness.Channel;
import me.limbo.smp.darkness.Darkness;
import me.limbo.smp.util.Msg;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Darkness fatigue: use BOTH of your channels within 8 seconds of each other and for the next 30 seconds your
 * passives only work while you are actually holding the null totem (normally having it in your inventory is enough).
 */
public final class FatigueManager {
    private static final long WINDOW_MS = 8_000L;
    private static final long FATIGUE_MS = 30_000L;

    private final Map<UUID, Map<String, Long>> lastUse = new HashMap<>();
    private final Map<UUID, Long> until = new HashMap<>();

    /** Call after every successful channel use. */
    public void record(Player p, Darkness d, Channel used) {
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();
        Map<String, Long> uses = lastUse.computeIfAbsent(id, k -> new HashMap<>());
        uses.put(used.id(), now);

        if (d.channels().size() < 2) return;
        for (Channel c : d.channels()) {
            Long t = uses.get(c.id());
            if (t == null || now - t > WINDOW_MS) return; // not every channel was used recently
        }
        uses.clear();
        until.put(id, now + FATIGUE_MS);
        Msg.send(p, "<dark_red>Darkness fatigue<gray>: your passives only work while you <white>hold<gray> the null totem for 30 seconds.");
    }

    public boolean isFatigued(UUID id) {
        Long u = until.get(id);
        if (u == null) return false;
        if (u <= System.currentTimeMillis()) return false;
        return true;
    }

    /** Called every passive tick: announces when the fatigue wears off. */
    public void tick(Player p) {
        Long u = until.get(p.getUniqueId());
        if (u != null && u <= System.currentTimeMillis()) {
            until.remove(p.getUniqueId());
            p.sendActionBar(Msg.parse("<gray>Darkness fatigue has worn off."));
        }
    }

    public void clear(UUID id) {
        until.remove(id);
        lastUse.remove(id);
    }
}
