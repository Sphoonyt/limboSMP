package me.limbo.smp.util;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

/** Simple chat feedback for channels. */
public final class Feedback {
    private static final long DEDUPE_MS = 6_000L; // ongoing effects only announce themselves once
    private static final Map<String, Long> last = new HashMap<>();

    private Feedback() {}

    /** To the caster: "activated <channel>". */
    public static void activated(Player p, String channel) {
        Msg.send(p, "<gray>activated <white>" + Msg.esc(channel));
    }

    /** To whoever is hit by a channel (good or bad): "you have been affected by <channel> from <player>". */
    public static void affected(Entity target, String channel, Player source) {
        if (!(target instanceof Player t) || t.getUniqueId().equals(source.getUniqueId())) return;
        String key = t.getUniqueId() + ":" + channel + ":" + source.getUniqueId();
        long now = System.currentTimeMillis();
        Long prev = last.get(key);
        if (prev != null && now - prev < DEDUPE_MS) return;
        if (last.size() > 500) last.values().removeIf(v -> now - v > DEDUPE_MS);
        last.put(key, now);
        Msg.send(t, "<gray>you have been affected by <white>" + Msg.esc(channel)
                + "<gray> from <white>" + Msg.esc(source.getName()));
    }
}
