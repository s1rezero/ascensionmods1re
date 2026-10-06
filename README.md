# Ascension (NeoForge 1.21.1) - v1.1.0 (complete)

A galaxy-style upgrade tree driven by XP, with 140 orbs, 12 abilities, an intro cinematic and milestone animations.

## Using it
- Press **.** to open the tree (rebind in Controls > Ascension). Every XP point you gain counts; **every 8 levels = 1 point**.
- Click an available orb to unlock it. Drag to pan, scroll to zoom, **R** recentre, **I** replay the intro, Esc or **.** to close.
- First time you open it, the intro plays: your voice line, the orb pulsing with it, then it collapses into the core and the tree blooms outward. Click or press Space to skip.

## Ability keys
Four keys (default **Z X C V**, rebindable). In the tree, the **ABILITY KEYS** bar at the bottom shows what is on each key:
left-click a slot for the next ability, right-click for the previous. New abilities go on the first free key automatically.
Your keys and cooldowns show in the bottom-right corner of the game screen.

## Milestones (3 per branch, cost 3 points, need 8 / 18 / 30 points spent in the branch)
| Branch | Abilities |
|---|---|
| Combat | Whirling Edge (spin strike), Time Fracture (slow nearby enemies), Blade Maelstrom (6s storm of blades) |
| Mining | Haste Surge (Haste III), Vein Burst (breaks a whole ore vein - needs the right tool), Seismic Pulse (shatters nearby blocks) |
| Exploration | Blink (dash 9 blocks), Sky Leap (launch + slow fall), Starwalk (8s of flight) |
| Inventory | Sort Button (Sort buttons in inventory/chests), Item Magnet (pulls items + XP), Pocket Dimension (opens your ender chest anywhere) |

## Other orb effects
Stat orbs use vanilla attributes (damage, speed, reach, health, armor, gravity, jump, step height, mining speed ...).
Fortune orbs: ores sometimes drop double (0.1 Fortune each = 1/10 of Fortune I). Mending orbs: breaking blocks sometimes repairs your tool.
Crit chance/damage, lifesteal, Ability Cooldown (-3% each, max -50%), Pickup Range (items and XP drift to you),
Max Stack Size (up to Minecraft's hard limit of 99 per stack).

## Commands (operators)
`/ascension addlevels 24`, `/ascension addxp 500`, `/ascension respec`, `/ascension reset`, `/ascension intro` (replay the cinematic).

## Tuning
`data/Progression.java` = XP curve and levels per point. `data/Nodes.java` = every orb's name/value. `data/Ability.java` = base cooldowns.

## Building
Push to GitHub and download the `ascension-jar` artifact from the Actions tab, or run `./gradlew build` with JDK 21.

## Notes
- Stack-size orbs work by giving your stacks a raised limit, so stacks picked up from a chest or the ground merge in your inventory within a moment.
- Starwalk flight is always revoked on logout/death.
- The intro voice is `assets/ascension/sounds/intro_voice.ogg`; `intro_envelope.json` is its loudness curve for the pulsing orb.
