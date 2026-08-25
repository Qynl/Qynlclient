# Qyn-L

**Qyn-L** is a Minecraft client available for **Fabric 1.21.1** and **Legacy Fabric 1.8.9**.

- **Qyn-L 1.8.9** — a clean, silent **ghost client** in the style of Vape Lite: a small set of high-quality modules tuned to look like a normal player. A flagship quantum-strike engine (**Qynl**), an AI **Director** that decides which combat modules may act and how hard, aim assistance, reach, velocity, auto-clicker, WTap, sprint, an evasion engine (**Aegis**), render helpers (Search, NameTags, Tracers, StorageESP, Echo, Fullbright), utility (Scaffold, ChestSteal, Blink, Refill, Throwpot, Clutch), a Text GUI and friends. The UI is a minimal, dark, Vape-style ClickGUI.
- **Qyn-L 1.21.1** — the same ghost client, ported: the identical 32-module set (Qynl quantum engine, Director AI, AimAssist, AutoClicker with Block Hit, Reach with Silent Pack-Choke, Velocity, WTap, BlockHit, Criticals, Aegis, StrafeAssist, Hindsight, Sprint, Blink, Clutch, Refill, Throwpot, VersionAssist, Text GUI, render helpers) with a Vape-style HUD.

All modules are deliberately tuned to mimic a real player: aim corrections snap to the mouse's own pixel grid and react with a human delay, clicks come at irregular intervals, reach fluctuates inside the ghost range, knockback is softened by a percentage rather than removed. No module is immune to aggressive anti-cheat — keep the flashier assists (Scaffold, Blink, the Reach pack-choke) on servers that tolerate them.

---

## Versions

| Version | Minecraft | Mod ID | Download |
|---------|-----------|--------|----------|
| **1.21.1** | 1.21.1 | `qynlclient` | `qynlclient-2.1.0.jar` |
| **1.8.9** | 1.8.9 | `qynlclient189` | `qynlclient-1.8.9-*.jar` |

The 1.8.9 jar ships with **ViaFabric (ViaVersion) embedded** — you can join 1.9–1.21.x servers from your 1.8.9 client with no extra mods.

---

## Qyn-L Clean BedWars texture packs

The repository includes two original, version-specific BedWars/PvP packs under `texturepacks/`:

| Pack | Minecraft | Focus |
|------|-----------|-------|
| `QynL-Clean-BedWars-1.8.9` | 1.8.9 | High-detail clean legacy layout, readable team wool, low-clutter glass, custom tools and dark GUI |
| `QynL-Clean-BedWars-1.21.1` | 1.21.1 | Modern `block/`, `item/`, GUI-sprite and bed-entity paths with the same clean visual language |

Both packs use original generated overlays and keep vanilla fallback assets where no BedWars-specific redesign is needed. They are not copies of Bare Bones or any other third-party pack. Install the folder matching your Minecraft version in `.minecraft/resourcepacks/` and enable it in the Resource Packs menu.

Regenerate both packs from the official client jars with:

```sh
bun scripts/gen-qynl-packs.mjs
```

The generator downloads the official jars into `.cache/` when they are not already available, preserves version-correct animation metadata, and writes pack formats 1 and 34 for 1.8.9 and 1.21.1 respectively.

---

## Qyn-L for Minecraft 1.21.1

### Requirements

- **Minecraft 1.21.1** (Java Edition)
- **Java 21** or newer
- **Fabric Loader** for 1.21.1
- **Fabric API** for 1.21.1

### How to install

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft 1.21.1
2. Download the latest `qynlclient-*.jar` from [GitHub Releases](https://github.com/Qynl/Qynlclient/releases)
3. Download [Fabric API](https://modrinth.com/mod/fabric-api) for 1.21.1
4. Place both jar files into your `.minecraft/mods/` folder
5. Launch the `fabric-loader-1.21.1` profile

**In-game:** Press **Right Shift** to open the Qyn-L menu. Enable modules, set keybinds, and adjust settings. Everything is saved automatically.

### Building from source

```sh
# Requires JDK 21
./gradlew build
# Jar output: build/libs/qynlclient-*.jar
```

### 1.21.1 Modules

Exact 1.8.9 parity — the same 32 modules, ported to the 1.21.1 API.

#### Combat

| Module | Description |
|--------|-------------|
| **AimAssist** | Smoothly aims at nearby targets — Rotations / LockView / Silent modes, cubic ease-out glide, human convergence (residual wobble, never perfect lock), GCD snap, hard rotation-speed cap, reaction delay, priority modes. Never targets friends |
| **AutoClicker** | Clicks while you hold attack — human timing variance, burst pattern, random start reaction, CPS random walk, occasional missed clicks, and **Block Hit** (re-blocks after every swing when an enemy is in range, randomized hold, sprint-reset) |
| **ReachAssist** | Extends reach with natural fluctuation locked to the ghost range (3.01–3.35 blocks per mode) plus a real **Silent Pack-Choke** — holds movement packets 1–2 ticks while closing on a target (never mid-air, never while sprinting, ping-gated) |
| **VelocityAssist** | Reduces knockback by a percentage with per-hit variance and a **per-hit chance** (default 75 %) — some hits take full knockback. Mixin dampening plus a per-tick nth-root fallback, never applied twice |
| **AutoSprint** | Sprints automatically while you move — vanilla rules plus randomized start delay, a post-attack re-engage window, and occasional missed starts |
| **WTap** | Taps W after each hit to reset sprint — extra knockback on enemies |
| **BlockHit** | Vape-Lite-grade auto-blocking: **Reactive** (blocks when an enemy swings) or **Rhythm** (re-blocks after your own swings, attacks land unblocked). Randomized hold/chance/cooldown |
| **Hindsight** | **Server-Time Replay** — replays the past, which is what the server actually checks: shows your own server-side position and every enemy's server-side hitbox, clicks exactly when the server's rewind-reach check passes. Works on retreating targets too. Defers to Qynl when both are on |
| **Qynl** | The flagship engine — **Quantum Superposition**. Rebuilds every enemy's server-side position by rewinding the packet history one ping (measured from the real keep-alive RTT) and projects where they'll be when your attack lands; clicks at the earliest crossing into reach. Silently aims the server at the server-side hitbox, and collapses into a 1–2 tick dodge strafe the instant an enemy swings at you. Every packet is an ordinary vanilla attack |
| **Criticals** | The Airborne Engine — attacks only inside vanilla airtime: your own jumps, knockback arcs and edge falls. Zero modified packets |
| **Aegis** | The Evasion Engine — integrates every inbound projectile's trajectory and sidesteps out of the way with pure vanilla input. Weighted-vector dodge, void guard, randomized reaction window |
| **StrafeAssist** | Auto-strafes left/right in combat with randomized intervals — never strafes while idle, keeps sprint alive |
| **Director** | The combat AI brain — watches the fight and decides, tick by tick, which combat modules may act and how hard, switching between 7 tactics (Engage / Combo / Trade / Defend / Evade / Retreat / Survive) with humanized reaction delays, parameter re-tuning and an aggression slider. Never starts a fight by itself (engagement gate: crosshair on target + attack held), never runs anything constantly, sends zero packets. Force-enables Clutch the instant a fall turns lethal |

#### Render

| Module | Description |
|--------|-------------|
| **Search** | Outlines chests, ores or storage blocks through walls (cached scan) |
| **NameTags** | Renders entity nametags through walls — friends green, enemies red |
| **Tracers** | Draws a line to every entity in range (players red, mobs orange, friends green) |
| **StorageESP** | Outlines all storage blocks through walls |
| **Fullbright** | Makes everything bright |
| **NoHurtCam** | Removes the damage camera tilt — damage and knockback untouched (render-only, silent) |
| **NoViewBob** | Removes walking view bobbing — movement untouched (render-only, silent) |
| **Zoom** | Hold the key to zoom in smoothly (2×–6× adjustable) |
| **Echo** | Soundscape radar — listens to the server's own sound packets (read-only) and draws fading 3D markers. Zero packets sent |

#### Utility

| Module | Description |
|--------|-------------|
| **Clutch** | Auto-Save Engine — latches a lethal fall, edge-sneaks at the very last moment, then MLG water/lava with ping-adaptive placement. The Director force-enables it when a fall turns lethal |
| **ScaffoldWalk** | Places a block under your feet while walking — bridge gaps without aiming down |
| **ChestStealer** | Takes everything from an opened chest, one shift-click at a time |
| **Blink** | Holds your movement packets briefly (Auto / Hold) — breaks the opponent's combo. Hardened: burst fake lag, holds only on the ground while moving |
| **Refill** | Refills your hotbar with food and potions from the inventory |
| **Throwpot** | Press the bind to throw / drink / eat your best healing item |
| **VersionAssist** | Play on 1.21.2+ servers from 1.21.1 — ViaFabric translates automatically; pick a target version or Auto — key V |

#### Other

| Module | Description |
|--------|-------------|
| **Text GUI** | Shows the enabled modules on the HUD (position + color options) |
| **Friends** | Comma-separated names that are never targeted and render green |
| **StreamerMode** | Hides the HUD from OBS/recordings — assists keep working silently |

---

## Qyn-L for Minecraft 1.8.9

The 1.8.9 client is the ghost client: a small, focused set of silent modules (Vape-Lite style) and a minimal dark ClickGUI. Everything is saved automatically.

The brain of the kit is the **Director** — a combat AI that watches the fight and only ever enables modules whose behavior is human-plausible in the current situation, switching tactics (engage / combo / trade / defend / evade / retreat / survive) with humanized reaction delays. Nothing runs constantly, and the client stays "asleep" (Idle) until you actually open a fight yourself.

### Requirements

- **Minecraft 1.8.9** (Java Edition)
- **Java 8** or newer
- **Fabric Loader** (Legacy Fabric) for 1.8.9

### How to install

1. Install [Legacy Fabric Loader](https://legacyfabric.net/) for Minecraft 1.8.9
2. Download the latest `qynlclient-1.8.9-*.jar` from [GitHub Releases](https://github.com/Qynl/Qynlclient/releases)
3. Place the jar into your `.minecraft/mods/` folder — **no other mods are needed** (ViaFabric is included)
4. Launch the fabric-loader-1.8.9 profile

**In-game:** Press **Right Shift** to open the Qyn-L ClickGUI. Left-click a module to toggle it, right-click to open its settings (keybind + options). The Text GUI shows enabled modules on the HUD.

### Building from source

```sh
# Requires JDK 21 (Gradle toolchain handles Java 8 target)
cd 1.8.9
chmod +x gradlew
./gradlew build
# Jar output: 1.8.9/build/libs/qynlclient-1.8.9-*.jar
```

### 1.8.9 Modules

#### Combat

| Module | Description |
|--------|-------------|
| **Director** | The combat AI brain — watches the fight and decides, tick by tick, which combat modules may act and how hard, switching between 7 tactics (Engage / Combo / Trade / Defend / Evade / Retreat / Survive) with humanized reaction delays, parameter re-tuning and an aggression slider. Never starts a fight by itself (engagement gate: crosshair on target + attack held), never runs anything constantly, sends zero packets. Force-enables Clutch the instant a fall turns lethal |
| **Qynl** | The flagship engine — **Quantum Superposition**. Rebuilds every enemy's server-side position by rewinding the packet history one ping (the same rewind the server's hit test uses) and projects where they'll be when your attack lands; it clicks at the earliest crossing into reach — for approaching **and** retreating targets. Silently aims the server at the server-side hitbox, and collapses into a 1–2 tick dodge strafe the instant an enemy swings at you. Every packet is an ordinary vanilla attack |
| **AimAssist** | Smoothly aims at nearby targets with humanized rotation or silent (packet) aiming; never targets friends. Cubic ease-out glide (accelerates out of the turn, eases onto the target), human convergence (damped settling + residual wobble, never perfect lock) and a hard rotation-speed cap |
| **AutoClicker** | Clicks while you hold attack — human timing variance, burst pattern, random start reaction, slow CPS random walk, micro-pauses while adjusting aim, occasional missed clicks, and a **Block Hit** mode: re-blocks after every swing only when an enemy is in range, randomized hold, sprint-reset |
| **Reach** | Extends reach with natural fluctuation locked to the ghost range (3.01–3.35 blocks per mode) plus an optional Silent Pack-Choke — only chokes on the ground while closing on a target (never mid-air, never while sprinting) |
| **Velocity** | Reduces knockback by a percentage with per-hit variance and a **per-hit chance** (default 75%) — some hits take full knockback, exactly like real connection jitter. Softer defaults (45 % / 20 %) |
| **Sprint** | Sprints automatically while you move — vanilla rules plus randomized start delay, a post-attack re-engage window, and occasional missed starts so the pattern matches a human |
| **WTap** | Taps W after each hit to reset sprint — extra knockback on the enemy. Only taps while actually sprinting, with a chance setting and ±1 tick humanized delay |
| **BlockHit** | Vape-Lite-grade auto-blocking: **Reactive** blocks when an enemy swings at you (human reaction delay), **Rhythm** re-blocks after your own swings with attacks landing unblocked (post-attack blocking). Randomized hold/chance/cooldown, sprint-reset before every block, sword/axe only — never blocks at air, never touches manual item use |
| **Criticals** | The Airborne Engine — attacks only inside vanilla airtime: your own jumps, knockback arcs (turning a combo against you into a chain of crits) and edge falls. Predicts the jump so the fall window is open when an enemy steps into reach (humanized chance + cooldown, solid ground only, approach-only), and clicks mid-air enemies for full-knockback combos. Zero modified packets |
| **Hindsight** | **Server-Time Replay** — the flip side of Qynl: instead of projecting the future, it replays the past, which is what the server actually checks. Shows your own **server-side position** (cyan box — on high ping it can be a block from your camera) and every enemy's server-side hitbox with lines, then clicks exactly when the server's own rewind-reach check will pass — works on **retreating** targets too, where forward prediction fails. Defers to Qynl when both are on. Zero modified packets, zero flags |
| **Aegis** | The Evasion Engine — integrates every projectile's trajectory (arrows, snowballs, splash pots, pearls) against your own motion and sidesteps out of the way with pure vanilla input. The dodge direction is the **weighted vector sum of all inbound projectiles** — one decisive strafe even under snowball/arrow spam, never a nervous left-right flicker. Void guard, jump dodge, randomized reaction window. Arrows physically cannot hit you |
| **StrafeAssist** | Auto-strafes left/right in combat — randomized interval (±30 %), 10 % hold-skip, never strafes while idle; keeps sprint alive while strafing — key Y |

#### Render

| Module | Description |
|--------|-------------|
| **Search** | Outlines chests, ores or storage blocks through walls (cached scan) |
| **NameTags** | Renders entity nametags through walls — friends green, enemies red |
| **Tracers** | Draws a line to every entity in range (players red, mobs orange, friends green) |
| **StorageESP** | Outlines all storage blocks through walls |
| **Echo** | Soundscape radar — listens to the server's own sound packets (read-only) and draws fading 3D markers: red player activity, magenta ender-pearl landings (revealed through walls), gold mobs, green utility, grey ambient. Zero packets sent — unflagable by construction |
| **Fullbright** | Makes everything bright |
| **NoHurtCam** | Removes the damage camera tilt — damage and knockback untouched (render-only, silent) |
| **NoViewBob** | Removes walking view bobbing — movement and camera control untouched (render-only, silent) |
| **Zoom** | Hold the key to zoom in smoothly (2×–6× adjustable, eased FOV + sensitivity) — key C |

#### Utility

| Module | Description |
|--------|-------------|
| **Scaffold** | Places a block under your feet while walking — bridge gaps without aiming down. Humanized placement: varying hit vectors, randomized delays, **edge sneak** like a real bridger, and a one-tick **rotation spoof** (1.8.9 placement packets carry no rotation, so ACs infer your look from movement packets — the spoof makes the server see you looking down at the block) |
| **ChestSteal** | Takes everything from an opened chest, one shift-click at a time |
| **Blink** | Holds your movement packets briefly (Auto / Hold) — breaks the opponent's combo. Hardened: **burst fake lag** (randomized hold + 12–24 tick gaps, never a constant rate) and holds only on the ground while moving |
| **Clutch** | Auto-Save Engine — latches a lethal fall, edge-sneaks at the very last moment, then MLG water/lava with ping-adaptive placement and an eased camera flick. The server sees a panicked-but-successful player, never a script. The Director force-enables it when a fall turns lethal |
| **Refill** | Refills your hotbar with food and potions from the inventory |
| **Throwpot** | Press the bind to throw / drink / eat your best healing item |
| **VersionAssist** | Play on 1.9–1.21 servers from 1.8.9 — embedded ViaVersion translates automatically; pick a target version or Auto — key V |

#### Other

| Module | Description |
|--------|-------------|
| **Text GUI** | Shows the enabled modules on the HUD (position + color options) |
| **Friends** | Comma-separated names that are never targeted and render green |
| **StreamerMode** | Hides the HUD from OBS/recordings — assists keep working silently — key F8 |

---

## Keybinds

| Key | Action |
|-----|--------|
| **Right Shift** | Open the Qyn-L menu |

Per-module keybinds are set from the module's settings panel in the ClickGUI (right-click a module → click "Bind"). Press **Esc** to clear a bind. All keybinds and settings are saved to your config automatically.
