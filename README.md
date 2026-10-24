# LimboSMP (ground work)

Paper 1.21.11, Java 21, Gradle (Kotlin DSL). `gradle build` -> `build/libs/LimboSMP-0.1.0.jar`.

Implemented: Incendiary (was Ignition), Vitality (was Serenity), Entropy, Inertia, Reflection, Singularity (packages under `darkness/`). All 6 registry slots are used.

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

## Rifts
Particle constructs with an invisible click hitbox. 3 spawn every 24h at random sun-exposed spots inside the
world border (`/limbo rift interval|count|spawn|info`). Click one to open the craft menu; ops get an editor
(deposit items + confirm) that writes `recipes.yml`.

## Player souls
Killing a player drops their soul (their head, glowing, with the darkness they had). Crouch + right click opens a menu of
online players with that darkness; left click one to track them (particle line, 20 blocks, 4 minutes, ends if either hits
the other). At a rift, the Rift Teleport button (left click, stand still 10s) consumes a soul and sends you to the farthest rift.

## Darkness fatigue
Using both of your channels within 8 seconds of each other puts you in fatigue for 30 seconds: your passives only work
while you are holding the null totem (instead of just carrying it).
