# Ascension (NeoForge 1.21.1) - Stage 1

A full-screen, galaxy-style upgrade tree driven by XP.

## Using it
- Press **.** (period) to open the tree. Rebind it in Controls > Ascension.
- Every point of XP you gain also counts toward your Ascension level. **Every 8 levels = 1 upgrade point.**
- Click an available orb to unlock it. Drag to pan, scroll to zoom, **R** to recentre, Esc or **.** to close.
- Hover an orb to see what it does and what it needs; the path back to the core lights up.

## Branches (35 orbs each, 3 milestones each)
Combat, Mining, Exploration, Inventory. Milestones cost 3 points and need 8 / 18 / 30 points already spent in that branch.

## Testing commands (operators)
- `/ascension addlevels 24`   gain levels (3 points)
- `/ascension addxp 500`      gain XP
- `/ascension respec`         refund every orb
- `/ascension reset`          wipe XP and orbs

## What works in this stage
Live now (vanilla attributes): attack damage / speed / reach, sweeping, armor, max health, knockback resistance,
movement speed, mining speed / efficiency / underwater mining, block reach, luck, lower gravity, jump height,
step height, safe fall distance, fall damage, sneak speed.
Stored but not active yet (shown as "Takes effect in a later update"): Fortune, Mending, crit, lifesteal, cooldowns,
stack size, pickup range and the 12 milestone abilities.

## Tuning
`data/Progression.java` holds the XP curve and levels-per-point. `data/Nodes.java` holds every orb's name and value.

## Building
Push to GitHub and download the `ascension-jar` artifact from the Actions tab, or run `./gradlew build` with JDK 21.
If the Actions tab shows "Get started", the hidden .github folder didn't upload: create `.github/workflows/build.yml`
on GitHub and paste in `github-workflow-build.yml`.

## Audio (for Stage 2)
`assets/ascension/sounds/intro_voice.ogg` is registered as the sound `ascension:intro_voice`, and
`assets/ascension/intro_envelope.json` holds its loudness curve (for the pulsing orb).
