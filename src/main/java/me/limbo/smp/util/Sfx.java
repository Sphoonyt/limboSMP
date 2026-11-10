package me.limbo.smp.util;

import me.limbo.smp.LimboPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Layered sound design. Instead of one vanilla sound, every call plays
 *   - the base sound (slightly randomised in pitch so repeats don't sound identical),
 *   - a low "body" sound under it for weight,
 *   - a high "tail" sound a few ticks later for shimmer / decay,
 * where body and tail are chosen per theme (one per darkness plus rituals and the rift).
 * Use it in place of world.playSound for one-shot casts and impacts, not for per-tick loops.
 */
public final class Sfx {
    private Sfx() {}

    /**
     * Five layers per call: the base sound, a low BODY for weight, a SUB thud under everything, an AIR whoosh a tick
     * later, and a high TAIL for shimmer/decay 3 ticks later. Pitches are jittered a little each time and the layers are
     * spread over a few ticks so a cast reads as one rich sound that never repeats exactly.
     */
    public enum Theme {
        ENTROPY(Sound.ENTITY_WARDEN_ATTACK_IMPACT, 0.55f, Sound.BLOCK_CHAIN_HIT, 1.2f, Sound.ENTITY_IRON_GOLEM_DAMAGE, 0.5f, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.7f),
        IGNITION(Sound.ITEM_FIRECHARGE_USE, 0.6f, Sound.BLOCK_LAVA_POP, 1.1f, Sound.ENTITY_GENERIC_EXPLODE, 1.6f, Sound.BLOCK_FIRE_AMBIENT, 1.4f),
        INERTIA(Sound.ENTITY_WIND_CHARGE_WIND_BURST, 0.8f, Sound.ITEM_TRIDENT_THROW, 1.5f, Sound.ENTITY_PHANTOM_FLAP, 0.6f, Sound.ITEM_ELYTRA_FLYING, 1.6f),
        SERENITY(Sound.BLOCK_BELL_RESONATE, 0.7f, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.7f, Sound.BLOCK_BEACON_AMBIENT, 0.6f, Sound.BLOCK_AMETHYST_CLUSTER_STEP, 1.8f),
        SINGULARITY(Sound.ENTITY_WARDEN_HEARTBEAT, 0.6f, Sound.BLOCK_END_PORTAL_FRAME_FILL, 0.8f, Sound.ENTITY_WITHER_AMBIENT, 0.5f, Sound.BLOCK_PORTAL_AMBIENT, 1.5f),
        REFLECTION(Sound.BLOCK_BEACON_DEACTIVATE, 0.7f, Sound.BLOCK_GLASS_BREAK, 1.9f, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 0.6f, Sound.ENTITY_ALLAY_ITEM_GIVEN, 1.9f),
        CHIMERA(Sound.ENTITY_RAVAGER_ATTACK, 0.7f, Sound.BLOCK_MOSS_BREAK, 1.3f, Sound.ENTITY_EVOKER_CAST_SPELL, 0.7f, Sound.ENTITY_SLIME_SQUISH, 0.8f),
        RITUAL(Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.5f, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.4f, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.5f, Sound.BLOCK_BEACON_POWER_SELECT, 1.2f);

        final Sound body;
        final float bodyPitch;
        final Sound tail;
        final float tailPitch;
        final Sound sub;
        final float subPitch;
        final Sound air;
        final float airPitch;

        Theme(Sound body, float bodyPitch, Sound tail, float tailPitch, Sound sub, float subPitch, Sound air, float airPitch) {
            this.body = body;
            this.bodyPitch = bodyPitch;
            this.tail = tail;
            this.tailPitch = tailPitch;
            this.sub = sub;
            this.subPitch = subPitch;
            this.air = air;
            this.airPitch = airPitch;
        }
    }

    /** Plays `base` with the theme's body, sub, air and tail layers. */
    public static void play(Location at, Sound base, float volume, float pitch, Theme theme) {
        World w = at.getWorld();
        if (w == null) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        float jitter = 1f + (r.nextFloat() - 0.5f) * 0.12f;
        w.playSound(at, base, volume, clamp(pitch * jitter));
        w.playSound(at, theme.body, volume * 0.55f, clamp(theme.bodyPitch * jitter));
        w.playSound(at, theme.sub, volume * 0.30f, clamp(theme.subPitch * jitter));
        Location l = at.clone();
        Bukkit.getScheduler().runTaskLater(LimboPlugin.get(), () ->
                l.getWorld().playSound(l, theme.air, volume * 0.28f, clamp(theme.airPitch * (2f - jitter))), 1L);
        Bukkit.getScheduler().runTaskLater(LimboPlugin.get(), () ->
                l.getWorld().playSound(l, theme.tail, volume * 0.38f, clamp(theme.tailPitch * (2f - jitter))), 3L);
    }

    /** An extra sound at the same moment as a layered one: plain, no body/tail (so a cast isn't layered twice). */
    public static void add(Location at, Sound sound, float volume, float pitch) {
        World w = at.getWorld();
        if (w != null) w.playSound(at, sound, volume, clamp(pitch));
    }

    private static float clamp(float pitch) { return Math.max(0.5f, Math.min(2.0f, pitch)); }
}
