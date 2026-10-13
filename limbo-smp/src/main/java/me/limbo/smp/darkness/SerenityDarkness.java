package me.limbo.smp.darkness;

import org.bukkit.Color;

/**
 * Serenity - Stillness, nature, healing.
 * GROUND WORK ONLY: nothing implemented yet.
 * To add content later:
 *   addPassive(new SomePassive());        // x2 max
 *   setFluxPassive(new SomeFlux());       // x1
 *   addChannel(new SomeChannel());        // x2 max, each bound to one ChannelInput
 */
public final class SerenityDarkness extends Darkness {
    public SerenityDarkness() {
        super("serenity", "Serenity", "Stillness, nature, healing.", Color.fromRGB(90, 220, 130));
    }
}
