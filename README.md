# WPO Environmental Expansion 1.20.1

Original add-on mod for Water Physics Overhaul, maintained for Forge 1.20.1.

## What It Adds

Environmental Expansion adds weather-driven outdoor water behavior on top of the base WPO fluid simulation.

The current add-on includes:

- rain barrels and cisterns for passive collection
- roof collectors, ground basins, and intake grate collectors
- rain-fed puddle growth
- distant rain catch-up for newly visited areas
- terrain absorption and later release
- evaporation and snowmelt
- drought tracking
- seasonal multipliers
- biome-based climate profiling for vanilla and modded biomes

## Collector Blocks

The placeable content in this module is built around collector-style storage blocks:

- `Rain Barrel`
- `Cistern`
- `Roof Collector`
- `Ground Basin`
- `Intake Grate Collector`

These blocks either collect rain directly, drain nearby surface water, push stored water downward, or combine those roles depending on their configured collector profile.

## Weather And Hydrology Systems

The environmental ticker samples outdoor columns around active players on a fixed interval and can apply several systems:

- rain accumulation into cauldrons and WPO surface water
- flood amplification during thunderstorms
- evaporation from exposed surface water
- absorption of surface water into suitable terrain
- release of stored water back to the surface under the right conditions
- snow and snow-block melt into WPO water
- ambient wetness memory so unloaded rainy areas can still materialize puddles later

The main world state persisted by the add-on includes drought score, ambient wetness, and absorbed ground water.

## Biome Climate Profiles

Environmental Expansion applies biome climate profiles on top of the global config values.

- Each biome resolves into one of five archetypes: `arid drought`, `tropical monsoon`, `boreal wet`, `snowmelt alpine`, or `balanced temperate`.
- On server or overworld load, missing biome entries are generated from registered biome information such as biome id naming and climate-related values.
- Generated and refined results are stored in:

```text
config/wpo_environmental_expansion/biome-profiles.json
```

- During play, loaded terrain can refine a biome profile using real observed surface blocks.
- Refinement is capped. After a biome reaches its observation limit, it stops collecting additional live samples and uses the stored profile it already has.

Base generation uses climate and biome-name hints. Live refinement uses block signals like:

- `snow`, `ice`, and similar names for colder and more frozen behavior
- `sand`, `red_sand`, `terracotta`, `cactus`, and similar names for hotter and drier behavior
- `mud`, `moss`, `mangrove`, `clay`, `seagrass`, and similar names for wetter and more retentive behavior
- `jungle` and `bamboo` for hotter and wetter behavior
- `spruce`, `podzol`, and `fern` for cooler and wetter behavior

Those archetypes act as multipliers on the existing environmental systems rather than replacing them.

They currently affect:

- rain buildup and puddle chance
- thunder flood amplification
- evaporation strength
- soil absorption and release behavior
- snowmelt runoff
- collector rain capture rate
- ambient wetness catch-up and dry-down

## Configuration

The common config is stored at:

```text
config/wpo_environmental_expansion/common.toml
```

The in-game config screen exposes the main systems and tuning values for:

- update interval and sampling radius
- rain chance and intensity
- storm intensity
- evaporation and absorption
- distant rain catch-up
- drought behavior
- season length and seasonal multipliers
- collector/storage values

## Notes

- Pipez is the recommended companion mod for external fluid transport when using the WPO add-ons.
- This add-on depends on both `SKDS-Core-1.20.1` and `Water-Physics-Overhaul-1.20.1`.

## Credits

- Original Water Physics Overhaul work: `Sasai_Kudasai_BM`
- 1.18.2 work used in the porting path: `Felicis`
- 1.20.1 port and repository maintenance: [`dev-willbird1936`](https://github.com/dev-willbird1936)

## Related Repositories

- [`SKDS-Core-1.20.1`](https://github.com/dev-willbird1936/SKDS-Core-1.20.1)
- [`Water-Physics-Overhaul-1.20.1`](https://github.com/dev-willbird1936/Water-Physics-Overhaul-1.20.1)
- [`WPO-Hydraulic-Utilities-1.20.1`](https://github.com/dev-willbird1936/WPO-Hydraulic-Utilities-1.20.1)

## Build

For local source builds, clone these repositories next to this one so the folder layout is:

```text
../SKDS-Core-1.20.1
../Water-Physics-Overhaul-1.20.1
../WPO-Environmental-Expansion-1.20.1
```

Typical local build:

```powershell
.\gradlew.bat build
```
