package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

/**
 * AutoClicker — you only have to hold the attack button and it keeps
 * attacking/mining for you at a steady, fair rate. Great for players who
 * cannot click fast or for long periods.
 */
public class AutoClickerModule extends Module {
	private int clickTicks = 0;

	public AutoClickerModule() {
		super("AutoClicker", "Hold the attack button and it attacks and mines for you — no fast clicking needed.",
				Category.ASSIST);
		bindKey(GLFW.GLFW_KEY_F);
	}

	@Override
	public void onTick(Minecraft client) {
		if (client.player == null || client.level == null || client.gameMode == null) {
			return;
		}
		if (client.screen != null || client.player.isUsingItem()) {
			clickTicks = 0;
			return;
		}
		if (!client.options.keyAttack.isDown()) {
			clickTicks = 0;
			return;
		}

		clickTicks++;
		if (clickTicks < 4) { // ~4 clicks per second — steady and fair
			return;
		}
		clickTicks = 0;
		// Respect the attack cooldown so this never out-performs a normal player.
		if (client.player.getAttackStrengthScale(0.0F) < 0.6F) {
			return;
		}
		if (client.hitResult == null) {
			return;
		}

		if (client.hitResult.getType() == HitResult.Type.ENTITY) {
			Entity target = ((EntityHitResult) client.hitResult).getEntity();
			client.gameMode.attack(client.player, target);
		} else if (client.hitResult.getType() == HitResult.Type.BLOCK) {
			BlockHitResult hit = (BlockHitResult) client.hitResult;
			BlockPos pos = hit.getBlockPos();
			Direction side = hit.getDirection();
			if (client.gameMode.isDestroying()) {
				client.gameMode.continueDestroyBlock(pos, side);
			} else {
				client.gameMode.startDestroyBlock(pos, side);
			}
		}
	}
}
