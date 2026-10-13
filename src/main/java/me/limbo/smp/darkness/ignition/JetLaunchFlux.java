package me.limbo.smp.darkness.ignition;

import io.papermc.paper.event.player.PlayerInputEvent;
import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.FluxPassive;
import me.limbo.smp.darkness.IgnitionDarkness;
import me.limbo.smp.util.Msg;
import me.limbo.smp.util.ParticleUtil;
import me.limbo.smp.util.TimeUtil;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Press space again in the air (orb held) -> fire burst at your feet = double jump.
 * Fixed vertical boost, strong propulsion toward where you look.
 * Cooldown (10s) starts when you land.
 */
public final class JetLaunchFlux implements FluxPassive, Listener {
    private static final String CD_KEY = "ignition:jet_launch";
    private static final long COOLDOWN_MS = 10_000L;
    private static final double VERTICAL = 0.85;     // always the same
    private static final double HORIZONTAL = 1.5;    // propulsion along look direction

    private final Map<UUID, Boolean> jumpHeld = new HashMap<>();
    private final Set<UUID> usedInAir = new HashSet<>();

    @Override public String id() { return "jet_launch"; }
    @Override public String name() { return "Jet Launch"; }
    @Override public String description() { return "Press space again in the air to burst fire at your feet and launch where you look. 10s cooldown after landing."; }

    @Override public void onDeactivate(Player p) { jumpHeld.remove(p.getUniqueId()); }

    @EventHandler
    public void onInput(PlayerInputEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        boolean jump = e.getInput().isJump();
        boolean was = jumpHeld.getOrDefault(id, false);
        jumpHeld.put(id, jump);
        if (!jump || was) return; // only the rising edge of a space press

        LimboPlugin pl = LimboPlugin.get();
        if (!pl.fluxActive(p, IgnitionDarkness.ID)) return;
        if (p.isOnGround() || p.isFlying() || p.isGliding() || p.isSwimming() || p.isInWater() || p.isInsideVehicle()) return;
        if (usedInAir.contains(id)) return;

        long left = pl.dispatcher().cooldowns().remaining(id, CD_KEY);
        if (left > 0) {
            p.sendActionBar(Msg.parse("<gray>Jet Launch <dark_gray>- <red>" + TimeUtil.seconds(left)));
            return;
        }
        launch(p);
    }

    private void launch(Player p) {
        UUID id = p.getUniqueId();
        LimboPlugin pl = LimboPlugin.get();

        Vector dir = p.getLocation().getDirection();
        Vector horiz = new Vector(dir.getX(), 0, dir.getZ());
        double len = horiz.length();
        Vector vel = new Vector(0, VERTICAL, 0);
        if (len > 0.05) {
            horiz.normalize().multiply(HORIZONTAL * Math.min(1.0, len * 1.5));
            vel.setX(horiz.getX()).setZ(horiz.getZ());
        }
        p.setVelocity(vel);
        usedInAir.add(id);

        Location feet = p.getLocation();
        World w = feet.getWorld();
        ParticleUtil.burst(feet, IgnitionDarkness.COLOR, 40);
        w.spawnParticle(Particle.FLAME, feet, 50, 0.3, 0.1, 0.3, 0.15);
        w.spawnParticle(Particle.EXPLOSION, feet, 1);
        w.spawnParticle(Particle.LARGE_SMOKE, feet, 12, 0.3, 0.1, 0.3, 0.05);
        w.playSound(feet, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.4f);
        w.playSound(feet, Sound.ENTITY_BLAZE_SHOOT, 1f, 0.8f);

        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                ticks += 2;
                boolean gone = !p.isOnline() || p.isDead();
                boolean landed = ticks > 6 && (p.isOnGround() || p.isInWater() || p.isFlying() || p.isInsideVehicle());
                if (gone || landed || ticks > 400) {
                    usedInAir.remove(id);
                    if (!gone) pl.dispatcher().cooldowns().set(id, CD_KEY, COOLDOWN_MS);
                    cancel();
                    return;
                }
                Location l = p.getLocation();
                l.getWorld().spawnParticle(Particle.FLAME, l, 3, 0.15, 0.05, 0.15, 0.02);
                ParticleUtil.blissDust(l, IgnitionDarkness.COLOR, 3, 0.15);
            }
        }.runTaskTimer(pl, 2L, 2L);
    }
}
