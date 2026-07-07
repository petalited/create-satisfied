# Create: Satisfied

A NeoForge 1.21.1 addon for [Create](https://www.curseforge.com/minecraft/mc-mods/create), bringing
Satisfactory-style throughput clarity to Create's machines: instant feedback, items/min as the
primary unit, and color-coded state instead of raw tick/RPM math.

## Features

- **Goggle overlay** — wear goggles and look at a running machine to see live items/min (or mB/min
  for fluids), stress (SU) cost at its current RPM, and a clear Overstressed / Stalled / Too-slow
  state instead of just "nothing happening."
- **JEI recipe sliders** — every Create recipe category gets a draggable RPM (or stack-size, where
  RPM doesn't apply) slider right on its JEI page, live-updating throughput and stress cost as you
  drag. No confirm button.
- **Sequenced Assembly support** — full breakdown of multi-step assembly recipes (Track, etc.),
  including per-machine stress cost and every real input (including what's deployed, not just the
  main item).
- **Configurable display units** — scroll while looking at a machine (or hovering a JEI slider) to
  cycle between per-minute, per-tick, and ticks-per-item/mB, matching however you think about
  Create's own tick-based timing. Also reachable from the mod's config screen.

Covers Millstone, Crushing Wheels, Mechanical Press/Mixer/Saw, Deployer, Encased Fan, Belts, Pumps,
and Spouts.

## Requirements

- NeoForge 1.21.1 (21.1.0+)
- [Create](https://www.curseforge.com/minecraft/mc-mods/create) 6.0.0+
- [JEI](https://www.curseforge.com/minecraft/mc-mods/jei) (optional — only the JEI sliders need it,
  the goggle overlay works without it)

## Building

```
./gradlew build
```

Run a dev client with `./gradlew runClient`. If you run into missing libraries or dependency
issues, `./gradlew --refresh-dependencies` usually fixes it.

## Mapping Names

This project uses Mojang's official mapping names, which are covered by their own license - see
the mapping file itself or the reference copy at
https://github.com/NeoForged/NeoForm/blob/main/Mojang.md.
