package me.limbo.smp.darkness;

import me.limbo.smp.darkness.inertia.ComboPassive;
import me.limbo.smp.darkness.inertia.Conversion;
import me.limbo.smp.darkness.inertia.Propel;
import me.limbo.smp.darkness.inertia.SpeedPassive;
import me.limbo.smp.darkness.inertia.UnstoppableForceFlux;
import org.bukkit.Color;

/** Inertia - speed, resistance to change, immovability. Teal (light and dark). */
public final class InertiaDarkness extends Darkness {
    public static final String ID = "inertia";
    /** Light teal: main accent. */
    public static final Color LIGHT = Color.fromRGB(64, 224, 208);
    /** Dark teal: used as the start colour of dust transitions. */
    public static final Color DARK = Color.fromRGB(8, 82, 86);
    public static final Color COLOR = LIGHT;

    public InertiaDarkness() {
        super(ID, "Inertia", "Speed, resistance to change, immovability.", COLOR);
        addPassive(new SpeedPassive());
        addPassive(new ComboPassive());
        setFluxPassive(new UnstoppableForceFlux());
        addChannel(new Propel());     // R
        addChannel(new Conversion()); // L
    }
}
