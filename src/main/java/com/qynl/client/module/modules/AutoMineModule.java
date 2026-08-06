package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

public class AutoMineModule extends Module {
	public AutoMineModule() {
		super("BreakAssist", "Keep breaking blocks while you hold the button — no need to time your clicks.", Category.ASSIST);
		bindKey(GLFW.GLFW_KEY_H);
	}

	@Override
	public void onTick(Minecraft client) {
		if (client.player == null || client.level == null || client.gameMode == null) {
			return;
		}
		if (client.hitResult == null || client.hitResult.getType() != HitResult.Type.BLOCK) {
			return;
		}
		if (!client.options.keyAttack.isDown()) {
			return;
		}
		if (client.gameMode.isDestroying()) {
			return;
		}

		BlockHitResult hit = (BlockHitResult) client.hitResult;
		BlockPos pos = hit.getBlockPos();
		Direction side = hit.getDirection();
		if (!client.level.isEmptyBlock(pos)) {
			client.gameMode.continueDestroyBlock(pos, side);
		}
	}
}
