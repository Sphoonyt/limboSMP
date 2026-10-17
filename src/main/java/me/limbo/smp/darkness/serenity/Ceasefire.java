package me.limbo.smp.darkness.serenity;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.Channel;
import me.limbo.smp.darkness.ChannelContext;
import me.limbo.smp.darkness.ChannelInput;
import me.limbo.smp.darkness.SerenityDarkness;
import me.limbo.smp.util.Msg;
import me.limbo.smp.util.ParticleUtil;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * R - an expanding wave reaches 10 blocks. Every player it reaches (you and allies included)
 * can't hit other players for 4s. The CASTER additionally can't be hit by other players.
 * Enforced in StatusManager + Targeting. 80s cooldown.
 */
public final class Ceasefire extends Channel {
    private static final double RANGE = 10.0;
    private static final int WAVE_STEPS = 10;      // 10 steps x 2 ticks = 1s expansion
    private static final int CEASEFIRE_TICKS = 80; // 4s

    public Ceasefire() {
        super("ceasefire", "Ceasefire",
                "Wave that stops players hitting players.",
                ChannelInput.R, 80_000L);
    }

    @Override
    public boolean execute(ChannelContext ctx) {
        Player caster = ctx.player();
        LimboPlugin pl = LimboPlugin.get();
        Location origin = caster.getLocation().clone();
        World w = origin.getWorld();
        Set<UUID> caught = new HashSet<>();

        w.playSound(origin, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f, 0.7f);
        w.playSound(origin, Sound.ENTITY_ALLAY_AMBIENT_WITH_ITEM, 1f, 0.8f);
        caster.sendActionBar(Msg.parse("<#ffafcd>Ceasefire"));

        new BukkitRunnable() {
            int step = 0;
            @Override public void run() {
                step++;
                double r = RANGE * step / WAVE_STEPS;
                for (double h : new double[]{0.1, 1.0, 2.0}) {
                    Location ring = origin.clone().add(0, h, 0);
                    ParticleUtil.ring(ring, r, (int) Math.max(24, r * 10), SerenityDarkness.COLOR);
                }
                for (int i = 0; i < 12; i++) {
                    double a = Math.random() * Math.PI * 2;
                    w.spawnParticle(Particle.CHERRY_LEAVES, origin.clone().add(Math.cos(a) * r, 1 + Math.random(), Math.sin(a) * r), 1, 0.1, 0.1, 0.1, 0.01);
                }
                for (Player t : w.getPlayers()) {
                    if (t.getGameMode() == GameMode.SPECTATOR || t.isDead()) continue;
                    if (t.getLocation().distance(origin) > r) continue;
                    if (!caught.add(t.getUniqueId())) continue;
                    boolean isCaster = t.getUniqueId().equals(caster.getUniqueId());
                    pl.status().setCeasefire(t.getUniqueId(), CEASEFIRE_TICKS, isCaster);
                    t.sendActionBar(Msg.parse(isCaster
                            ? "<#ffafcd>Ceasefire <gray>- you can't hit players and players can't hit you (4s)"
                            : "<#ffafcd>Ceasefire <gray>- you can't hit other players (4s)"));
                }
                if (step >= WAVE_STEPS) cancel();
            }
        }.runTaskTimer(pl, 0L, 2L);

        // pink shell on everyone currently under ceasefire
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                ticks += 4;
                if (ticks > 20 + CEASEFIRE_TICKS + 8) { cancel(); return; }
                for (UUID id : caught) {
                    Player t = pl.getServer().getPlayer(id);
                    if (t == null || !pl.status().isCeasefire(id)) continue;
                    ParticleUtil.ring(t.getLocation().add(0, 0.2, 0), 0.8, 14, SerenityDarkness.COLOR);
                    if (pl.status().isProtected(id)) { // full shell for the caster only
                        ParticleUtil.ring(t.getLocation().add(0, 1.0, 0), 0.8, 14, SerenityDarkness.COLOR);
                        ParticleUtil.ring(t.getLocation().add(0, 1.8, 0), 0.6, 10, SerenityDarkness.COLOR);
                    }
                }
            }
        }.runTaskTimer(pl, 0L, 4L);
        return true;
    }
}
