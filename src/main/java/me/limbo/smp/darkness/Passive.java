package me.limbo.smp.darkness;

import org.bukkit.entity.Player;

/**
 * A flat passive. Active while the player has a null orb in their main inventory.
 * Lifecycle is managed by PassiveManager: onActivate -> onTick (every tick-interval) -> onDeactivate.
 * Use onActivate/onDeactivate for effects/attribute modifiers so they're always cleaned up.
 * Passives are NOT affected by siphon.
 * If the implementing class also implements org.bukkit.event.Listener it is auto-registered.
 * Check LimboPlugin#passivesActive(player, darknessId) inside event handlers.
 */
public interface Passive {
    String id();
    String name();
    String description();

    default void onActivate(Player player) {}
    default void onTick(Player player) {}
    default void onDeactivate(Player player) {}
    /** Plugin disabling: cancel tasks, restore state. */
    default void onShutdown() {}
}
