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

    public enum Theme {
        ENTROPY(Sound.ENTITY_WARDEN_ATTACK_IMPACT, 0.55f, Sound.BLOCK_CHAIN_HIT, 1.2f),
        IGNITION(Sound.ITEM_FIRECHARGE_USE, 0.6f, Sound.BLOCK_LAVA_POP, 1.1f),
        INERTIA(Sound.ENTITY_WIND_CHARGE_WIND_BURST, 0.8f, Sound.ITEM_TRIDENT_THROW, 1.5f),
        SERENITY(Sound.BLOCK_BELL_RESONATE, 0.7f, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.7f),
        SINGULARITY(Sound.ENTITY_WARDEN_HEARTBEAT, 0.6f, Sound.BLOCK_END_PORTAL_FRAME_FILL, 0.8f),
        REFLECTION(Sound.BLOCK_BEACON_DEACTIVATE, 0.7f, Sound.BLOCK_GLASS_BREAK, 1.9f),
        CHIMERA(Sound.ENTITY_RAVAGER_ATTACK, 0.7f, Sound.BLOCK_MOSS_BREAK, 1.3f),
        RITUAL(Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.5f, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.4f);

        final Sound body;
        final float bodyPitch;
        final Sound tail;
        final float tailPitch;

        Theme(Sound body, float bodyPitch, Sound tail, float tailPitch) {
            this.body = body;
            this.bodyPitch = bodyPitch;
            this.tail = tail;
            this.tailPitch = tailPitch;
        }
    }

    /** Plays `base` with the theme's body and tail layers. */
    public static void play(Location at, Sound base, float volume, float pitch, Theme theme) {
        World w = at.getWorld();
        if (w == null) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        float jitter = 1f + (r.nextFloat() - 0.5f) * 0.08f;
        w.playSound(at, base, volume, clamp(pitch * jitter));
        w.playSound(at, theme.body, volume * 0.55f, clamp(theme.bodyPitch * jitter));
        Location tailAt = at.clone();
        Bukkit.getScheduler().runTaskLater(LimboPlugin.get(), () ->
                tailAt.getWorld().playSound(tailAt, theme.tail, volume * 0.35f, clamp(theme.tailPitch * (2f - jitter))), 3L);
    }

    /** An extra sound at the same moment as a layered one: plain, no body/tail (so a cast isn't layered twice). */
    public static void add(Location at, Sound sound, float volume, float pitch) {
        World w = at.getWorld();
        if (w != null) w.playSound(at, sound, volume, clamp(pitch));
    }

    private static float clamp(float pitch) { return Math.max(0.5f, Math.min(2.0f, pitch)); }
}
