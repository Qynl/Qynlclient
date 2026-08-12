# QynlClient

**QynlClient** is a free, open-source **assistive Minecraft client** for Fabric 1.21.1 and Legacy Fabric 1.8.9.

## Agent Runtime v3

QynlClient v3 turns the adapter into a practical **local Minecraft gameplay interface** for private/single-player worlds. An external agent can observe Minecraft, receive screen frames, read structured world state, and execute core gameplay actions.

### Perception

- Screen capture as PNG for a vision model
- Player position, yaw/pitch, health and hunger
- On-ground, sprinting and sneaking state
- Dimension, time, rain and thunder
- Nearby entities with type, name, distance, position and health
- Current targeted entity
- Inventory item totals

### Gameplay actions

`AgentRuntime` now exposes:

- `move(...)` for W/A/S/D, jump, sneak and sprint
- `look(yawDelta, pitchDelta)` with safe pitch limits
- `attack()` against the currently targeted entity
- `use()` with the main hand
- `stop()` to release all movement input
- `snapshot()` for structured world state
- `captureScreen()` for vision input

These are Minecraft-side actions, not simulated responses. They drive the actual client input/action APIs.

### Safety boundary

Agent gameplay input is **single-player only**. The input gate requires an active player/world and `MinecraftClient.isInSingleplayer()`. Multiplayer input is rejected. This keeps the feature suitable for local AI gameplay experiments rather than making it a multiplayer automation client.

## Architecture

```text
Vision / AI model
       |
       v
  AgentRuntime
   /    |     \
  v     v      v
Screen  State  Actions
Vision  Adapter  |
        |        +--> Movement
        |        +--> Look
        |        +--> Attack
        |        +--> Use
        v
   Minecraft client
```

The mod does not contain a model or network server. A separate local agent can provide the AI/planner and choose a transport while QynlClient handles Minecraft perception and actions.

## Status: v3

**v3 gameplay adapter: implemented.** The Minecraft side now has the core perception + action surface required for an agent to actually play a single-player world. A future protocol layer can add external transport, action batching, observation-rate control and model integration.

## Versions

| Version | Minecraft | Mod ID |
|---|---|---|
| **1.21.1** | 1.21.1 | `qynlclient` |
| **1.8.9** | 1.8.9 | `qynlclient189` |

The 1.8.9 version retains its embedded ViaFabric/ViaVersion setup.

## QynlClient for Minecraft 1.21.1

### Requirements

- Minecraft 1.21.1 (Java Edition)
- Java 21 or newer
- Fabric Loader for 1.21.1
- Fabric API for 1.21.1

### Install

1. Install Fabric Loader for Minecraft 1.21.1.
2. Download the latest QynlClient jar from GitHub Releases.
3. Install Fabric API for 1.21.1.
4. Put both jars into `.minecraft/mods/`.
5. Launch the Fabric 1.21.1 profile.

Press **Right Shift** to open the QynlClient menu.

### Build

```sh
./gradlew build
```

The 1.21.1 jar is written to `build/libs/`.

## Existing assist modules

QynlClient retains its accessibility and assist modules, including AutoArmor, AutoEat, AutoMine, AutoTool, AutoWalk, AutoJump, SafeWalk, AutoSprint, Fullbright, Zoom, InfoHUD, Keystrokes and other assistive features.

## Project Structure

```text
├── src/main/java/com/qynl/client/
│   ├── agent/
│   │   ├── AgentActions.java
│   │   ├── AgentInput.java
│   │   ├── AgentRuntime.java
│   │   ├── AgentState.java
│   │   ├── MinecraftAgentAdapter.java
│   │   └── ScreenVisionAdapter.java
│   ├── hud/
│   ├── mixin/
│   ├── module/
│   └── util/
├── 1.8.9/src/
├── build.gradle
└── 1.8.9/build.gradle
```

## License

MIT
