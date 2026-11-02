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

## Events (ops: `/limbo event ...`)
- `start|stop server_start`: the deity at spawn wakes, tells people to group up, the slot-machine darkness roll, cursed items revealed, the Big Rift sends each group out. Everything is timed in `events.server-start.*`.
- `start|end|stop progression`: the transition. Deity summons up to 8 people near it, marks (purple + glowing), launches them, netherite stones appear; when every stone has a marked player on it the deity explodes, the border grows, Nether/villagers/enchants unlock, holders get a null totem (claim from chat) + 3 rerolls.
- `/limbo event deity <spawn [here]|set|remove|awaken|sleep|say <text>|shatter>`: place and test the deity. Voice lines: `events.deity.lines.*`.
- Admins in spectator are never picked or teleported.

## Progression locks
`/limbo unlock|lock <nether|villagers|rifts|enchants>`, `/limbo unlocks`. Everything starts locked. While `rifts` is locked Entropy only gets +1.5 attack damage instead of Strength I and rifts don't auto-spawn.

## Cursed items
`/limbo cursed [roll|on|off]`. A different set of inert items is active each day (`cursed-items.*`). Holding one runs your passives and lets you use your first channel at double cooldown.

## Devotion Core (reroll item) and rituals
A head item (skin of NormalPlayerSus). Right click: it is consumed and a circle (ritual.radius) stays for ritual.seconds with a floating countdown; when it ends everyone inside is rerolled with the slot machine and the coordinates are announced. Admin: `/limbo give <player> core [n]`. Totem rewards are claimed with `/limbo claim`.

## Soul tracking
Started from a rift's menu (Soul Tracking button), not from the soul item.

## Chimera (green)
Flat: Shield Splintering, Copycat (copies the hit enemy's second flat passive for 30s). Flux: Global Adaptation (5 hearts/second cap after the first hit, 15% less from channels that already hit you in the last hour; history in adaptation.yml). Channels: R Evolution (30s, 180s cd), L Silencing Maw (45s cd). Silenced players lose passives, flux and channels (`StatusManager#silence`).
