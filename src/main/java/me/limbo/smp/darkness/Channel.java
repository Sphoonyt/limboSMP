package me.limbo.smp.darkness;

/**
 * An activated ability bound to one ChannelInput. A darkness has at most 2.
 * Blocked while the player is siphoned. Cooldown is handled by InputDispatcher.
 *
 * Rules for implementers:
 *  - Damage/targeting MUST use Targeting / TrueDamage (respects /trust, ceasefire + kill credit).
 *  - Visuals: use ParticleUtil with the darkness colour.
 *  - Return true if the channel was actually used (starts the cooldown), false to cancel (no cooldown).
 *  - Implement org.bukkit.event.Listener on the channel if it needs events (auto-registered).
 */
public abstract class Channel {
    private final String id;
    private final String name;
    private final String description;
    private final ChannelInput input;
    private final long cooldownMillis;

    protected Channel(String id, String name, String description, ChannelInput input, long cooldownMillis) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.input = input;
        this.cooldownMillis = cooldownMillis;
    }

    public abstract boolean execute(ChannelContext ctx);

    /** Plugin disabling: cancel tasks, restore state. */
    public void onShutdown() {}

    public String id() { return id; }
    public String name() { return name; }
    public String description() { return description; }
    public ChannelInput input() { return input; }
    public long cooldownMillis() { return cooldownMillis; }
}
