package me.limbo.smp.darkness;

import org.bukkit.Color;

/**
 * Ignition - Flames, eruption, bursts.
 * GROUND WORK ONLY: nothing implemented yet.
 * To add content later:
 *   addPassive(new SomePassive());        // x2 max
 *   setFluxPassive(new SomeFlux());       // x1
 *   addChannel(new SomeChannel());        // x2 max, each bound to one ChannelInput
 */
public final class IgnitionDarkness extends Darkness {
    public IgnitionDarkness() {
        super("ignition", "Ignition", "Flames, eruption, bursts.", Color.fromRGB(255, 94, 20));
    }
}
