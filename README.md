# Qyn-L

**Qyn-L** is a Minecraft client available for **Fabric 1.21.1** and **Legacy Fabric 1.8.9**.

- **Qyn-L 1.8.9** — a clean, silent **ghost client** in the style of Vape Lite: a small set of high-quality modules tuned to look like a normal player. A flagship quantum-strike engine (**Qynl**), an AI **Director** that decides which combat modules may act and how hard, aim assistance, reach, velocity, auto-clicker, WTap, sprint, an evasion engine (**Aegis**), render helpers (Search, NameTags, Tracers, StorageESP, Echo, Fullbright), utility (Scaffold, ChestSteal, Blink, Refill, Throwpot, Clutch), a Text GUI and friends. The UI is a minimal, dark, Vape-style ClickGUI.
- **Qyn-L 1.21.1** — an assistive client with a broader set of convenience modules for easier play.

All modules are deliberately tuned to mimic a real player: aim corrections snap to the mouse's own pixel grid and react with a human delay, clicks come at irregular intervals, reach fluctuates inside the ghost range, knockback is softened by a percentage rather than removed. No module is immune to aggressive anti-cheat — keep the flashier assists (Scaffold, Blink, the Reach pack-choke) on servers that tolerate them.

---

## Versions

| Version | Minecraft | Mod ID | Download |
|---------|-----------|--------|----------|
| **1.21.1** | 1.21.1 | `qynlclient` | `qynlclient-*.jar` |
| **1.8.9** | 1.8.9 | `qynlclient189` | `qynlclient-1.8.9-*.jar` |

The 1.8.9 jar ships with **ViaFabric (ViaVersion) embedded** — you can join 1.9–1.21.x servers from your 1.8.9 client with no extra mods.

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

#### Assist

| Module | Description | Key |
|--------|-------------|-----|
| **AimAssist** | Gently guides aim toward nearest hostile while attacking | None |
| **AntiAFK** | Turns your view gently so servers don't kick you for being idle | None |
| **AntiDrown** | Swims up when you're about to drown | None |
| **AutoArmor** | Equips the best armor from your inventory automatically | J |
| **AutoClicker** | Holds attack with humanly irregular timing | None |
| **AutoClimb** | Climbs ladders and vines automatically | None |
| **AutoEat** | Eats the best food in your hotbar when hungry | None |
| **AutoFish** | Reels in fish when they bite, then re-casts for you | None |
| **AutoJump** | Automatically hops over small gaps and up stairs | N |
| **AutoMine** | Keeps mining a block until it breaks — you don't have to hold the button | H |
| **AutoPotion** | Splashes healing potions automatically when health is low | None |
| **AutoRespawn** | Clicks the respawn button for you when you die | None |
| **AutoSprint** | Sprint stays on while you move forward | Y |
| **AutoStep** | Steps up one-block-high edges without jumping | None |
| **AutoSwim** | Swims up automatically when underwater | None |
| **AutoSword** | Switches to your strongest weapon when you attack | None |
| **AutoTool** | Switches to the right tool for the block you're looking at | None |
| **AutoTorch** | Places torches from your hotbar when light is too low | None |
| **AutoTotem** | Moves a Totem of Undying to your offhand when health is low | None |
| **AutoWalk** | Walks forward — free your hands while travelling | B |
| **BowAssist** | Draws and releases your bow/crossbow at full charge | None |
| **ChestStealer** | Open a chest and it automatically takes everything | T |
| **CritAssist** | Time your jumps so hits land as critical strikes (MiniJump / OnGround modes) | None |
| **FastPlaceAssist** | Speeds up block placement | None |
| **FlyAssist** | Smooth flight without fly permission (Silent / Smooth / Vanilla modes) | G |
| **InvWalk** | Walk while your inventory or any menu is open | P |
| **NinjaBridge** | Auto-sneak and auto-place while bridging across gaps | None |
| **NoFall** | Prevents fall damage — for players with depth-perception issues | None |
| **ReachAssist** | Extends your reach slightly with natural fluctuation | None |
| **SafeWalk** | Stops you from walking off edges | None |
| **ScaffoldWalk** | Places blocks under you while you walk (sneak to use) | None |
| **ShieldAssist** | Auto-blocks when an enemy attacks (BlockHit / Hold modes) | None |
| **StreamerMode** | Hides sensitive info from the HUD for OBS/streaming | None |
| **ToggleSneak** | Sneak stays on until you press the key again | V |
| **VelocityAssist** | Softens knockback by a percentage you choose | None |

#### Render

| Module | Description | Key |
|--------|-------------|-----|
| **Fullbright** | Boost brightness so you can see clearly in the dark | K |
| **Zoom** | Hold the key to zoom in for a closer look | Z |

#### Info

| Module | Description |
|--------|-------------|
| **CoordConvert** | Shows Nether ↔ Overworld coordinate conversions |
| **DeathCoords** | Remembers where you died and shows it on HUD |
| **DurabilityWarn** | Shows a warning when your tools are about to break |
| **EffectTimers** | Shows how much time is left on your active effects |
| **InfoHUD** | Shows coordinates, FPS, and other info |
| **Keystrokes** | On-screen WASD / mouse / CPS display |
| **TargetInfo** | Shows info about the entity you're looking at |

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
| **Zoom** | Hold the key to zoom in smoothly (2×–6× adjustable, eased FOV + sensitivity) — key F7 |

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
