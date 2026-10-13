package me.limbo.smp.darkness;

import org.bukkit.Color;

/**
 * Inertia - Speed, resistance to change, immovability.
 * GROUND WORK ONLY: nothing implemented yet.
 * To add content later:
 *   addPassive(new SomePassive());        // x2 max
 *   setFluxPassive(new SomeFlux());       // x1
 *   addChannel(new SomeChannel());        // x2 max, each bound to one ChannelInput
 */
public final class InertiaDarkness extends Darkness {
    public InertiaDarkness() {
        super("inertia", "Inertia", "Speed, resistance to change, immovability.", Color.fromRGB(110, 170, 255));
    }
}
