package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import com.qynl.client189.PingTracker;
import com.qynl.client189.Setting;
import com.qynl.client189.WorldDraw;
import com.qynl.client189.mixin.KeyBindingAccessor;
import net.minecraft.block.BlockState;
import net.minecraft.block.Material;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.input.Keyboard;

import java.util.Random;

/**
 * CLUTCH — the auto-save engine.
 *
 * <p>Not a combat module and not a perception module: it is a physical
 * rescue reflex. It watches your fall and the block column below you, and
 * when a fall becomes lethal it does exactly what a top player would —
 * only with perfect timing and zero wasted ticks:</p>
 * <ul>
 *   <li><b>Edge save</b> — before you run off a cliff into the void, lava
 *       or a lethal drop, the module presses sneak for you (vanilla key
 *       input, nothing sent) so you stop at the edge. The void cannot be
 *       MLG'd with a bucket in 1.8 (the raycast simply misses), so this
 *       prevention is the real void defense.</li>
 *   <li><b>MLG</b> — falling onto ground that would deal fall damage: the
 *       module flicks your pitch down (a human flick, 1–2 ticks), picks the
 *       water bucket from the hotbar, and right-clicks at the exact moment
 *       the ground is in reach — a perfectly placed water pool at your
 *       feet.</li>
 *   <li><b>Lava clutch</b> — falling toward lava: same water placement. The
 *       water converts the lava column under you to obsidian/cobble and you
 *       land on the platform instead of the lake.</li>
 * </ul>
 *
 * <p>Everything is a vanilla action: one key press, one hotbar swap, one
 * right-click, one swap back. Zero packets are fabricated — the server
 * performs its own placement from the rotation we send, exactly like a
 * human MLG. The flick is humanized (reaction beat, eased rotation, delayed
 * swap-back, cooldown, chance), so there is no perfect-reaction signature
 * for statistical anti-cheats to fingerprint.</p>
 *
 * <p>Honest limits: a bucket cannot save you from a pure void fall in 1.8
 * (nothing to place against) — that is exactly why the edge save exists.
 * Deep lava lakes (&gt; ~2.5 blocks) are a best-effort save: the raycast
 * places at the lake floor, so only the shallow lakes convert in time.</p>
 */
public class ClutchModule extends Module {
    private static ClutchModule instance;
    private static final Random RANDOM = new Random();

    // ── state machine ───────────────────────────────────────────
    private static final int IDLE = 0;
    private static final int ROTATE = 1;   // flicking pitch down + holding it
    private static final int PLACE = 2;    // the right-click
    private static final int RECOVER = 3;  // swap back, look back up

    private int phase = IDLE;
    private int phaseTicks = 0;
    private int cooldownTicks = 0;

    // ── recovery state ──────────────────────────────────────────
    private int prevSlot = -1;
    private int bucketSlot = -1;
    private float savedPitch = 0.0F;
    private boolean rotated = false;
    private boolean placed = false;

    // ── fall analysis ───────────────────────────────────────────
    private boolean inDanger = false;
    private boolean lavaBelow = false;
    private boolean voidBelow = false;
    /** Latch: the current airborne fall was dangerous at some point (the
     *  ground gets closer as we fall, so danger must be decided once, at the
     *  top of the fall — not when the MLG window is already open). */
    private boolean fallLatched = false;
    private double solidTop = Double.MAX_VALUE; // first solid under lava/air
    private double surfaceY = 0.0;              // lava surface or ground top
    private double surfaceDist = 0.0;           // feet -> surface
    private boolean markerVisible = false;
    private double markerX, markerY, markerZ;
    private int waitTicks = 0;                  // failsafe: window never opened

    // ── edge prevention ─────────────────────────────────────────
    private boolean edgeDanger = false;
    private boolean sneakHeld = false;
    private double edgeX, edgeY, edgeZ;

    public ClutchModule() {
        super("Clutch",
                "Auto-save engine: catches you before you die to the void, lava or fall damage. Edge save (auto-sneak at lethal cliffs), MLG (water bucket at the perfect tick) and lava clutch (water converts the lake under you to a platform) — all vanilla actions, humanized timing, zero fabricated packets.",
                Category.UTILITY);
        instance = this;
        bindKey(Keyboard.KEY_F6);
        addSetting(Setting.options("mode",    "Mode",    "Save",  "Save", "Fall", "Lava"));
        addSetting(Setting.options("edge",    "Edge save", "On",  "On",   "Off"));
        addSetting(Setting.range("fallH",     "Fall height", 4.0, 2.0, 8.0, 0.5, "b"));
        addSetting(Setting.range("range",     "Place range", 3.6, 2.0, 4.4, 0.1, "b"));
        addSetting(Setting.range("chance",    "Chance",   100.0, 50, 100, 5, "%"));
        addSetting(Setting.options("humanize", "Humanize", "On",  "On",  "Off"));
        addSetting(Setting.options("render",   "Render",   "On",  "On",  "Off"));
    }

    // ── static state for the combat Director ─────────────────────

    /** True while the clutch state machine is mid-save (rotating, placing
     *  or recovering). The Director reads this to shut combat down — you
     *  cannot fight while you are saving yourself, and block-hitting or
     *  strafing in that window would move you off the water column. */
    public static boolean isSaving() {
        return instance != null && instance.isEnabled() && instance.phase != IDLE;
    }

    /** True while a lethal fall is latched or an edge is dangerous right now.
     *  The Director uses this to pre-empt its own tactics and force the
     *  survival posture before the fall even resolves.
     *
     *  <p>The analysis also runs while the module is <b>disabled</b>: the
     *  fall scan is a pure sensor (reads blocks, writes a few fields, presses
     *  nothing), so the Director can detect a lethal fall and force the
     *  module on before anything else acts — without the module needing to
     *  be enabled beforehand.</p>
     */
    public static boolean isInDanger() {
        if (instance == null) return false;
        if (!instance.isEnabled()) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.world == null) return false;
            instance.analyzeFall(client);
            instance.computeEdgeDanger(client);
        }
        return instance.inDanger || instance.edgeDanger;
    }

    // ── lifecycle ───────────────────────────────────────────────

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            resetAll(client);
            return;
        }
        // Creative/spectator take no fall damage and need no saving.
        if (client.player.abilities.creativeMode || client.player.isSpectator()) {
            resetAll(client);
            return;
        }
        if (cooldownTicks > 0) cooldownTicks--;

        analyzeFall(client);
        tickEdge(client);
        tickClutch(client);
    }

    @Override
    public void onDisable() {
        MinecraftClient client = MinecraftClient.getInstance();
        resetAll(client);
    }

    private void resetAll(MinecraftClient client) {
        abort(client);
        if (sneakHeld && client != null && client.options != null) {
            ((KeyBindingAccessor) client.options.keySneak).setPressed(false);
            sneakHeld = false;
        }
        edgeDanger = false;
        markerVisible = false;
    }

    // ── fall analysis ───────────────────────────────────────────

    private void analyzeFall(MinecraftClient client) {
        lavaBelow = false;
        voidBelow = false;
        inDanger = false;
        markerVisible = false;

        if (client.player.onGround) fallLatched = false;

        double feetY = client.player.y;
        int bx = MathHelper.floor(client.player.x);
        int bz = MathHelper.floor(client.player.z);
        int startY = MathHelper.floor(feetY);

        double lavaTop = Double.MAX_VALUE;
        double groundTop = Double.MAX_VALUE;
        boolean foundSolid = false;

        for (int y = startY; y > startY - 64; y--) {
            Material mat = materialAt(client, bx, y, bz);
            if (mat == Material.AIR) continue;
            if (mat == Material.LAVA) {
                if (lavaTop == Double.MAX_VALUE) lavaTop = y + 1;
                continue;
            }
            // Water or any solid block is the landing surface.
            groundTop = y + 1;
            foundSolid = true;
            break;
        }

        if (!foundSolid) {
            // Pure void below: no bucket can save a 1.8 void fall (the use
            // raycast finds nothing to place against) — edge save only.
            voidBelow = true;
            solidTop = Double.MAX_VALUE;
            surfaceY = startY - 64.0;
            surfaceDist = Double.MAX_VALUE;
            return;
        }

        solidTop = groundTop;
        if (lavaTop != Double.MAX_VALUE && lavaTop <= groundTop) {
            lavaBelow = true;
            surfaceY = lavaTop;
        } else {
            surfaceY = groundTop;
        }
        surfaceDist = surfaceY - feetY;
        // Danger is decided once per fall: a lethal drop stays dangerous all
        // the way down even while surfaceDist shrinks past the threshold.
        if (!client.player.onGround && surfaceDist > getDoubleSetting("fallH")) {
            fallLatched = true;
        }
        inDanger = lavaBelow || fallLatched;

        if (inDanger) {
            markerVisible = true;
            markerX = bx + 0.5;
            markerY = surfaceY;
            markerZ = bz + 0.5;
        }
    }

    // ── edge save: press sneak before walking off a lethal cliff ─

    private void tickEdge(MinecraftClient client) {
        edgeDanger = computeEdgeDanger(client);
        boolean want = "On".equals(getStringSetting("edge")) && edgeDanger;
        if (want && !sneakHeld) {
            ((KeyBindingAccessor) client.options.keySneak).setPressed(true);
            sneakHeld = true;
        } else if (!want && sneakHeld) {
            ((KeyBindingAccessor) client.options.keySneak).setPressed(false);
            sneakHeld = false;
        }
    }

    private boolean computeEdgeDanger(MinecraftClient client) {
        if (!client.player.onGround) return false;
        if (client.player.isSneaking()) return false; // already safe

        double vx = client.player.x - client.player.prevX;
        double vz = client.player.z - client.player.prevZ;
        double speed = Math.sqrt(vx * vx + vz * vz);
        if (speed < 0.04) return false; // standing still

        vx /= speed;
        vz /= speed;
        int ay = MathHelper.floor(client.player.y);
        // Check the cell we're about to step into (plus a slightly closer
        // cell for diagonal runs).
        for (double look : new double[]{1.1, 0.6}) {
            int ax = MathHelper.floor(client.player.x + vx * look);
            int az = MathHelper.floor(client.player.z + vz * look);
            if (lethalDropBelow(client, ax, ay, az)) {
                edgeDanger = true;
                edgeX = ax + 0.5;
                edgeY = ay + 0.05;
                edgeZ = az + 0.5;
                return true;
            }
        }
        return false;
    }

    /** True if the block at (ax, ay, az) is air with void/lava/lethal drop below. */
    private boolean lethalDropBelow(MinecraftClient client, int ax, int ay, int az) {
        Material at = materialAt(client, ax, ay, az);
        if (at != Material.AIR) return false; // solid or water in the way

        for (int y = ay - 1; y > ay - 12; y--) {
            Material m = materialAt(client, ax, y, az);
            if (m == Material.AIR) continue;
            if (m == Material.LAVA) return true;
            if (m == Material.WATER) return false; // water landing — safe
            int drop = ay - (y + 1); // air blocks below the edge
            return drop >= getDoubleSetting("fallH");
        }
        return true; // nothing found within 12 — void below the edge
    }

    // ── the clutch state machine ────────────────────────────────

    private void tickClutch(MinecraftClient client) {
        boolean landed = client.player.onGround || client.player.velocityY >= 0.0;
        // Hard abort: the fall resolved while we were still setting up. A
        // save already in RECOVER is allowed to finish on the ground — the
        // hotbar swap-back and the eased camera recovery must complete even
        // after touchdown (that is exactly when a human looks back up).
        if (landed && phase != IDLE && phase != RECOVER) {
            abort(client);
            return;
        }
        // Pure void falls can't be clutched with a bucket.
        if (voidBelow && phase != RECOVER) {
            if (phase != IDLE) abort(client);
            return;
        }

        switch (phase) {
            case IDLE: {
                if (cooldownTicks > 0) return;
                if (!inDanger) return;
                if (RANDOM.nextInt(100) >= getDoubleSetting("chance")) return;
                String mode = getStringSetting("mode");
                if ("Fall".equals(mode) && lavaBelow) return;
                if ("Lava".equals(mode) && !lavaBelow) return;
                // Start the reaction a little early so the window is open
                // by the time the flick completes.
                if (surfaceDist > adaptivePlaceDist() + 2.2) return;

                bucketSlot = findWaterBucket(client);
                if (bucketSlot < 0) return; // no bucket — nothing we can do
                prevSlot = client.player.inventory.selectedSlot;
                savedPitch = client.player.pitch;
                phase = ROTATE;
                phaseTicks = "On".equals(getStringSetting("humanize"))
                        ? 1 + RANDOM.nextInt(2) : 1;
                break;
            }
            case ROTATE: {
                rotated = true;
                if (phaseTicks > 0) {
                    // Eased flick toward straight-down.
                    client.player.pitch += (90.0F - client.player.pitch) * 0.6F;
                    phaseTicks--;
                } else if (phaseTicks == 0) {
                    // Exact 90° — the NEXT movement packet carries it, so
                    // the server's placement raycast also points down.
                    client.player.pitch = 90.0F;
                    phaseTicks = -1;
                    waitTicks = 0;
                } else {
                    // Holding at 90: wait for the placement window.
                    client.player.pitch = 90.0F;
                    // Failsafe: we fell past the window or drifted off the
                    // column — don't stay rotated forever.
                    if (++waitTicks > 14) {
                        abort(client);
                        return;
                    }
                    if (placementPossible(client)
                            && surfaceDist <= adaptivePlaceDist()
                            && client.player.velocityY < 0.0) {
                        phase = PLACE;
                    }
                }
                break;
            }
            case PLACE: {
                client.player.inventory.selectedSlot = bucketSlot;
                if (client.interactionManager != null) {
                    client.interactionManager.interactItem(
                            client.player, client.world,
                            client.player.inventory.getMainHandStack());
                    placed = true;
                    markerVisible = false;
                }
                phase = RECOVER;
                phaseTicks = "On".equals(getStringSetting("humanize"))
                        ? 3 + RANDOM.nextInt(6) : 2;
                break;
            }
            case RECOVER: {
                // Swap the hotbar back first — the delayed swap reads like a
                // player who just did a clutch and is still watching the
                // water land. The bucket slot only stays selected for the
                // duration of the hold.
                if (prevSlot >= 0 && client.player.inventory.selectedSlot == bucketSlot) {
                    client.player.inventory.selectedSlot = prevSlot;
                    prevSlot = -1;
                }
                // Ease the camera back up instead of snapping — a human flicks
                // the look back down after an MLG, they don't teleport it. The
                // eased recovery keeps Intave's look-heuristic clean.
                if (rotated) {
                    float diff = savedPitch - client.player.pitch;
                    if (Math.abs(diff) < 0.75F) {
                        client.player.pitch = savedPitch;
                        rotated = false;
                    } else {
                        client.player.pitch += diff * 0.45F;
                    }
                }
                if (--phaseTicks <= 0 || (!rotated && prevSlot < 0)) {
                    finishRestore(client);
                }
                return;
            }
        }
    }

    /** True when the use-raycast (5.0 from the eyes) can reach the landing
     *  surface — for lava this is the solid floor beneath the lake. */
    private boolean placementPossible(MinecraftClient client) {
        double target = lavaBelow ? solidTop : surfaceY;
        return target - (client.player.y + 1.62) <= 5.0;
    }

    /**
     * Distance below the feet at which to place. The client raycast reaches
     * 3.38 blocks below the feet (5.0 from the eyes), and the server places
     * from its own lagged position — the higher the ping, the earlier we
     * must place so the server's raycast still reaches the ground.
     */
    private double adaptivePlaceDist() {
        double base = Math.min(getDoubleSetting("range"), 3.38);
        double pingSec = PingTracker.hasPing() ? PingTracker.getPingMs() / 1000.0 : 0.0;
        double lagBlocks = Math.min(pingSec * 12.0, 3.0); // ~12 b/s terminal fall
        return Math.max(0.9, base - lagBlocks);
    }

    /** Finishes the recovery (slot + pitch already handled) and returns to
     *  idle with a humanized cooldown so saves never chain back-to-back. */
    private void finishRestore(MinecraftClient client) {
        rotated = false;
        placed = false;
        prevSlot = -1;
        bucketSlot = -1;
        phase = IDLE;
        phaseTicks = 0;
        waitTicks = 0;
        cooldownTicks = 30 + RANDOM.nextInt(21);
    }

    /** Emergency restore when the fall resolves before the clutch finished. */
    private void abort(MinecraftClient client) {
        if (phase == IDLE) return;
        if (client != null && client.player != null) {
            if (rotated) client.player.pitch = savedPitch;
            if (prevSlot >= 0) client.player.inventory.selectedSlot = prevSlot;
        }
        rotated = false;
        placed = false;
        prevSlot = -1;
        bucketSlot = -1;
        phase = IDLE;
        phaseTicks = 0;
        waitTicks = 0;
        cooldownTicks = 20 + RANDOM.nextInt(21);
    }

    /** Finds a water bucket in the hotbar (slots 0–8), or -1. */
    private int findWaterBucket(MinecraftClient client) {
        ItemStack[] main = client.player.inventory.main;
        for (int i = 0; i < 9; i++) {
            ItemStack s = main[i];
            if (s != null && s.getItem() == Items.WATER_BUCKET && s.count > 0) {
                return i;
            }
        }
        return -1;
    }

    private Material materialAt(MinecraftClient client, int x, int y, int z) {
        BlockState state = client.world.getBlockState(new BlockPos(x, y, z));
        return state.getBlock().getMaterial();
    }

    // ── rendering: impact + edge markers ────────────────────────

    public static void render(float partialTicks) {
        if (instance == null || !instance.isEnabled()
                || "Off".equals(instance.getStringSetting("render"))) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        if (!instance.markerVisible && !instance.edgeDanger) return;

        double camX = client.player.prevX + (client.player.x - client.player.prevX) * partialTicks;
        double camY = client.player.prevY + (client.player.y - client.player.prevY) * partialTicks;
        double camZ = client.player.prevZ + (client.player.z - client.player.prevZ) * partialTicks;

        WorldDraw.begin(false);

        // Predicted impact: red for lava, orange for fall damage.
        if (instance.markerVisible && instance.inDanger) {
            float r = instance.lavaBelow ? 1.0f : 1.0f;
            float g = instance.lavaBelow ? 0.30f : 0.60f;
            float b = instance.lavaBelow ? 0.30f : 0.20f;
            double ex = instance.markerX - camX;
            double ey = instance.markerY - camY;
            double ez = instance.markerZ - camZ;
            WorldDraw.line(ex, ey - 0.5, ez, ex, ey + 0.5, ez, r, g, b, 0.9f);
            ring(ex, ey, ez, r, g, b);
        }

        // Edge danger: red ring on the cell you would step off.
        if (instance.edgeDanger) {
            double ex = instance.edgeX - camX;
            double ey = instance.edgeY - camY;
            double ez = instance.edgeZ - camZ;
            ring(ex, ey, ez, 1.0f, 0.25f, 0.25f);
        }

        WorldDraw.end();
    }

    private static void ring(double ex, double ey, double ez, float r, float g, float b) {
        int segments = 12;
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0 * i / segments;
            double a1 = Math.PI * 2.0 * (i + 1) / segments;
            WorldDraw.line(
                    ex + Math.cos(a0) * 0.45, ey, ez + Math.sin(a0) * 0.45,
                    ex + Math.cos(a1) * 0.45, ey, ez + Math.sin(a1) * 0.45,
                    r, g, b, 0.9f);
        }
    }
}
