package me.limbo.smp.darkness.chimera;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.ChimeraDarkness;
import me.limbo.smp.darkness.FluxPassive;
import me.limbo.smp.util.ParticleUtil;
import me.limbo.smp.util.TrueDamage;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.entity.Projectile;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Flux (totem held). Two parts:
 *  - Damage cap: you can't take more than 5 hearts of damage inside one second. Only repeat hits are limited,
 *    the first hit of a window can still deal any amount.
 *  - Adaptation: damage from a channel you've already been hit by in the last hour is reduced by 15%.
 *    Channels are identified by the name they report through Feedback.affected, history is kept for everyone
 *    (so it counts even from before you were Chimera) and saved in adaptation.yml.
 * Both also apply to true damage (see TrueDamage), which doesn't go through the normal damage event.
 */
public final class GlobalAdaptationFlux implements FluxPassive, Listener {
    private static final double CAP = 10.0;            // 5 hearts
    private static final long WINDOW_MS = 1_000L;
    private static final double REDUCTION = 0.15;
    private static final long MEMORY_MS = 3_600_000L;  // 1 hour
    private static final long ATTRIBUTION_MS = 150L;   // damage and "affected by" happen within a few ticks of each other

    private record Attribution(String channel, long at, boolean eligible, UUID source) {}

    private static final class Window { long start; double taken; Window(long s, double t) { start = s; taken = t; } }

    private static final class LastDamage {
        final long at; final double amount; final UUID source; boolean reduced;
        LastDamage(long at, double amount, UUID source, boolean reduced) { this.at = at; this.amount = amount; this.source = source; this.reduced = reduced; }
    }

    private static final Map<UUID, Window> WINDOWS = new HashMap<>();
    private static final Map<UUID, Map<String, Long>> HISTORY = new HashMap<>();
    private static final Map<UUID, Attribution> ATTRIBUTION = new HashMap<>();
    private static final Map<UUID, LastDamage> LAST = new HashMap<>();

    public GlobalAdaptationFlux() { load(); }

    @Override public String id() { return "global_adaptation"; }
    @Override public String name() { return "Global Adaptation"; }
    @Override public String description() {
        return "You can't take more than 5 hearts of damage in one second (the first hit still can). Channels you've been hit by in the last hour deal 15% less.";
    }

    // ------------------------------------------------------------ persistence

    private static File file() { return new File(LimboPlugin.get().getDataFolder(), "adaptation.yml"); }

    private static void load() {
        HISTORY.clear();
        File f = file();
        if (!f.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        long now = System.currentTimeMillis();
        var sec = y.getConfigurationSection("h");
        if (sec == null) return;
        for (String uid : sec.getKeys(false)) {
            try {
                UUID id = UUID.fromString(uid);
                var inner = sec.getConfigurationSection(uid);
                if (inner == null) continue;
                Map<String, Long> m = new HashMap<>();
                for (String ch : inner.getKeys(false)) {
                    long ts = inner.getLong(ch);
                    if (now - ts < MEMORY_MS) m.put(ch.replace('_', ' '), ts);
                }
                if (!m.isEmpty()) HISTORY.put(id, m);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    @Override
    public void onShutdown() {
        YamlConfiguration y = new YamlConfiguration();
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Map<String, Long>> e : HISTORY.entrySet()) {
            for (Map.Entry<String, Long> c : e.getValue().entrySet()) {
                if (now - c.getValue() < MEMORY_MS) y.set("h." + e.getKey() + "." + c.getKey().replace(' ', '_'), c.getValue());
            }
        }
        try {
            LimboPlugin.get().getDataFolder().mkdirs();
            y.save(file());
        } catch (IOException ex) {
            LimboPlugin.get().getLogger().severe("Could not save adaptation.yml: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------ core

    private static boolean active(Player p) {
        return LimboPlugin.get().fluxActive(p, ChimeraDarkness.ID);
    }

    /** Applies the 5-hearts-per-second cap. Returns how much of `amount` is allowed. */
    private static double cap(UUID id, double amount) {
        long now = System.currentTimeMillis();
        Window w = WINDOWS.get(id);
        if (w == null || now - w.start >= WINDOW_MS) { // first hit of a window: unlimited
            WINDOWS.put(id, new Window(now, amount));
            return amount;
        }
        double remaining = CAP - w.taken;
        if (remaining <= 0) return 0;
        double allowed = Math.min(amount, remaining);
        w.taken += allowed;
        return allowed;
    }

    private static Attribution recent(UUID victim, UUID source) {
        Attribution a = ATTRIBUTION.get(victim);
        if (a == null || !a.eligible() || System.currentTimeMillis() - a.at() > ATTRIBUTION_MS) return null;
        return source != null && source.equals(a.source()) ? a : null;
    }

    private static void feedback(Player v) {
        Location at = v.getLocation().add(0, 1, 0);
        ParticleUtil.blissDust(at, ChimeraDarkness.COLOR, 6, 0.4);
        v.getWorld().playSound(at, Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.7f, 1.4f);
    }

    /** Called by TrueDamage before it lowers health. Returns the amount that may actually be dealt. */
    public static double filterTrue(Player attacker, LivingEntity victim, double amount) {
        if (!(victim instanceof Player v) || !active(v)) return amount;
        double out = amount;
        boolean reduced = false;
        if (recent(v.getUniqueId(), attacker.getUniqueId()) != null) { out *= (1 - REDUCTION); reduced = true; }
        out = cap(v.getUniqueId(), out);
        LAST.put(v.getUniqueId(), new LastDamage(System.currentTimeMillis(), out, attacker.getUniqueId(), reduced));
        if (reduced || out < amount) feedback(v);
        return out;
    }

    /** Called by Feedback.affected: the target was hit by `channel` from `source`. */
    public static void onAffected(Entity target, String channel, Player source) {
        if (!(target instanceof Player v) || v.getUniqueId().equals(source.getUniqueId())) return;
        UUID id = v.getUniqueId();
        long now = System.currentTimeMillis();
        Map<String, Long> h = HISTORY.computeIfAbsent(id, k -> new HashMap<>());
        Long prev = h.get(channel);
        boolean eligible = prev != null && now - prev < MEMORY_MS;
        h.put(channel, now);
        ATTRIBUTION.put(id, new Attribution(channel, now, eligible, source.getUniqueId()));

        // the damage may already have landed a moment ago: give the 15% back
        if (eligible && active(v)) {
            LastDamage d = LAST.get(id);
            if (d != null && !d.reduced && now - d.at <= ATTRIBUTION_MS && source.getUniqueId().equals(d.source)) {
                d.reduced = true;
                var max = v.getAttribute(Attribute.MAX_HEALTH);
                double cap = max != null ? max.getValue() : 20.0;
                v.setHealth(Math.min(cap, v.getHealth() + d.amount * REDUCTION));
                feedback(v);
            }
        }
    }

    // ------------------------------------------------------------ normal damage

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (TrueDamage.isApplying()) return; // the tiny hit TrueDamage uses for kill credit
        if (!(e.getEntity() instanceof Player v) || !active(v)) return;

        UUID source = null;
        if (e instanceof EntityDamageByEntityEvent be) {
            if (be.getDamager() instanceof Player p) source = p.getUniqueId();
            else if (be.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player p) source = p.getUniqueId();
        }
        double base = e.getDamage();
        double fin = e.getFinalDamage();
        if (fin <= 0) return;
        boolean reduced = false;
        if (source != null && recent(v.getUniqueId(), source) != null) {
            e.setDamage(base * (1 - REDUCTION));
            fin = e.getFinalDamage();
            reduced = true;
        }
        double allowed = cap(v.getUniqueId(), fin);
        if (allowed <= 0) { e.setCancelled(true); feedback(v); return; }
        if (allowed < fin) { e.setDamage(e.getDamage() * (allowed / fin)); feedback(v); }
        else if (reduced) feedback(v);
        LAST.put(v.getUniqueId(), new LastDamage(System.currentTimeMillis(), allowed, source, reduced));
    }
}
