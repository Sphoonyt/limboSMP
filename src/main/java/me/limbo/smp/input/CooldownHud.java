package me.limbo.smp.input;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.Channel;
import me.limbo.smp.darkness.Darkness;
import me.limbo.smp.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Shows every cooldown on the action bar while a player holds their null totem (or an active cursed item):
 * each channel (ready or counting down, charges for the Axiom Dagger) plus flux/passive cooldowns that are running.
 * Nothing is shown when everything is ready. It steps aside for a moment after an ability is used so that
 * ability's own message isn't overwritten.
 */
public final class CooldownHud {
    private final LimboPlugin plugin;
    private final Map<UUID, Integer> quietUntil = new HashMap<>();
    private final Map<UUID, Set<String>> cooling = new HashMap<>();
    private BukkitTask task;

    public CooldownHud(LimboPlugin plugin) { this.plugin = plugin; }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 4L);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        task = null;
    }

    /** Don't draw over this player's action bar for a moment (an ability message is showing). */
    public void quiet(Player p, int ticks) {
        quietUntil.put(p.getUniqueId(), Bukkit.getCurrentTick() + ticks);
    }

    private static String hex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static String time(long ms) {
        double s = Math.max(0, ms) / 1000.0;
        if (s < 10) return String.format("%.1fs", s);
        long whole = (long) Math.ceil(s);
        return whole >= 60 ? (whole / 60) + "m " + (whole % 60) + "s" : whole + "s";
    }

    private static String pretty(String key) {
        String k = key.contains(":") ? key.substring(key.indexOf(':') + 1) : key;
        StringBuilder sb = new StringBuilder();
        for (String part : k.split("_")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1).toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }

    private void tick() {
        int now = Bukkit.getCurrentTick();
        CooldownManager cds = plugin.dispatcher().cooldowns();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isDead()) continue;
            Integer quiet = quietUntil.get(p.getUniqueId());
            if (quiet != null && now < quiet) continue;
            Darkness d = plugin.data().getDarkness(p);
            if (d == null) continue;
            boolean totem = plugin.items().isHoldingOrb(p);
            boolean cursedOnly = !totem && plugin.cursed().isHoldingActive(p);
            if (!totem && !cursedOnly) continue;

            UUID id = p.getUniqueId();
            String col = hex(d.color());
            List<String> parts = new ArrayList<>();
            boolean anyCooling = false;
            Set<String> channelKeys = new HashSet<>();
            List<Channel> channels = d.channels();
            for (int i = 0; i < channels.size(); i++) {
                Channel c = channels.get(i);
                String key = d.id() + ":" + c.id();
                channelKeys.add(key);
                if (cursedOnly && i > 0) continue; // a cursed item only runs the first channel
                long left = c.usesCustomCooldown() ? c.hudRemaining(id) : cds.remaining(id, key);
                String charges = c.usesCustomCooldown() ? c.hudCharges(id) : null;
                String label = "<" + col + ">" + Msg.esc(c.name()) + " ";
                if (charges != null) label += "<white>" + charges + " ";
                Set<String> was = cooling.computeIfAbsent(id, k -> new HashSet<>());
                if (left > 0) { anyCooling = true; was.add(key); parts.add(label + "<red>" + time(left)); }
                else {
                    parts.add(label + "<green>ready");
                    if (was.remove(key)) { // it just came back: a soft two-note chime
                        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 1.5f);
                        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 1.9f);
                    }
                }
            }
            // flux / passive cooldowns that are running (hidden when ready, they aren't something you trigger)
            for (Map.Entry<String, Long> e : cds.active(id).entrySet()) {
                if (channelKeys.contains(e.getKey()) || !e.getKey().startsWith(d.id() + ":")) continue;
                anyCooling = true;
                parts.add("<" + col + ">" + Msg.esc(pretty(e.getKey())) + " <red>" + time(e.getValue()));
            }
            long silenced = plugin.status().silenceRemaining(id);
            if (silenced > 0) { anyCooling = true; parts.add(0, "<#46be5a>Silenced <red>" + time(silenced)); }
            if (!anyCooling) continue;
            p.sendActionBar(Msg.parse(String.join(" <dark_gray>| ", parts)));
        }
    }
}
