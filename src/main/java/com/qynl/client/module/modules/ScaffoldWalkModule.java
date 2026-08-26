package com.qynl.client.module.modules;

import com.qynl.client.QynlClient;
import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import com.qynl.client.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * ScaffoldWalk — Vape-Lite style bridge.
 *
 * <p>Placement direction comes from the <b>movement input</b> (W/A/S/D
 * relative to your yaw), never from velocity — velocity is noisy at low
 * speed and made the old scaffold place blocks "randomly". The camera
 * smoothly aims at the block being placed while you bridge (legit look),
 * and <b>Ninja</b> mode adds the classic 45° diagonal bridge with
 * auto-sneak: hold W+D and the floor builds itself diagonally while you
 * sneak to the edge.</p>
 */
public class ScaffoldWalkModule extends Module {
    private int cooldown = 0;
    private boolean forcedSneak = false;
    /** Committed placement direction (hysteresis), NaN while idle. */
    private double dirAngle = Double.NaN;
    /** Legit camera look state — look down to place, return after. */
    private boolean lookEngaged = false;
    private float restoreYaw = 0.0F, restorePitch = 0.0F;

    public ScaffoldWalkModule() {
        super("ScaffoldWalk",
                "Vape-Lite bridge: input-based placement, smooth camera look, Ninja 45° diagonal with auto-sneak.",
                Category.UTILITY);
        bindKey(GLFW.GLFW_KEY_B);
        // Ninja is the default: the Vape-Lite bridge experience (45° diagonal
        // with auto-sneak). Walk mode places without sneaking.
        addSetting(Setting.options("mode", "Mode", "Ninja", "Walk", "Ninja"));
        addSetting(Setting.range("delay", "Place delay", 2.0, 1, 6, 1, "t"));
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            releaseSneak(mc);
            return;
        }
        var player = mc.player;
        if (player.isSpectator() || player.isPassenger() || player.isUsingItem()
                || player.getAbilities().flying || mc.screen != null) {
            releaseSneak(mc);
            return;
        }

        boolean ninja = "Ninja".equals(getStringSetting("mode"));
        Vec3 move = placementDir(player);
        BlockPos feet = player.getBlockPosBelowThatAffectsMyMovement();
        boolean moving = move.lengthSqr() > 1e-4;

        // Place one block ahead in the movement direction; standing still
        // only fills the hole directly under the player.
        BlockPos target = moving
                ? feet.offset((int) Math.round(move.x), 0, (int) Math.round(move.z))
                : feet;

        BlockPos support = findSupport(mc, target);
        boolean bridging = support != null && mc.level.getBlockState(target).canBeReplaced();

        // Ninja auto-sneak: only while actually bridging over a gap, never on
        // solid ground. Never force-release the player's own sneak key.
        if (ninja) {
            boolean want = bridging && moving;
            if (want) {
                player.setShiftKeyDown(true);
                forcedSneak = true;
            } else if (forcedSneak && !mc.options.keyShift.isDown()) {
                player.setShiftKeyDown(false);
                forcedSneak = false;
            }
        } else if (forcedSneak) {
            player.setShiftKeyDown(false);
            forcedSneak = false;
        }

        // Legit Vape-Lite look: while bridging, the camera glides toward the
        // block being placed; when the bridge ends it returns to the view the
        // player had before (unless AimAssist owns the camera).
        boolean underFeet = Math.abs(target.getX() - feet.getX())
                + Math.abs(target.getZ() - feet.getZ()) == 0;
        smoothLook(player, target, bridging && !underFeet && moving);

        if (!bridging) return;

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        Direction dir = Direction.fromDelta(
                target.getX() - support.getX(),
                target.getY() - support.getY(),
                target.getZ() - support.getZ());
        if (dir == null) return;

        Inventory inventory = player.getInventory();
        int slot = findBlockSlot(inventory);
        if (slot < 0) return;
        if (inventory.selected != slot) inventory.selected = slot;

        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(support)
                        .add(dir.getStepX() * 0.5, dir.getStepY() * 0.5, dir.getStepZ() * 0.5),
                dir, support, false);
        if (player.getEyePosition().distanceTo(hit.getLocation()) > 5.0) return;

        mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
        cooldown = (int) getDoubleSetting("delay");
        // No swing animation — the block just appears at your feet, like a
        // real bridge player (Vape Lite never swings for scaffold).
    }

    /** Movement direction from keyboard input, relative to the player's yaw,
     *  with direction hysteresis: once committed to a direction we keep it
     *  until the input clearly turns (>35°). The aim-assisted yaw wobbles a
     *  few degrees around a target and without this the placed line zigzags
     *  between adjacent diagonals — the old "places random blocks". */
    private Vec3 placementDir(LocalPlayer player) {
        float forward = player.input.forwardImpulse;
        float left = player.input.leftImpulse;
        if (forward == 0.0F && left == 0.0F) {
            // No key input — fall back to the actual movement direction so
            // momentum (knockback, walk-off inertia) keeps bridging instead
            // of stalling the line mid-flight.
            Vec3 vel = player.getDeltaMovement();
            double spd = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
            if (spd > 0.05) {
                double raw = Math.toDegrees(Math.atan2(vel.x, vel.z));
                if (Double.isNaN(dirAngle) || Math.abs(Mth.wrapDegrees(raw - dirAngle)) > 35.0) {
                    dirAngle = raw;
                }
                double snapped = Math.round(dirAngle / 45.0) * 45.0;
                double r = Math.toRadians(snapped);
                return new Vec3(Math.sin(r), 0, Math.cos(r));
            }
            dirAngle = Double.NaN;
            return Vec3.ZERO;
        }
        double yaw = Math.toRadians(player.getYRot());
        double sin = Math.sin(yaw), cos = Math.cos(yaw);
        double mx = -sin * forward - cos * left;
        double mz =  cos * forward - sin * left;
        double len = Math.sqrt(mx * mx + mz * mz);
        if (len < 1e-4) return Vec3.ZERO;
        double raw = Math.toDegrees(Math.atan2(mx, mz));
        if (Double.isNaN(dirAngle) || Math.abs(Mth.wrapDegrees(raw - dirAngle)) > 35.0) {
            dirAngle = raw;
        }
        // Quantize to the nearest 45° compass direction — straight, stable lines.
        double snapped = Math.round(dirAngle / 45.0) * 45.0;
        double r = Math.toRadians(snapped);
        return new Vec3(Math.sin(r), 0, Math.cos(r));
    }

    /** Smoothly steers the camera toward the block being placed while
     *  bridging, and returns it to the pre-bridge view when the bridge ends
     *  (legit look-down, place, look-up). Never snaps, never pulls sideways
     *  more than a small amount; AimAssist takes precedence when enabled. */
    private void smoothLook(LocalPlayer player, BlockPos targetPos, boolean active) {
        if (active) {
            // AimAssist owns the camera — never fight it while bridging.
            if (QynlClient.getInstance().getModuleManager().isEnabled("AimAssist")) {
                lookEngaged = false;
                return;
            }
            if (!lookEngaged) {
                lookEngaged = true;
                restoreYaw = player.getYRot();
                restorePitch = player.getXRot();
            }
            Vec3 eye = player.getEyePosition();
            Vec3 d = Vec3.atCenterOf(targetPos).subtract(eye);
            double dist = d.length();
            if (dist > 0.01) {
                float yawTo = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
                float pitchTo = (float) Math.toDegrees(Math.asin(-d.y / dist));
                float dy = Mth.wrapDegrees(yawTo - player.getYRot());
                if (Math.abs(dy) < 30.0F) {
                    player.setYRot(player.getYRot() + Mth.clamp(dy, -8.0F, 8.0F));
                }
                float dp = Mth.clamp(pitchTo - player.getXRot(), -90.0F, 0.0F);
                if (dp < 0.0F) {
                    player.setXRot(player.getXRot() + Math.max(dp, -8.0F));
                }
                player.yHeadRot = player.getYRot();
                player.yHeadRotO = player.getYRot();
            }
            return;
        }
        if (!lookEngaged) return;
        // AimAssist owns the camera — never fight it on the way back.
        if (QynlClient.getInstance().getModuleManager().isEnabled("AimAssist")) {
            lookEngaged = false;
            return;
        }
        float dy = Mth.wrapDegrees(restoreYaw - player.getYRot());
        float dp = restorePitch - player.getXRot();
        player.setYRot(player.getYRot() + Mth.clamp(dy, -10.0F, 10.0F));
        player.setXRot(player.getXRot() + Mth.clamp(dp, -10.0F, 10.0F));
        player.yHeadRot = player.getYRot();
        if (Math.abs(dy) < 0.5F && Math.abs(dp) < 0.5F) {
            lookEngaged = false;
        }
    }

    /** Finds a solid block to place the target block against. */
    private BlockPos findSupport(Minecraft mc, BlockPos target) {
        if (isSolid(mc, target.below())) {
            return target.below();
        }
        // The block the player stands on — the last placed block when bridging.
        BlockPos feet = mc.player.getBlockPosBelowThatAffectsMyMovement();
        if (isSolid(mc, feet) && !feet.equals(target)) {
            return feet;
        }
        // Finally any solid horizontal neighbour (hole walls etc.).
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos n = target.relative(dir);
            if (isSolid(mc, n) && !n.equals(feet)) {
                return n;
            }
        }
        return null;
    }

    private boolean isSolid(Minecraft mc, BlockPos pos) {
        return !mc.level.getBlockState(pos).getCollisionShape(mc.level, pos).isEmpty();
    }

    /** Finds a placeable block item in the hotbar, preferring the selected slot. */
    private int findBlockSlot(Inventory inventory) {
        if (isPlaceable(inventory.getItem(inventory.selected))) {
            return inventory.selected;
        }
        for (int i = 0; i < 9; i++) {
            if (i != inventory.selected && isPlaceable(inventory.getItem(i))) {
                return i;
            }
        }
        return -1;
    }

    private boolean isPlaceable(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem item)) {
            return false;
        }
        return item.getBlock() != Blocks.AIR;
    }

    private void releaseSneak(Minecraft mc) {
        if (forcedSneak && mc.player != null) {
            mc.player.setShiftKeyDown(false);
        }
        forcedSneak = false;
    }

    @Override
    public void onDisable() {
        releaseSneak(Minecraft.getInstance());
        cooldown = 0;
        dirAngle = Double.NaN;
        lookEngaged = false;
    }

    @Override
    public void onWorldChange(Minecraft mc) {
        releaseSneak(mc);
        cooldown = 0;
        dirAngle = Double.NaN;
        lookEngaged = false;
    }

    @Override
    public void onDisconnect(Minecraft mc) {
        releaseSneak(mc);
        cooldown = 0;
        dirAngle = Double.NaN;
        lookEngaged = false;
    }
}
