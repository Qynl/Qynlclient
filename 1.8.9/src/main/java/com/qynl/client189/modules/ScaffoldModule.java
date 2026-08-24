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

        // Support block + face to place against. When filling the block ahead
        // (bridging), the support is the block we stand on — the placement
        // goes against its side face facing the gap. When placing under our
        // own feet, the support is the block below (face UP). The support
        // must always be solid: the server silently drops placements whose
        // support block is air, so the old target.down() support broke
        // forward bridging over gaps (the block under the gap is air).
        BlockPos feet = new BlockPos(
                (int) Math.floor(player.x),
                (int) Math.floor(player.getBoundingBox().minY - 0.01),
                (int) Math.floor(player.z));
        BlockPos support;
        Direction face;
        int tdx = target.getX() - feet.getX();
        int tdz = target.getZ() - feet.getZ();
        if (tdx == 0 && tdz == 0) {
            support = feet.down();
            face = Direction.UP;
        } else if (tdx > 0) {
            support = feet;
            face = Direction.EAST;
        } else if (tdx < 0) {
            support = feet;
            face = Direction.WEST;
        } else if (tdz > 0) {
            support = feet;
            face = Direction.SOUTH;
        } else {
            support = feet;
            face = Direction.NORTH;
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

        float yawRad = (float) Math.toRadians(player.yaw);
        int dx, dz;
        if ("NinjaBridge".equals(getStringSetting("mode"))) {
            // Ninja bridging places DIAGONALLY at 45°: the combined forward +
            // strafe input steers the placement into the next cell the player
            // will step into. Holding W+D bridges forward-right, W+A forward-
            // left — the classic 45° ninja technique — instead of always the
            // pure forward cell. Falls back to the feet block when idle.
            double fwd = player.input.movementForward;
            double strafe = player.input.movementSideways;
            double vx = -Math.sin(yawRad) * fwd + Math.cos(yawRad) * strafe;
            double vz = Math.cos(yawRad) * fwd + Math.sin(yawRad) * strafe;
            double vlen = Math.sqrt(vx * vx + vz * vz);
            if (vlen < 0.05) {
                dx = 0;
                dz = 0;
            } else {
                vx /= vlen;
                vz /= vlen;
                dx = (int) Math.signum(vx);
                dz = (int) Math.signum(vz);
            }
        } else {
            // Movement direction (from input yaw, which is what walking
            // uses); falls back to the look direction when idle.
            double mx = player.x - player.prevX;
            double mz = player.z - player.prevZ;
            double speed = Math.sqrt(mx * mx + mz * mz);
            if (speed > 0.02) {
                dx = (int) Math.signum(mx);
                dz = (int) Math.signum(mz);
            } else {
                dx = (int) Math.signum(-Math.sin(yawRad));
                dz = (int) Math.signum(Math.cos(yawRad));
            }
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

    private boolean isAir(MinecraftClient client, BlockPos pos) {
        return client.world.isAir(pos);
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
