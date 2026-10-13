# LimboSMP (ground work)

Paper 1.21.11, Java 21, Gradle (Kotlin DSL). `gradle build` -> `build/libs/LimboSMP-0.1.0.jar`.

No darkness content is implemented. The four darknesses are empty stubs in `darkness/`.

## Adding content later
```java
public final class IgnitionDarkness extends Darkness {
    public IgnitionDarkness() {
        super("ignition", "Ignition", "...", Color.fromRGB(255, 94, 20));
        addPassive(new MyPassive());      // max 2, lifecycle: onActivate/onTick/onDeactivate
        setFluxPassive(new MyFlux());     // only while orb held (main/off hand)
        addChannel(new MyChannel());      // max 2, each bound to a ChannelInput (R, CR, L, CL, H)
    }
}
```
Channels extend `Channel` (id, name, description, input, cooldownMillis) and return `true` from
`execute(ctx)` when used (starts the cooldown).
ALL damage/targeting goes through `Targeting` so /trust and kill credit work.

## Commands
- `/limbo darknessinfo`
- `/trust <player>`, `/trust remove <player>`, `/trust list` (also `/limbo trust ...`)
- Admin (`limbo.admin`): `/limbo set|reset|siphon|unsiphon|give|rift|cooldown|reload|debug`

## Data files (plugins/LimboSMP/)
`players.yml` (darkness + siphon expiry), `trust.yml`, `rifts.yml`, `config.yml`
