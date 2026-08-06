# Qynl Performance+

The official texture pack for QynlClient — clean, flat, high-FPS textures for Minecraft **1.21.1**.

- **Lower fill rate** — flat surfaces with minimal noise render faster on weak GPUs.
- **Cleaner look** — smooth stone, sand, glass and metal textures with a consistent palette.
- **No animations** — every texture is static, saving CPU on water and fire.

## Install

1. Copy the `resourcepack` folder contents into a zip: `cd resourcepack && zip -r ../QynlPerformance+.zip .`
2. Drop `QynlPerformance+.zip` into your `.minecraft/resourcepacks/` folder.
3. Enable it in-game: Options → Resource Packs → **Qynl Performance+**.

## Regenerate

Textures are generated from `scripts/gen-textures.mjs`:

```sh
bun scripts/gen-textures.mjs
```
