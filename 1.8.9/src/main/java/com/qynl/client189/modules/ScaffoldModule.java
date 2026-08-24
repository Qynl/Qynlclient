package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import com.qynl.client189.Setting;
import com.qynl.client189.SilentAim;
import com.qynl.client189.mixin.KeyBindingAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.input.Keyboard;

import java.util.Random;

/**
 * ScaffoldWalk — places a block under your feet as you walk, so you can
 * bridge across gaps, climb pillars and walk on air without ever aiming
 * down or clicking. The placement uses the same normal right-click block
 * path a player uses ({@code ClientPlayerInteractionManager.onRightClick}),
 * so the server only ever sees ordinary block placement packets. A humanized
 * click delay keeps the pattern from looking machine-perfect.
 */
public class ScaffoldModule extends Module {
    private static final Random RANDOM = new Random();

    private int placeCooldown = 0;

    // Rotation spoof state: the player's camera is set to the placement look
    // for one tick so the movement packet carries it (1.8.9 placement packets
    // have no rotation — ACs infer your look from movement packets), then the
    // mixin restores the real camera. Tracked here as a safety fallback.
    private boolean spoofPending = false;
    private float spoofRealYaw = 0.0F;
    private float spoofRealPitch = 0.0F;
    private boolean sneakHeld = false;

    public ScaffoldModule() {
        super("Scaffold", "Places a block under your feet while walking — bridge gaps and pillars without aiming down.",
                Category.UTILITY);
        bindKey(Keyboard.KEY_F9);
        addSetting(Setting.options("mode",   "Mode",    "Forward", "Forward", "Always", "NinjaBridge"));
        addSetting(Setting.range("delay",    "Delay",   2.0, 1, 6, 1, "t"));
        addSetting(Setting.options("humanize", "Humanize", "On", "On", "Off"));
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null || client.interactionManager == null) {
            return;
        }
        if (client.currentScreen != null || !client.player.isAlive()) {
            placeCooldown = 0;
            return;
        }
        ClientPlayerEntity player = client.player;

        // Safety: restore the camera from last tick's rotation spoof if the
        // movement packet never went out (idempotent if the mixin did it).
        // Must run BEFORE the early returns — otherwise sneaking/using/riding
        // while a spoof is pending would skip the restore and leave the
        // camera stuck on the placement look.
        if (spoofPending) {
            player.yaw = spoofRealYaw;
            player.pitch = spoofRealPitch;
            spoofPending = false;
        }
        if (player.isSneaking() || player.isUsingItem() || player.hasVehicle()) {
            releaseSneak(client);
            return;
        }
        boolean forwardOnly = "Forward".equals(getStringSetting("mode"));
        if (forwardOnly && player.input.movementForward <= 0.0F) {
            return;
        }
        if (!forwardOnly && player.input.movementForward == 0.0F && player.input.movementSideways == 0.0F) {
            return;
        }
        if (placeCooldown > 0) {
            placeCooldown--;
            return;
        }

        // Pick a block item: keep the current slot when it holds a block,
        // otherwise grab the first block in the hotbar (normal slot switch).
        PlayerInventory inventory = player.inventory;
        ItemStack held = inventory.getMainHandStack();
        if (!(held != null && held.getItem() instanceof BlockItem)) {
            int blockSlot = -1;
            for (int i = 0; i < 9; i++) {
                ItemStack stack = inventory.main[i];
                if (stack != null && stack.getItem() instanceof BlockItem) {
                    blockSlot = i;
                    break;
                }
            }
            if (blockSlot < 0) {
                return;
            }
            inventory.selectedSlot = blockSlot;
            held = inventory.getMainHandStack();
        }
        if (held == null || !(held.getItem() instanceof BlockItem)) {
            return;
        }

        BlockPos target = findTarget(client);
        if (target == null) {
            return;
        }

        // Support block + face to place against. Our placement puts the new
        // block at support.offset(face), so we must pick (support, face) with
        // support.offset(face) == target. The support has to be solid — the
        // server silently drops placements whose support block is air (the
        // old target.down() support broke bridging over gaps, where the block
        // under the target is air).
        //
        // A single Direction moves only ONE axis, so a strict DIAGONAL target
        // (NinjaBridge 45°) can never be reached from the feet block alone —
        // feet.offset(East/South) lands on an axis cell one short. The correct
        // 1.8 placement for a diagonal cell is against an already-solid
        // neighbor: search the target's 4 horizontal neighbours for a solid
        // support S and place across the face that runs back to the target.
        BlockPos feet = new BlockPos(
                (int) Math.floor(player.x),
                (int) Math.floor(player.getBoundingBox().minY - 0.01),
                (int) Math.floor(player.z));
        BlockPos support;
        Direction face;
        if (target.equals(feet)) {
            support = feet.down();
            face = Direction.UP;
        } else {
            BlockPos supportCell = findSolidSupport(client, target, feet);
            if (supportCell != null) {
                // guarantee supportCell.offset(face) == target
                support = supportCell;
                face = directionFrom(supportCell, target);
            } else {
                // No solid single-axis neighbour (e.g. bridging off a single
                // pillar diagonally): fall back to placing an axis-aligned
                // intermediate cell against the feet block so the bridge
                // still forms in one or two steps.
                int tdx = Integer.signum(target.getX() - feet.getX());
                int tdz = Integer.signum(target.getZ() - feet.getZ());
                support = feet;
                if (tdx != 0) {
                    face = tdx > 0 ? Direction.EAST : Direction.WEST;
                } else {
                    face = tdz > 0 ? Direction.SOUTH : Direction.NORTH;
                }
            }
        }

        // The hit point varies across the face each time — a perfectly
        // centered hit vector on every placement is a machine signature some
        // ACs fingerprint.
        boolean humanize = "On".equals(getStringSetting("humanize"));
        double rx = humanize ? 0.35 + RANDOM.nextDouble() * 0.3 : 0.5;
        double ry = humanize ? 0.35 + RANDOM.nextDouble() * 0.3 : 0.5;
        double rz = humanize ? 0.35 + RANDOM.nextDouble() * 0.3 : 0.5;
        Vec3d hitVec;
        switch (face) {
            case UP:
                hitVec = new Vec3d(support.getX() + rx, support.getY() + 1.0, support.getZ() + rz);
                break;
            case EAST:
                hitVec = new Vec3d(support.getX() + 1.0, support.getY() + ry, support.getZ() + rz);
                break;
            case WEST:
                hitVec = new Vec3d(support.getX(), support.getY() + ry, support.getZ() + rz);
                break;
            case SOUTH:
                hitVec = new Vec3d(support.getX() + rx, support.getY() + ry, support.getZ() + 1.0);
                break;
            default: // NORTH
                hitVec = new Vec3d(support.getX() + rx, support.getY() + ry, support.getZ());
                break;
        }

        // Edge sneak: while bridging over air, hold sneak exactly like a real
        // bridger (Vulcan/Intave scaffold checks expect the sneak state).
        setSneak(client, isAir(client, target.down()));

        // Rotation spoof: 1.8.9 placement packets carry no rotation, so ACs
        // infer the placement look from movement packets. Arm a one-tick
        // look at the placement — the movement packet this tick then shows
        // the player looking down at the block (like a real bridger), while
        // the camera itself never moves.
        Vec3d eye = player.getCameraPosVec(1.0F);
        double ldx = hitVec.x - eye.x;
        double ldy = hitVec.y - eye.y;
        double ldz = hitVec.z - eye.z;
        double lDist = Math.sqrt(ldx * ldx + ldy * ldy + ldz * ldz);
        if (lDist > 0.01) {
            float spoofYaw = (float) Math.toDegrees(Math.atan2(-ldx, ldz));
            float spoofPitch = (float) Math.toDegrees(Math.asin(-ldy / lDist));
            boolean ninja = "NinjaBridge".equals(getStringSetting("mode"));
            if (ninja) {
                // Ninja bridging reads as a ~50° down-forward glance — the
                // player looks down barely past the horizon while walking,
                // never straight down (a straight-down look + diagonal step
                // is exactly what flags silent scaffold as inhuman). Force
                // the spoofed pitch into the 48–56° ninja band; the yaw
                // already points at the placement cell being bridged.
                spoofPitch = Math.min(90.0F, Math.max(30.0F,
                        52.0F + (RANDOM.nextFloat() - 0.5F) * 8.0F));
            }
            // GCD fix: snap the spoofed look to the mouse grid, exactly like
            // a real mouse turn. An instant snap to a non-grid rotation is
            // the fingerprint Grim uses to catch silent scaffold.
            double sens = client.options.sensitivity;
            double f = sens * 0.6 + 0.2;
            double gcdStep = (f * f * f) * 8.0 * 0.15;
            if (gcdStep > 1e-6) {
                spoofYaw = (float) (Math.round(spoofYaw / gcdStep) * gcdStep);
                spoofPitch = (float) (Math.round(spoofPitch / gcdStep) * gcdStep);
            }
            spoofRealYaw = player.yaw;
            spoofRealPitch = player.pitch;
            SilentAim.spoofForTick(spoofRealYaw, spoofRealPitch, spoofYaw, spoofPitch);
            player.yaw = spoofYaw;
            player.pitch = spoofPitch;
            spoofPending = true;
        }

        // The face passed here determines where the block actually goes
        // (support.offset(face)) — it must be the computed face, not UP:
        // clicking the side of the block under our feet with face EAST
        // places the block into the gap ahead.
        boolean placed = client.interactionManager.onRightClick(
                player, (ClientWorld) client.world, held, support, face, hitVec);
        if (placed) {
            player.swingHand();
            int base = (int) getDoubleSetting("delay");
            placeCooldown = humanize ? base + RANDOM.nextInt(3) : base;
            if (humanize && RANDOM.nextInt(100) < 8) {
                placeCooldown += 3; // occasional longer pause, like a real player
            }
        }
    }

    /**
     * The block to fill: the one the player is about to step onto (one block
     * ahead along the movement direction at foot level), or the block under
     * the feet when it is already air.
     */
    private BlockPos findTarget(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        BlockPos feet = new BlockPos(
                (int) Math.floor(player.x),
                (int) Math.floor(player.getBoundingBox().minY - 0.01),
                (int) Math.floor(player.z));

        if ("NinjaBridge".equals(getStringSetting("mode"))) {
            // Ninja bridging advances ONE axis at a time — a strict diagonal
            // cell (feet ± 1, ± 1) can't be placed in a single click against
            // the block under you, and on a 1-wide bridge there is no solid
            // side cell to place it against. Real 45° ninja-bridging lays an
            // axis-aligned STAIRCASE instead: each block is placed against
            // the one you're standing on / just placed. The module alternates
            // between the two axes as the player crosses each block boundary
            // (whichever is closer to being reached next), so holding W+D
            // walks the bridge diagonally while every single placement is a
            // plain axis placement the server accepts.
            double fwd = player.input.movementForward;
            double strafe = player.input.movementSideways;
            float yawRad = (float) Math.toRadians(player.yaw);
            double vx = -Math.sin(yawRad) * fwd + Math.cos(yawRad) * strafe;
            double vz = Math.cos(yawRad) * fwd + Math.sin(yawRad) * strafe;
            double vlen = Math.sqrt(vx * vx + vz * vz);
            if (vlen < 0.05) {
                return isAir(client, feet) ? feet : null;
            }
            vx /= vlen;
            vz /= vlen;
            int dx = (int) Math.signum(vx);
            int dz = (int) Math.signum(vz);

            BlockPos cellX = feet.add(dx, 0, 0);
            BlockPos cellZ = feet.add(0, 0, dz);
            boolean preferX = preferXBoundary(player, dx, dz);
            if (preferX) {
                if (isAir(client, cellX)) return cellX;
                if (isAir(client, cellZ)) return cellZ;
            } else {
                if (isAir(client, cellZ)) return cellZ;
                if (isAir(client, cellX)) return cellX;
            }
            // No air cell directly ahead along the movement — fill the feet
            // cell only if the player is standing in air (falling).
            if (isAir(client, feet)) return feet;
            return null;
        }

        // Other modes: movement direction (from physical motion, falling
        // back to the look direction when idle).
        float yawRad = (float) Math.toRadians(player.yaw);
        double mx = player.x - player.prevX;
        double mz = player.z - player.prevZ;
        double speed = Math.sqrt(mx * mx + mz * mz);
        int dx, dz;
        if (speed > 0.02) {
            dx = (int) Math.signum(mx);
            dz = (int) Math.signum(mz);
        } else {
            dx = (int) Math.signum(-Math.sin(yawRad));
            dz = (int) Math.signum(Math.cos(yawRad));
        }
        BlockPos ahead = feet.add(dx, 0, dz);
        if ((dx != 0 || dz != 0) && isAir(client, ahead) && isAir(client, ahead.down())) {
            return ahead;
        }
        if (isAir(client, feet)) {
            return feet;
        }
        return null;
    }

    /**
     * For a diagonal 45° bridge: whichever block boundary (X or Z) the player
     * is closer to crossing next decides the axis to fill. As they cross one
     * boundary after another this alternates, laying down a diagonal
     * staircase — the call that makes NinjaBridge actually walk at 45°
     * instead of always advancing one fixed axis.
     */
    private boolean preferXBoundary(ClientPlayerEntity player, int dx, int dz) {
        if (dz == 0) return true;   // only X motion
        if (dx == 0) return false;  // only Z motion
        double fx = dx > 0 ? (player.x - Math.floor(player.x)) : (Math.ceil(player.x) - player.x);
        double fz = dz > 0 ? (player.z - Math.floor(player.z)) : (Math.ceil(player.z) - player.z);
        return fx >= fz;
    }

    private boolean isAir(MinecraftClient client, BlockPos pos) {
        return client.world.isAir(pos);
    }

    /**
     * Finds a solid horizontal neighbour of {@code target} to place against.
     * A Direction moves only one axis, so a support is only valid when it
     * differs from the target on EXACTLY one axis (support.offset(face) ==
     * target). The strict diagonal cell — NinjaBridge at 45° — can therefore
     * only be reached by placing against an already-solid side cell, never
     * from the feet block (which differs on two axes). Prefers the side cell
     * nearest the travel direction: the block you naturally bridge from.
     * Returns null when no single-axis neighbour is solid.
     */
    private BlockPos findSolidSupport(MinecraftClient client, BlockPos target, BlockPos feet) {
        int tdx = target.getX() - feet.getX() > 0 ? 1 : (target.getX() - feet.getX() < 0 ? -1 : 0);
        int tdz = target.getZ() - feet.getZ() > 0 ? 1 : (target.getZ() - feet.getZ() < 0 ? -1 : 0);
        // Candidate supports (single-axis offsets from the target), ordered:
        // the two "behind-target" side cells first, then the two "ahead" ones.
        int[][] dirs = {
                {-tdx, 0},
                {0, -tdz},
                {tdx != 0 ? tdx : 1, 0},
                {0, tdz != 0 ? tdz : 1}
        };
        for (int[] off : dirs) {
            BlockPos s = target.add(off[0], 0, off[1]);
            if (!client.world.isAir(s)) {
                return s;
            }
        }
        return null;
    }

    /** The Direction from {@code from} to {@code to} (differ on exactly one axis). */
    private Direction directionFrom(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        if (dx > 0) return Direction.EAST;
        if (dx < 0) return Direction.WEST;
        if (dz > 0) return Direction.SOUTH;
        return Direction.NORTH;
    }

    private void setSneak(MinecraftClient client, boolean sneak) {
        if (client.options == null || client.options.keySneak == null) return;
        if (sneak && !sneakHeld) {
            ((KeyBindingAccessor) client.options.keySneak).setPressed(true);
            sneakHeld = true;
        } else if (!sneak && sneakHeld) {
            ((KeyBindingAccessor) client.options.keySneak).setPressed(false);
            sneakHeld = false;
        }
    }

    private void releaseSneak(MinecraftClient client) {
        if (client.options != null && client.options.keySneak != null && sneakHeld) {
            ((KeyBindingAccessor) client.options.keySneak).setPressed(false);
        }
        sneakHeld = false;
    }

    @Override
    public void onDisable() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.player != null && spoofPending) {
            client.player.yaw = spoofRealYaw;
            client.player.pitch = spoofRealPitch;
        }
        spoofPending = false;
        releaseSneak(client);
    }
}
