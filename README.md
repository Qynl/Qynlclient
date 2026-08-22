# QynlClient

**QynlClient** is a free, open-source **assistive Minecraft client** available for **Fabric 1.21.1** and **Legacy Fabric 1.8.9**.

It is made for players who find parts of the game hard to handle — people with disabilities, players who are new, or anyone who simply wants a more forgiving experience. It adds helpful in-game automation and accessibility features that do things for you (so you don't need fast clicks, precise timing, or quick reaction), plus an on-screen HUD to switch them on and off, an in-game settings screen to fine-tune each assist, and keybinds you can set — or remove — yourself.

The assist modules are deliberately tuned to look like the hand of a normal player: aim corrections snap to the mouse's own pixel grid, react with a human delay, wander slightly instead of locking perfectly, clicks come at irregular intervals, reach fluctuates a little, and knockback is softened by a percentage rather than removed. The goal is that server anti-cheat systems never mistake accessibility help for cheating.

It is **not** a cheat client: no combat hacks, no unfair advantages — just assistance that keeps the game honest while making it playable for everyone.

---

## Versions

| Version | Minecraft | Mod ID | Download |
|---------|-----------|--------|----------|
| **1.21.1** | 1.21.1 | `qynlclient` | `qynlclient-*.jar` |
| **1.8.9** | 1.8.9 | `qynlclient189` | `qynlclient-1.8.9-*.jar` |

The 1.8.9 version ships with **ViaFabric (ViaVersion) embedded** — you can join 1.9–1.21.x servers from your 1.8.9 client with no extra mods. VersionAssist is on by default and auto-detects the server version.

---

## QynlClient for Minecraft 1.21.1

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

**In-game:** Press **Right Shift** to open the QynlClient menu. Enable modules, set keybinds, and adjust settings. Everything is saved automatically.

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

#### GUI

| Module | Description |
|--------|-------------|
| **ClickGUI** | Opens the QynlClient menu (Right Shift) |

---

## QynlClient for Minecraft 1.8.9

### Requirements

- **Minecraft 1.8.9** (Java Edition)
- **Java 8** or newer
- **Fabric Loader** (Legacy Fabric) for 1.8.9

### How to install

1. Install [Legacy Fabric Loader](https://legacyfabric.net/) for Minecraft 1.8.9
2. Download the latest `qynlclient-1.8.9-*.jar` from [GitHub Releases](https://github.com/Qynl/Qynlclient/releases)
3. Place the jar into your `.minecraft/mods/` folder — **no other mods are needed** (ViaFabric is included)
4. Launch the fabric-loader-1.8.9 profile

**VersionAssist** is enabled by default — you can join servers from 1.9 through 1.21.x without any extra setup. The server version is auto-detected from the multiplayer server list. Use the built-in **VIA button** on the multiplayer screen for per-server version overrides.

**In-game:** Press **Right Shift** to open the QynlClient menu. Enable modules and adjust settings.

### Building from source

```sh
# Requires JDK 21 (Gradle toolchain handles Java 8 target)
cd 1.8.9
chmod +x gradlew
./gradlew build
# Jar output: 1.8.9/build/libs/qynlclient-1.8.9-*.jar
```

### 1.8.9 Modules

#### Assist

| Module | Description | Key |
|--------|-------------|-----|
| **AimAssist** | Gently guides aim toward nearest hostile while attacking | None |
| **AutoClicker** | Holds attack with humanly irregular timing (Rotations / Packets mode) | None |
| **BlockHit** | Rhythmically blocks between sword swings for damage reduction | None |
| **CritAssist** | Time your jumps so hits land as critical strikes (MiniJump / OnGround modes) | None |
| **FastPlace** | Shortens the local block-placement delay for held blocks (Legit / Fast / Instant modes); no camera, click automation, or packet spoofing | None |
| **FlyAssist** | Smooth flight without fly permission (Silent / Smooth / Vanilla modes) | K |
| **NinjaBridge** | Auto-sneak and auto-place while bridging across gaps | None |
| **QuantumSuperposition** | Latency prediction assist: models where the server thinks you are (A), where you are (B) and where you'll be (C), with adaptive lookahead that scales with speed/ping and collapses when you stop. Flushes your true position before attacks so hits register on high ping, and re-confirms your last stable position during packet-loss spikes (anti-rubberband). Optional **Quantum Echo** overlay renders the superposition in world space: fading position trail, cyan ghost at A, magenta ghost at C, and a pulsing red anchor ring during spikes | Z |
| **ReachAssist** | Extends your reach slightly with natural fluctuation | None |
| **StrafeAssist** | Auto-strafes left/right in combat | None |
| **TriggerBot** | Auto-attacks when your crosshair is on an enemy | None |
| **VelocityAssist** | Softens knockback by a percentage you choose | None |
| **VersionAssist** | Play on 1.9–1.21.x servers from 1.8.9 (ViaFabric embedded) | V |

#### Render

| Module | Description | Key |
|--------|-------------|-----|
| **Fullbright** | Boost brightness so you can see clearly in the dark | None |
| **NoHurtCam** | Removes damage camera tilt without changing damage or knockback | None |
| **NoViewBob** | Removes walking view bobbing without changing movement | None |
| **StreamerMode** | Hides sensitive info from the HUD for OBS/streaming | None |

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

## Keybinds

| Key | Function |
|-----|----------|
| **Right Shift** | Open the QynlClient menu |
| **Click a module row** | Toggle the module on/off |
| **Right-click a module** | Open detail panel with settings, toggle, and keybind editor |

All keybinds are saved to your config and can be changed from the **Keybinds…** screen in the QynlClient menu. Press **Esc** or **right-click** to clear a keybind.

## Project Structure

```
├── src/                  # 1.21.1 Fabric mod source
├── 1.8.9/src/            # 1.8.9 Legacy Fabric mod source
├── build.gradle          # 1.21.1 build script
├── 1.8.9/build.gradle    # 1.8.9 build script (embeds ViaFabric)
├── build/libs/           # 1.21.1 jar output
└── 1.8.9/build/libs/     # 1.8.9 jar output
```

---

## Support

Found a bug or have an idea? Open an issue at [github.com/Qynl/Qynlclient](https://github.com/Qynl/Qynlclient/issues).

QynlClient is MIT licensed — free forever, for everyone.
