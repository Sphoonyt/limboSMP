package me.limbo.smp.darkness;

import me.limbo.smp.darkness.entropy.DisruptionPassive;
import me.limbo.smp.darkness.entropy.Ruination;
import me.limbo.smp.darkness.entropy.SlicingDecay;
import me.limbo.smp.darkness.entropy.SpinecutterFlux;
import me.limbo.smp.darkness.entropy.StrengthPassive;
import org.bukkit.Color;

/** Entropy - decay, disorder, disruption. Dark crimson red. */
public final class EntropyDarkness extends Darkness {
    public static final String ID = "entropy";
    public static final Color COLOR = Color.fromRGB(150, 20, 40);

    public EntropyDarkness() {
        super(ID, "Entropy", "Decay, disorder, disruption.", COLOR);
        addPassive(new StrengthPassive());
        addPassive(new DisruptionPassive());
        setFluxPassive(new SpinecutterFlux());
        addChannel(new Ruination());    // H
        addChannel(new SlicingDecay()); // R
    }
}
