# LimboSMP (ground work)

Paper 26.3, Java 25, Gradle (Kotlin DSL). `gradle build` -> `build/libs/LimboSMP-0.1.0.jar`.

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
`/limbo unlock|lock <nether|villagers|rifts|enchants>`, `/limbo unlocks`. Everything starts locked. While `rifts` is locked rifts don't auto-spawn.

## Cursed item
Admins build a pool: `/limbo cursed pool add [item]` (no item = the one in your hand) / `remove` / `list` / `clear`. `/limbo cursed roll` makes ONE random item from the pool the cursed item (`set <item>` forces one). While it is the cursed item it glows in your hotbar or offhand, and holding it lets you use your first channel at double cooldown (no passives). `cursed-items.rotation-hours` > 0 re-rolls automatically; 0 (default) is manual only.

## Devotion Core (reroll item) and rituals
A head item (skin of NormalPlayerSus). Right click: it is consumed and a circle (ritual.radius) stays for ritual.seconds with a floating countdown; when it ends everyone inside is rerolled with the slot machine and the coordinates are announced. Admin: `/limbo give <player> core [n]`. Totem rewards are claimed with `/limbo claim`.

## Soul tracking
Started from a rift's menu (Soul Tracking button), not from the soul item.

## Chimera (green)
Flat: Shield Splintering, Copycat (copies the hit enemy's second flat passive for 30s). Flux: Global Adaptation (5 hearts/second cap after the first hit, 15% less from channels that already hit you in the last hour; history in adaptation.yml). Channels: R Evolution (30s, 180s cd), L Silencing Maw (45s cd). Silenced players lose passives, flux and channels (`StatusManager#silence`).

## Ability notes
- Wind charges: `wind-charge.cooldown-seconds` (default 5). Inertia's Quick Charges flux passive halves it, rounded down.
- Entropy: Strength I (permanent), Enchain on R (12s), Black Flash on H.
- Reflection: Reflected Construct (L): 6 block blink in your movement direction, 5s true invisibility, exploding decoy, 2 charges of 45s.
- Cursed items: first channel only, double cooldown, no passives or flux.

## Resource pack / model effects
`resourcepack/` holds the pack assets. Totems use `minecraft:totems/<darkness id>` item models (one texture per darkness, Chimera's is the Entropy texture recoloured green). Flat effect models (`limbo:effects/*`) are shown with ItemDisplay entities through `util/FlatFx`: black hole ring, Enchain chain links (`util/ChainFx`), Propel impact, Temporal Repose halo, Reality Shatter shards. Replace the textures in `assets/limbo/textures/item/effects/` with final art, keep the file names.


## On-screen status feedback (26.3 `/posteffect`)
`status/ScreenFx` shows duration statuses as screen shaders from the pack (`assets/limbo/post_effect/*`, one tint shader `limbo:post/screen_tint`).
Victims: Enchain, Silencing Maw, Temporal Repose, Axiom Dagger ground, Black Hole, Kinetic Thief (stun and slow share a look, the slow covers less), Spinecutter. You: Evolution, Black Flash buff, Reflected Construct invisibility.
Each look has 3 strengths (`name_0/1/2`) that are stepped through to fade in/out; the blinky stuns are capped at 60%; at most `screen-fx.max-stacked` looks are drawn; the ambient darkness-fatigue look is always hidden while a status look shows.
`/limbo fx mode shader|bossbar|both|off`, `/limbo fx test <effect> [player]`, `/limbo fx clear [player]`.
Regenerate looks: `python3 tools/gen_post_effects.py`. Stuns (Kinetic Thief, Spinecutter) are repeated-teleport stuns (`StatusManager#holdInPlace`).

## Auto enchants
Holding your null totem enchants the gear you carry (armor, hotbar, offhand) for your darkness; removed when you stop holding it and before items leave you. See `enchant/AutoEnchants`.

## Resource pack
The status shaders need the pack on the client. Set `resource-pack.url`/`sha1` and `resource-pack.required: true` in config.yml to force it (players who decline are kicked by the client). `/limbo reroll [player|all]` rerolls with the slot machine.
