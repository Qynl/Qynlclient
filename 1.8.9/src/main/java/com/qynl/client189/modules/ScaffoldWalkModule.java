package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import com.qynl.client189.Setting;
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
public class ScaffoldWalkModule extends Module {
    private static final Random RANDOM = new Random();

    private int placeCooldown = 0;

    public ScaffoldWalkModule() {
        super("ScaffoldWalk", "Places a block under your feet while walking — bridge gaps and pillars without aiming down.",
                Category.ASSIST);
        bindKey(Keyboard.KEY_F9);
        addSetting(Setting.options("mode",   "Mode",    "Forward", "Forward", "Always"));
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
        if (player.isSneaking() || player.isUsingItem() || player.hasVehicle()) {
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

        // Place against the block below the target (face UP) through the
        // normal right-click path.
        BlockPos support = target.down();
        boolean placed = client.interactionManager.onRightClick(
                player, (ClientWorld) client.world, held, support, Direction.UP,
                new Vec3d(support.getX() + 0.5, support.getY() + 1.0, support.getZ() + 0.5));
        if (placed) {
            player.swingHand();
            int base = (int) getDoubleSetting("delay");
            placeCooldown = "On".equals(getStringSetting("humanize")) ? base + RANDOM.nextInt(2) : base;
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

        // Movement direction (from input yaw, which is what walking uses).
        float yawRad = (float) Math.toRadians(player.yaw);
        double lookDx = -Math.sin(yawRad);
        double lookDz = Math.cos(yawRad);
        double mx = player.x - player.prevX;
        double mz = player.z - player.prevZ;
        double speed = Math.sqrt(mx * mx + mz * mz);

        // Prefer the block ahead (movement direction, falling back to look).
        int dx, dz;
        if (speed > 0.02) {
            dx = (int) Math.signum(mx);
            dz = (int) Math.signum(mz);
        } else {
            dx = (int) Math.signum(lookDx);
            dz = (int) Math.signum(lookDz);
        }
        BlockPos ahead = feet.add(dx, 0, dz);
        if (isAir(client, ahead) && isAir(client, ahead.down())) {
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
}
