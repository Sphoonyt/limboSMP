package me.limbo.smp.darkness;

import me.limbo.smp.darkness.singularity.AntiGravityPassive;
import me.limbo.smp.darkness.singularity.AxiomDagger;
import me.limbo.smp.darkness.singularity.BalancedFallPassive;
import me.limbo.smp.darkness.singularity.BlackHole;
import me.limbo.smp.darkness.singularity.InfinityFlux;
import org.bukkit.Color;

/** Singularity - gravity, collapse, the void between. Purple. */
public final class SingularityDarkness extends Darkness {
    public static final String ID = "singularity";
    public static final Color COLOR = Color.fromRGB(150, 70, 230);
    /** Lighter purple used for rims and glints. */
    public static final Color LIGHT = Color.fromRGB(200, 150, 255);

    public SingularityDarkness() {
        super(ID, "Singularity", "Gravity, collapse, the void between.", COLOR);
        addPassive(new BalancedFallPassive());
        addPassive(new AntiGravityPassive());
        setFluxPassive(new InfinityFlux());
        addChannel(new AxiomDagger()); // L
        addChannel(new BlackHole());   // R
    }
}
