# Qyn-L Clean (BedWars PvP)

A clean, **Bare-Bones-style** resource pack for **Minecraft 1.8.9**, tuned for BedWars / PvP
and matching the Qyn-L client aesthetic. Every texture is generated from the vanilla 1.8.9
jar by `scripts/gen-qynl-pack.mjs` — no pixels are copied from any other resource pack.

## What it changes
- **Blocks** — every solid block flattened to clean, flat colors (grass, stone, cobblestone,
  bricks, planks, logs, ores, metal blocks, prismarine, sea lantern, slime, sponge, beds, TNT …)
- **Wool (16)** — flat with a subtle weave so bed layers / defenses read at a distance
- **Glass + panes (17)** — near-invisible with a bright 1px border (PvP standard)
- **Stained clay / terracotta (16)** — clean flat colors
- **Items** — hand-drawn clean tools (sword/pickaxe/axe/shovel/hoe in 5 tiers), bow (4 states),
  arrow, gems, ingots, food, materials, buckets, dyes
- **GUI** — dark clean containers (inventory, chest, crafting, furnace, …), hotbar/widgets,
  icons, menus
- **Environment** — clean sun, moon phases, clouds
- Everything else falls back to vanilla 1.8.9 textures.

## Install
Copy the `QynL-BedWars` folder into `.minecraft/resourcepacks/` (or your launcher's
resource-pack folder) and enable it in Options → Resource Packs.

## Regenerate
```bash
bun scripts/gen-qynl-pack.mjs [path/to/1.8.9.jar]
```
(Downloads the vanilla 1.8.9 client jar automatically if no path is given.)
