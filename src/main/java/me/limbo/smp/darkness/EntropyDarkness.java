package me.limbo.smp.darkness;

import org.bukkit.Color;

/**
 * Entropy - Decay, disorder, disruption.
 * GROUND WORK ONLY: nothing implemented yet.
 * To add content later:
 *   addPassive(new SomePassive());        // x2 max
 *   setFluxPassive(new SomeFlux());       // x1
 *   addChannel(new SomeChannel());        // x2 max, each bound to one ChannelInput
 */
public final class EntropyDarkness extends Darkness {
    public EntropyDarkness() {
        super("entropy", "Entropy", "Decay, disorder, disruption.", Color.fromRGB(170, 60, 210));
    }
}
