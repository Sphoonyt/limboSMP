package me.limbo.smp.darkness;

import me.limbo.smp.darkness.ignition.DroughtCompression;
import me.limbo.smp.darkness.ignition.FireResistancePassive;
import me.limbo.smp.darkness.ignition.FlameTouchPassive;
import me.limbo.smp.darkness.ignition.JetLaunchFlux;
import me.limbo.smp.darkness.ignition.OpenFurnace;
import org.bukkit.Color;

/** Incendiary - flames, eruption, bursts. Fiery orange. */
public final class IgnitionDarkness extends Darkness {
    public static final String ID = "incendiary";
    public static final Color COLOR = Color.fromRGB(255, 110, 20);

    public IgnitionDarkness() {
        super(ID, "Incendiary", "Flames, eruption, bursts.", COLOR);
        addPassive(new FireResistancePassive());
        addPassive(new FlameTouchPassive());
        setFluxPassive(new JetLaunchFlux());
        addChannel(new DroughtCompression()); // R
        addChannel(new OpenFurnace());        // L
    }
}
