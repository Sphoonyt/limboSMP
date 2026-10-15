package me.limbo.smp.darkness;

public enum ChannelInput {
    /** Right click while holding (double right click if orb is offhand + sword/axe/empty mainhand). */
    R("R", "Right click"),
    /** Crouch + right click while holding. */
    CR("CR", "Crouch right click"),
    /** Left click while holding. */
    L("L", "Left click"),
    /** Crouch + left click while holding. */
    CL("CL", "Crouch left click"),
    /** On enemy hit with the orb. */
    H("H", "Hit");

    private final String shortName;
    private final String description;

    ChannelInput(String shortName, String description) {
        this.shortName = shortName;
        this.description = description;
    }

    public String shortName() { return shortName; }
    public String description() { return description; }
}
