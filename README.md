# QynlClient

**QynlClient** is a free, open-source **assistive Minecraft client** for Fabric **1.21.1**.

It is made for players who find parts of the game hard to handle — people with disabilities, players who are new, or anyone who simply wants a more forgiving experience. It adds helpful in-game automation and accessibility features that do things for you (so you don't need fast clicks, precise timing, or quick reaction), plus an on-screen HUD to switch them on and off, an in-game settings screen to fine-tune each assist, and keybinds you can set — or remove — yourself. Recent assists keep a Totem of Undying ready in your offhand, put your strongest weapon in your hand when you attack, and soften knockback — small things that make a big difference.

The assist modules are deliberately tuned to look like the hand of a normal player: aim corrections snap to the mouse's own pixel grid, react with a human delay, wander slightly instead of locking perfectly, clicks come at irregular intervals, reach fluctuates a little, and knockback is softened by a percentage rather than removed. The goal is that server anti-cheat systems never mistake accessibility help for cheating. You can also switch AimAssist and AutoClicker between **Rotations** (the view moves) and **Packets** (only the server sees the aimed direction, your camera stays where you point it) to find what works best for you.

It is **not** a cheat client: no combat hacks, no unfair advantages — just assistance that keeps the game honest while making it playable for everyone.

---

## Requirements

- **Minecraft 1.21.1** (Java Edition)
- **Java 21** or newer
- **Fabric Loader** for 1.21.1
- **Fabric API** for 1.21.1

---

## How to install

### 1. Install Fabric Loader

1. Download the [Fabric installer](https://fabricmc.net/use/installer/).
2. Run it and choose **Minecraft version: 1.21.1**, **Loader version: latest**, **Game version: latest**.
3. Select **Client** and click **Install**.
4. Open the Minecraft launcher and launch the new **fabric-loader-1.21.1** profile once so the game creates its folders.

### 2. Get the mod files

You need **two** files:

| File | Where to get it |
|---|---|
| `qynlclient-*.jar` | [GitHub Releases](https://github.com/Qynl/Qynlclient/releases) (the latest `v*` release) |
| `fabric-api-*.jar` (1.21.1) | [Modrinth](https://modrinth.com/mod/fabric-api) or [CurseForge](https://www.curseforge.com/minecraft/mc-mods/fabric-api) |

### 3. Put them in the mods folder

1. Press **Win + R** (Windows) and run `%appdata%\.minecraft`, or open `~/Library/Application Support/minecraft` on macOS.
2. Open the **`mods`** folder (create it if it doesn't exist).
3. Copy **both** jar files into it.
4. *(Optional)* Also grab **Qynl Performance+** (a small zip, also attached to the release) and drop it into the **`resourcepacks`** folder, then enable it in-game under **Options → Resource Packs** for smoother performance.

### 4. Play

1. Start the game with the **fabric-loader-1.21.1** profile.
2. In-game, press **Right Shift** to open the QynlClient menu.
3. Turn on whatever assistance you want — it's saved automatically.
4. Want a different key for something? Open **Keybinds…** in the QynlClient menu and press any key (press **Esc** or **right-click** to leave a module without a key). F-keys and number keys are kept for the game, so they can't be stolen by accident.
5. Open **Settings…** to switch assists between **Rotations** and **Packets** mode, and to fine-tune things like click speed and knockback reduction.

---

## Building from source

Requires **JDK 21**.

```sh
./gradlew build
```

The mod jar will be in `build/libs/`.

---

## Support

Found a bug or have an idea? Open an issue at [github.com/Qynl/Qynlclient](https://github.com/Qynl/Qynlclient/issues).

QynlClient is MIT licensed — free forever, for everyone.
