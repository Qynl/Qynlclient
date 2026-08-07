package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import com.qynl.client189.Setting;
import com.qynl.client189.mixin.KeyBindingAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.input.Keyboard;

import java.util.Random;

/**
 * NinjaBridge — automates sneaking at block edges for full-speed bridging.
 *
 * <p><b>Straight mode:</b> walk backwards (S) while the module taps sneak
 * at the edge of each block.</p>
 *
 * <p><b>45° Diagonal mode:</b> walk diagonally backwards (S + one strafe
 * key) for true ninja-bridge speed. The strafe side follows the bridge
 * line: while moving it is held steady (no mid-bridge zig-zag), and when
 * you stop at an edge it is re-picked to aim at the gap you are bridging
 * toward. You can force it with the Diag. side setting.</p>
 *
 * <p>Edge behavior — the timing is driven by the player's actual position,
 * not by fixed tick counts, so it works at any walking speed:</p>
 * <ul>
 *   <li><b>Placement starts early</b> (as soon as the gap is within reach
 *       and the crosshair is on a block), so the next block is already on
 *       the ground when you arrive — never a click into the air.</li>
 *   <li><b>Sneak only engages while you actually cross the edge</b> and is
 *       held until you are safely onto the next block; if you stop at an
 *       edge it stays held so you never slide off.</li>
 *   <li>The <b>Sneak Start</b> setting moves the sneak point (earlier =
 *       safer, later = faster), and timing carries a small humanized jitter
 *       so the taps don't look machine-perfect.</li>
 *   <li>All auto-pressed keys (sneak, walk, strafe) are released as soon as
 *       the module is disabled, you stop holding a block, open a screen, or
 *       die.</li>
 * </ul>
 */
public class NinjaBridgeModule extends Module {

    private static final Random RANDOM = new Random();

    /** Start placing this far from the block edge (fraction of a block). */
    private static final double PLACE_EDGE = 0.20;

    private boolean sneaking;
    private int placeCooldown;
    private boolean diagonalLeft = true; // last diagonal lean, held while moving

    public NinjaBridgeModule() {
        super("NinjaBridge", "Auto-sneaks at block edges while bridging. 45° diagonal for max speed.", Category.ASSIST);
        bindKey(Keyboard.KEY_N);
        addSetting(Setting.options("mode",      "Mode",        "Straight", "Straight", "45° Diagonal"));
        addSetting(Setting.range("speed",       "Speed",       75.0, 50, 100, 1, "%"));
        addSetting(Setting.range("edgeSneak",   "Sneak Start", 40.0, 30, 48, 1, "%"));
        addSetting(Setting.options("autoWalk",  "Auto Walk",   "On",  "Off", "On"));
        addSetting(Setting.options("place",     "Place",       "Auto", "Auto", "Hold RMB"));
        addSetting(Setting.options("side",      "Diag. side",  "Auto", "Auto", "Left", "Right"));
        addSetting(Setting.options("humanize",  "Humanize",    "On",  "Off", "On"));
    }

    @Override
    public void onDisable() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            releaseSneak(client);
            releaseWalkKeys(client);
        }
        sneaking = false;
        placeCooldown = 0;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null || client.interactionManager == null) {
            releaseAll(client);
            return;
        }
        if (client.currentScreen != null || !client.player.isAlive()) {
            releaseAll(client);
            return;
        }

        if (!isHoldingBlock(client)) {
            releaseSneak(client);
            releaseWalkKeys(client);
            return;
        }

        boolean diagonal = "45° Diagonal".equals(getStringSetting("mode"));
        boolean humanize = "On".equals(getStringSetting("humanize"));
        boolean autoPlace = "Auto".equals(getStringSetting("place"));
        double speedPct = getDoubleSetting("speed") / 100.0;

        // Sneak engages when the edge is actually reached (adjustable so
        // players can trade a little safety for a later, faster tap).
        // Clamped so stale config values (the old 1–5 tick window) stay sane.
        double sneakEdge = Math.max(0.25, Math.min(0.48, getDoubleSetting("edgeSneak") / 100.0));
        double releaseEdge = Math.max(0.15, sneakEdge - 0.07);

        GapInfo gap = diagonal ? gapInfoDiagonal(client) : gapInfoStraight(client);

        // ── Sneak: hold while crossing the edge (engage at sneakEdge, let go
        //    only after the player is well onto the next block), and hold
        //    whenever the player stops at an open edge.
        if (gap.gap && gap.maxRel > (sneaking ? releaseEdge : sneakEdge)) {
            if (!sneaking) {
                pressSneak(client);
                sneaking = true;
            }
        } else {
            releaseSneak(client);
        }

        // ── Place: fill the gap as soon as it comes within reach, but only
        //    while the crosshair is on a block so we never click into the air.
        boolean canPlace = autoPlace && gap.gap && gap.maxRel > PLACE_EDGE
                && client.result != null && client.result.type == BlockHitResult.Type.BLOCK;
        if (placeCooldown > 0) placeCooldown--;
        if (canPlace && placeCooldown <= 0) {
            client.interactionManager.interactItem(
                    client.player, client.world, client.player.inventory.getMainHandStack());
            int base = Math.max(1, (int) Math.round(2.0 / speedPct));
            placeCooldown = humanize ? base + RANDOM.nextInt(2) : base;
        } else if (!gap.gap) {
            placeCooldown = 0; // fresh cooldown for the next edge
        }

        // ── Auto walk ────────────────────────────────────────
        if ("On".equals(getStringSetting("autoWalk"))) {
            if (diagonal) {
                applyDiagonalWalk(client, gap);
            } else {
                ((KeyBindingAccessor) client.options.keyBack).setPressed(true);
            }
        }
    }

    // ── diagonal walk ───────────────────────────────────────────

    /**
     * Moves diagonally backward (S + one strafe key). The strafe side is
     * chosen so the motion continues along the bridge line: held steady
     * while moving (no zig-zag), re-aimed at the gap when stopped at an
     * edge, and a yaw-based default when stopped on open ground. The
     * Diag. side setting overrides all of it.
     */
    private void applyDiagonalWalk(MinecraftClient client, GapInfo gap) {
        String side = getStringSetting("side");
        boolean wantLeft;
        if ("Left".equals(side)) {
            wantLeft = true;
        } else if ("Right".equals(side)) {
            wantLeft = false;
        } else if (isMoving(client)) {
            wantLeft = diagonalLeft; // keep the lean while bridging
        } else if (gap.gap) {
            wantLeft = sideForGap(client, gap); // stopped at an edge: aim along the bridge
        } else {
            wantLeft = sideForYaw(client); // stopped on open ground
        }

        diagonalLeft = wantLeft;

        ((KeyBindingAccessor) client.options.keyBack).setPressed(true);
        ((KeyBindingAccessor) client.options.keyLeft).setPressed(wantLeft);
        ((KeyBindingAccessor) client.options.keyRight).setPressed(!wantLeft);
    }

    /**
     * Picks the strafe side whose diagonal motion best matches the direction
     * of the gap. With S+A the motion is ~(sin+cos, sin−cos) × √2 and with
     * S+D it is ~(sin−cos, −cos−sin) × √2 (yaw convention 0° = +Z). This
     * keeps the player on the bridge line no matter how the camera is turned.
     */
    private boolean sideForGap(MinecraftClient client, GapInfo gap) {
        double sin = Math.sin(Math.toRadians(client.player.yaw));
        double cos = Math.cos(Math.toRadians(client.player.yaw));

        double dotLeft  = gap.aimDx * (sin + cos) + gap.aimDz * (sin - cos);
        double dotRight = gap.aimDx * (sin - cos) + gap.aimDz * (-cos - sin);

        if (Math.abs(dotLeft - dotRight) < 1e-6) return diagonalLeft; // symmetric: keep
        return dotLeft > dotRight;
    }

    /**
     * Default diagonal lean for Auto when not at an edge:
     *   0° = south, 90° = west, 180° = north, 270° = east.
     * Facing the western/northern half leans left (A), the eastern/southern
     * half leans right (D).
     */
    private boolean sideForYaw(MinecraftClient client) {
        float yaw = client.player.yaw;
        while (yaw < 0) yaw += 360;
        yaw = yaw % 360;
        return (yaw >= 90 && yaw < 180) || yaw >= 270;
    }

    private void releaseWalkKeys(MinecraftClient client) {
        ((KeyBindingAccessor) client.options.keyBack).setPressed(false);
        ((KeyBindingAccessor) client.options.keyLeft).setPressed(false);
        ((KeyBindingAccessor) client.options.keyRight).setPressed(false);
    }

    private void releaseAll(MinecraftClient client) {
        releaseSneak(client);
        releaseWalkKeys(client);
    }

    // ── edge detection ──────────────────────────────────────────

    /** What the edge detector found this tick. */
    private static final class GapInfo {
        boolean gap;          // open edge (air block + air below) near us?
        int aimDx, aimDz;     // block direction toward the gap (for the lean)
        double maxRel;        // furthest offset from the block center (0..0.5)
    }

    /**
     * Straight mode: a gap on either horizontal axis, each checked with its
     * own sign so corners and off-center positions never confuse the axes.
     */
    private GapInfo gapInfoStraight(MinecraftClient client) {
        GapInfo info = new GapInfo();
        BlockPos feet = feetPos(client);
        double relX = client.player.x - feet.getX() - 0.5;
        double relZ = client.player.z - feet.getZ() - 0.5;
        info.maxRel = Math.max(Math.abs(relX), Math.abs(relZ));

        if (Math.abs(relX) > 0.05) {
            int dx = relX > 0 ? 1 : -1;
            if (isGap(client, feet.add(dx, 0, 0))) {
                info.gap = true;
                info.aimDx = dx;
            }
        }
        if (Math.abs(relZ) > 0.05) {
            int dz = relZ > 0 ? 1 : -1;
            if (isGap(client, feet.add(0, 0, dz))) {
                info.gap = true;
                info.aimDz = dz;
            }
        }
        return info;
    }

    /**
     * Diagonal mode: the gap is looked for at the corner the player is
     * heading toward (movement direction while moving, position bias while
     * still) plus both cardinal neighbors of that corner, so it also covers
     * nearly-straight motion. A movement component that is ~0 falls back to
     * the position bias instead of defaulting to an arbitrary sign.
     */
    private GapInfo gapInfoDiagonal(MinecraftClient client) {
        GapInfo info = new GapInfo();
        BlockPos feet = feetPos(client);
        double relX = client.player.x - feet.getX() - 0.5;
        double relZ = client.player.z - feet.getZ() - 0.5;
        info.maxRel = Math.max(Math.abs(relX), Math.abs(relZ));

        double mx, mz;
        if (isMoving(client)) {
            mx = client.player.x - client.player.prevX;
            mz = client.player.z - client.player.prevZ;
        } else {
            mx = relX;
            mz = relZ;
        }

        int dx = Math.abs(mx) > 0.05 ? (mx > 0 ? 1 : -1) : (relX > 0 ? 1 : -1);
        int dz = Math.abs(mz) > 0.05 ? (mz > 0 ? 1 : -1) : (relZ > 0 ? 1 : -1);
        info.aimDx = dx;
        info.aimDz = dz;

        info.gap = isGap(client, feet.add(dx, 0, dz))
                || isGap(client, feet.add(dx, 0, 0))
                || isGap(client, feet.add(0, 0, dz));
        return info;
    }

    // ── helpers ─────────────────────────────────────────────────

    private BlockPos feetPos(MinecraftClient client) {
        return new BlockPos(
                (int) Math.floor(client.player.x),
                (int) Math.floor(client.player.getBoundingBox().minY - 0.01),
                (int) Math.floor(client.player.z));
    }

    /** True when the block is missing and there is nothing below it to stand on. */
    private boolean isGap(MinecraftClient client, BlockPos pos) {
        return client.world.isAir(pos) && client.world.isAir(pos.down());
    }

    /** True when the player is actually moving horizontally (> ~5 mm/tick). */
    private boolean isMoving(MinecraftClient client) {
        if (client.player == null) return false;
        double mx = client.player.x - client.player.prevX;
        double mz = client.player.z - client.player.prevZ;
        return mx * mx + mz * mz > 0.000025;
    }

    private void pressSneak(MinecraftClient client) {
        ((KeyBindingAccessor) client.options.keySneak).setPressed(true);
    }

    private void releaseSneak(MinecraftClient client) {
        if (sneaking) {
            ((KeyBindingAccessor) client.options.keySneak).setPressed(false);
            sneaking = false;
        }
    }

    private boolean isHoldingBlock(MinecraftClient client) {
        if (client.player == null) return false;
        ItemStack held = client.player.inventory.getMainHandStack();
        return held != null && held.getItem() instanceof BlockItem;
    }
}
