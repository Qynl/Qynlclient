package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

public class AutoToolModule extends Module {
	public AutoToolModule() {
		super("AutoTool", "Automatically pick the fastest tool for the block you are aiming at.", Category.ASSIST);
		bindKey(GLFW.GLFW_KEY_G);
	}

	@Override
	public void onTick(Minecraft client) {
		if (client.player == null || client.level == null) {
			return;
		}
		if (client.hitResult == null || client.hitResult.getType() != HitResult.Type.BLOCK) {
			return;
		}
		if (!client.options.keyAttack.isDown()) {
			return;
		}

		BlockHitResult hit = (BlockHitResult) client.hitResult;
		BlockPos pos = hit.getBlockPos();
		BlockState state = client.level.getBlockState(pos);
		if (state.isAir()) {
			return;
		}

		Inventory inventory = client.player.getInventory();
		int bestSlot = inventory.selected;
		float bestSpeed = 0.0F;
		for (int i = 0; i < 9; i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			float speed = stack.getDestroySpeed(state);
			if (speed > bestSpeed) {
				bestSpeed = speed;
				bestSlot = i;
			}
		}
		if (bestSlot != inventory.selected) {
			inventory.selected = bestSlot;
		}
	}
}
