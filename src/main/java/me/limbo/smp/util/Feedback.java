package me.limbo.smp.util;

import me.limbo.smp.LimboPlugin;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Ability feedback ("activated X", "you have been affected by X from Y").
 *
 * feedback.mode: top (default) shows it as a line of text at the TOP of the screen for 3 seconds, then fades it out in
 * steps and removes it; chat sends it to chat instead; off sends nothing.
 * "top" is a yellow boss bar whose bar graphics the resource pack makes fully transparent
 * (assets/minecraft/textures/gui/sprites/boss_bar/yellow_*.png), so only the text shows. Nothing else uses yellow bars.
 * Without the pack the text still shows, with an ordinary yellow bar behind it.
 */
public final class Feedback {
    private static final long DEDUPE_MS = 6_000L; // ongoing effects only announce themselves once
    private static final int MAX_LINES = 3;
    private static final Map<String, Long> last = new HashMap<>();
    private static final Map<UUID, Deque<BossBar>> lines = new HashMap<>();

    private Feedback() {}

    /** To the caster: "activated <channel>". */
    public static void activated(Player p, String channel) {
        send(p, c -> "<" + c[0] + ">activated <" + c[1] + ">" + Msg.esc(channel));
    }

    /** To whoever is hit by a channel (good or bad): "<channel> from <player>". */
    public static void affected(Entity target, String channel, Player source) {
        if (!(target instanceof Player t) || t.getUniqueId().equals(source.getUniqueId())) return;
        me.limbo.smp.darkness.chimera.GlobalAdaptationFlux.onAffected(target, channel, source);
        String key = t.getUniqueId() + ":" + channel + ":" + source.getUniqueId();
        long now = System.currentTimeMillis();
        Long prev = last.get(key);
        if (prev != null && now - prev < DEDUPE_MS) return;
        if (last.size() > 500) last.values().removeIf(v -> now - v > DEDUPE_MS);
        last.put(key, now);
        send(t, c -> "<" + c[0] + ">affected by <" + c[1] + ">" + Msg.esc(channel) + " <" + c[0] + ">from <" + c[1] + ">" + Msg.esc(source.getName()));
    }

    /** colours per fade stage: {label colour, name colour} */
    private static final String[][] STAGES = {
            {"gray", "white"}, {"#8a8a90", "#d0d0d4"}, {"#5c5c62", "#8a8a90"}, {"#3a3a40", "#5c5c62"}};

    private static void send(Player p, java.util.function.Function<String[], String> render) {
        LimboPlugin pl = LimboPlugin.get();
        String mode = pl.getConfig().getString("feedback.mode", "top").toLowerCase();
        if (mode.equals("off")) return;
        if (mode.equals("chat")) { Msg.send(p, render.apply(STAGES[0])); return; }

        Deque<BossBar> mine = lines.computeIfAbsent(p.getUniqueId(), k -> new ArrayDeque<>());
        while (mine.size() >= MAX_LINES) { BossBar old = mine.pollFirst(); p.hideBossBar(old); }
        BossBar bar = BossBar.bossBar(Msg.parse(render.apply(STAGES[0])), 1f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
        mine.addLast(bar);
        p.showBossBar(bar);
        // 3 seconds on screen: full for 2.1s, then three dimmer steps, gone at 3.0s
        int[] at = {42, 50, 56};
        for (int i = 0; i < at.length; i++) {
            final int stage = i + 1;
            Bukkit.getScheduler().runTaskLater(pl, () -> {
                if (mine.contains(bar)) bar.name(Msg.parse(render.apply(STAGES[stage])));
            }, at[i]);
        }
        Bukkit.getScheduler().runTaskLater(pl, () -> { mine.remove(bar); p.hideBossBar(bar); }, 60L);
    }
}
