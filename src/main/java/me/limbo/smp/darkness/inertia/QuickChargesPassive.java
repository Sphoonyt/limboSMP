package me.limbo.smp.darkness.inertia;

import me.limbo.smp.darkness.FluxPassive;

/**
 * Inertia's flux passive (totem held): your wind charges have half the cooldown they normally do (whole seconds, rounded down: a 5 second cooldown
 * becomes 2). The cooldown itself is applied by WindChargeCooldown, which asks whether this flux passive is running.
 */
public final class QuickChargesPassive implements FluxPassive {
    @Override public String id() { return "quick_charges"; }
    @Override public String name() { return "Quick Charges"; }
    @Override public String description() { return "Your wind charges have half the cooldown (rounded down to whole seconds)."; }
}
