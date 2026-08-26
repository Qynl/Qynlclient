package com.qynl.client.module.modules;

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

    public ScaffoldWalkModule() {
        super("ScaffoldWalk",
                "Vape-Lite bridge: input-based placement, smooth camera look, Ninja 45° diagonal with auto-sneak.",
                Category.UTILITY);
        bindKey(GLFW.GLFW_KEY_B);
        addSetting(Setting.options("mode", "Mode", "Walk", "Walk", "Ninja"));
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
        Vec3 move = moveDir(player);
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

        if (!bridging) return;

        // Legit look: while bridging (target not straight under the feet) the
        // camera glides toward the block being placed — never snaps, never
        // yanks sideways while strafing in a fight.
        boolean underFeet = Math.abs(target.getX() - feet.getX())
                + Math.abs(target.getZ() - feet.getZ()) == 0;
        if (!underFeet) {
            smoothLook(player, target);
        }

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

    /** Movement direction from keyboard input, relative to the player's yaw. */
    private Vec3 moveDir(LocalPlayer player) {
        float forward = player.input.forwardImpulse;
        float left = player.input.leftImpulse;
        if (forward == 0.0F && left == 0.0F) return Vec3.ZERO;
        double yaw = Math.toRadians(player.getYRot());
        double sin = Math.sin(yaw), cos = Math.cos(yaw);
        double mx = -sin * forward - cos * left;
        double mz =  cos * forward - sin * left;
        return new Vec3(mx, 0, mz).normalize();
    }

    /** Smoothly steers the camera toward the block being placed. */
    private void smoothLook(LocalPlayer player, BlockPos targetPos) {
        Vec3 eye = player.getEyePosition();
        Vec3 d = Vec3.atCenterOf(targetPos).subtract(eye);
        double dist = d.length();
        if (dist < 0.01) return;
        float yawTo = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitchTo = (float) Math.toDegrees(Math.asin(-d.y / dist));

        // Only steer the yaw when the place direction is roughly ahead —
        // never pull the camera sideways while strafing in a fight.
        float dy = Mth.wrapDegrees(yawTo - player.getYRot());
        if (Math.abs(dy) < 40.0F) {
            player.setYRot(player.getYRot() + dy * 0.3F);
            player.yHeadRot = player.getYRot();
        }
        // Pitch glides gently DOWN toward the block (never forced up).
        float dp = pitchTo - player.getXRot();
        if (dp < 0.0F) {
            player.setXRot(player.getXRot() + Math.max(dp, -4.0F));
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
    }

    @Override
    public void onWorldChange(Minecraft mc) {
        releaseSneak(mc);
        cooldown = 0;
    }

    @Override
    public void onDisconnect(Minecraft mc) {
        releaseSneak(mc);
        cooldown = 0;
    }
}
