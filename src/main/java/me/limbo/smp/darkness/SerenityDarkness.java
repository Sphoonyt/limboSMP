package me.limbo.smp.darkness;

import me.limbo.smp.darkness.serenity.CalmMending;
import me.limbo.smp.darkness.serenity.CleansingPassive;
import me.limbo.smp.darkness.serenity.ImbalanceRing;
import me.limbo.smp.darkness.serenity.SustainedHeal;
import me.limbo.smp.darkness.serenity.TemporalRepose;
import org.bukkit.Color;

/** Vitality - stillness, nature, healing. Cherry blossom pink. */
public final class SerenityDarkness extends Darkness {
    public static final String ID = "vitality";
    public static final Color COLOR = Color.fromRGB(255, 175, 205);

    public SerenityDarkness() {
        super(ID, "Vitality", "Stillness, nature, healing.", COLOR);
        addPassive(new SustainedHeal());
        addPassive(new CleansingPassive());
        setFluxPassive(new CalmMending());
        addChannel(new ImbalanceRing());   // R
        addChannel(new TemporalRepose());  // H
    }
}
