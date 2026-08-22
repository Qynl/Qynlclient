package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Material;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.input.Keyboard;

/**
 * AutoTool — automatically switches to the fastest tool for the block you
 * are mining. Same hotbar-selection path a player uses with the 1–9 keys,
 * so the server just sees normal tool switching while you dig.
 */
public class AutoToolModule extends Module {

    public AutoToolModule() {
        super("AutoTool", "Automatically pick the fastest tool for the block you are mining.",
                Category.ASSIST);
        bindKey(Keyboard.KEY_NONE);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return;
        }
        if (client.result == null || client.result.type != BlockHitResult.Type.BLOCK) {
            return;
        }
        if (!client.options.keyAttack.isPressed()) {
            return;
        }

        BlockPos pos = client.result.getBlockPos();
        BlockState state = client.world.getBlockState(pos);
        Block block = state.getBlock();
        if (block.getMaterial() == Material.AIR) {
            return;
        }

        PlayerInventory inventory = client.player.inventory;
        int bestSlot = inventory.selectedSlot;
        float handSpeed = miningSpeed(inventory.main[bestSlot], block);
        float bestSpeed = handSpeed;
        for (int i = 0; i < 9; i++) {
            float speed = miningSpeed(inventory.main[i], block);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = i;
            }
        }
        // Only switch when it actually helps (a real tool vs the bare hand).
        if (bestSlot != inventory.selectedSlot && bestSpeed > handSpeed + 0.05F) {
            inventory.selectedSlot = bestSlot;
        }
    }

    private float miningSpeed(ItemStack stack, Block block) {
        if (stack == null || stack.getItem() == null) {
            return 1.0F;
        }
        return stack.getItem().getMiningSpeedMultiplier(stack, block);
    }
}
