package me.limbo.smp.darkness;

import me.limbo.smp.darkness.ignition.DroughtCompression;
import me.limbo.smp.darkness.ignition.FireResistancePassive;
import me.limbo.smp.darkness.ignition.FlameTouchPassive;
import me.limbo.smp.darkness.ignition.JetLaunchFlux;
import me.limbo.smp.darkness.ignition.Overheat;
import org.bukkit.Color;

/** Ignition - flames, eruption, bursts. Fiery orange. */
public final class IgnitionDarkness extends Darkness {
    public static final String ID = "ignition";
    public static final Color COLOR = Color.fromRGB(255, 110, 20);

    public IgnitionDarkness() {
        super(ID, "Ignition", "Flames, eruption, bursts.", COLOR);
        addPassive(new FireResistancePassive());
        addPassive(new FlameTouchPassive());
        setFluxPassive(new JetLaunchFlux());
        addChannel(new DroughtCompression()); // R
        addChannel(new Overheat());           // L
    }
}
