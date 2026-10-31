package me.limbo.smp.darkness;

import me.limbo.smp.darkness.reflection.InvisibilityFlux;
import me.limbo.smp.darkness.reflection.MirroredEffectsPassive;
import me.limbo.smp.darkness.reflection.PhasingPassive;
import me.limbo.smp.darkness.reflection.RealityShatter;
import me.limbo.smp.darkness.reflection.ReflectedConstructs;
import org.bukkit.Color;

/** Reflection - mirrors, echoes, shattered glass. White / silvery blue (the one darkness allowed pale particles). */
public final class ReflectionDarkness extends Darkness {
    public static final String ID = "reflection";
    /** Silvery blue: main accent. */
    public static final Color COLOR = Color.fromRGB(165, 205, 255);
    /** Near-white highlight. */
    public static final Color WHITE = Color.fromRGB(232, 242, 255);

    public ReflectionDarkness() {
        super(ID, "Reflection", "Mirrors, echoes, deflection.", COLOR);
        addPassive(new MirroredEffectsPassive());
        addPassive(new PhasingPassive());
        setFluxPassive(new InvisibilityFlux());
        addChannel(new RealityShatter());      // R
        addChannel(new ReflectedConstructs()); // L
    }
}
