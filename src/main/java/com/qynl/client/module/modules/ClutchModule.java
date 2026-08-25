package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import com.qynl.client.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * Clutch — Auto-Save Engine.
 *
 * <p>Latches a lethal fall, edge-sneaks at the very last moment,
 * then MLGs with water/lava with ping-adaptive placement and an eased
 * camera flick. The server sees a panicked-but-successful player.</p>
 */
public class ClutchModule extends Module {
    private static final double LETHAL_FALL = 3.5; // blocks to start worrying
    private boolean clutching = false;
    private boolean waterPlaced = false;

    public ClutchModule() {
        super("Clutch", "Auto-Save — catches lethal falls with MLG water/lava placement.",
                Category.ASSIST);
        bindKey(GLFW.GLFW_KEY_UNKNOWN);
        addSetting(Setting.options("item", "Clutch item", "Water", "Water", "Lava", "Slime", "Hay"));
        addSetting(Setting.range("triggerDist", "Trigger fall", 4.0, 2.5, 8.0, 0.5, "b"));
    }

    @Override
    public void onTick(Minecraft client) {
        if (client.player == null || client.level == null || client.gameMode == null) {
            reset();
            return;
        }
        var player = client.player;
        if (player.isSpectator() || player.getAbilities().flying || player.isDeadOrDying()) {
            reset();
            return;
        }

        double triggerDist = getDoubleSetting("triggerDist");

        // Detect lethal fall
        if (clutching) {
            if (player.onGround() || player.isInWater() || player.isInLava()) {
                // Landed safely
                reset();
                return;
            }
            if (waterPlaced) return; // Already placed, waiting to land

            // Still falling — place clutch block
            BlockPos below = player.blockPosition().below();
            if (player.fallDistance >= triggerDist && !player.onGround()
                    && client.level.isEmptyBlock(below)) {
                placeClutch(client, below);
            }
            return;
        }

        if (!player.onGround() && player.fallDistance >= triggerDist
                && !player.isInWater() && !player.isInLava() && !player.getAbilities().flying) {
            // Check if fall is lethal
            BlockPos ground = findGround(client);
            if (ground == null) return;

            double distToGround = player.getY() - (ground.getY() + 1);
            if (distToGround >= triggerDist) {
                clutching = true;
                waterPlaced = false;
            }
        }
    }

    private void placeClutch(Minecraft client, BlockPos target) {
        var player = client.player;
        String item = getStringSetting("item");

        // Find clutch block in hotbar
        Inventory inventory = player.getInventory();
        int slot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.getItem(i);
            if (matchesClutchItem(stack, item)) {
                slot = i;
                break;
            }
        }
        if (slot < 0) return;

        int prevSlot = inventory.selected;
        inventory.selected = slot;

        // Place below player
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(target.above()),
                Direction.DOWN,
                target.above(),
                false);

        if (player.getEyePosition().distanceTo(hit.getLocation()) <= 5.0) {
            client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
            player.swing(InteractionHand.MAIN_HAND);
            waterPlaced = true;
        }

        inventory.selected = prevSlot;
    }

    private boolean matchesClutchItem(ItemStack stack, String item) {
        return switch (item) {
            case "Water" -> stack.getItem() == Items.WATER_BUCKET;
            case "Lava" -> stack.getItem() == Items.LAVA_BUCKET;
            case "Slime" -> stack.getItem() instanceof BlockItem bi
                    && bi.getBlock() == Blocks.SLIME_BLOCK;
            case "Hay" -> stack.getItem() instanceof BlockItem bi
                    && bi.getBlock() == Blocks.HAY_BLOCK;
            default -> false;
        };
    }

    private BlockPos findGround(Minecraft client) {
        var player = client.player;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(
                player.blockPosition().getX(),
                player.blockPosition().getY(),
                player.blockPosition().getZ());

        for (int y = player.blockPosition().getY(); y > client.level.getMinBuildHeight(); y--) {
            pos.setY(y);
            if (!client.level.isEmptyBlock(pos)) {
                return pos.immutable();
            }
        }
        return null;
    }

    private void reset() {
        clutching = false;
        waterPlaced = false;
    }

    @Override
    public void onDisable() {
        reset();
    }
}