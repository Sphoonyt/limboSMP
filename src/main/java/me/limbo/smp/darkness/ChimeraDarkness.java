package me.limbo.smp.darkness;

import me.limbo.smp.darkness.chimera.CopycatPassive;
import me.limbo.smp.darkness.chimera.Evolution;
import me.limbo.smp.darkness.chimera.GlobalAdaptationFlux;
import me.limbo.smp.darkness.chimera.ShieldSplinteringPassive;
import me.limbo.smp.darkness.chimera.SilencingMaw;
import org.bukkit.Color;

/** Chimera - adaptation, mimicry, evolution. Green. */
public final class ChimeraDarkness extends Darkness {
    public static final String ID = "chimera";
    public static final Color COLOR = Color.fromRGB(70, 190, 90);

    public ChimeraDarkness() {
        super(ID, "Chimera", "Adaptation, mimicry, evolution.", COLOR);
        addPassive(new ShieldSplinteringPassive());
        addPassive(new CopycatPassive());
        setFluxPassive(new GlobalAdaptationFlux());
        addChannel(new Evolution());     // R
        addChannel(new SilencingMaw());  // L
    }
}
