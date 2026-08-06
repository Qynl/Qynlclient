package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * AutoTorch — lights up dark caves for you. When the light around you drops
 * too low it places a torch from your hotbar at your feet, so you never
 * wander into darkness where mobs can hide and you can't see.
 */
public class AutoTorchModule extends Module {
	private static final int ATTEMPT_EVERY = 25; // ticks (~1.25s)
	private int timer = 0;

	public AutoTorchModule() {
		super("AutoTorch", "Places a torch automatically when it gets too dark.",
				Category.ASSIST);
		bindKey(GLFW.GLFW_KEY_F7);
	}

	@Override
	public void onTick(Minecraft client) {
		timer++;
		if (timer < ATTEMPT_EVERY) {
			return;
		}
		timer = 0;
		if (client.player == null || client.level == null || client.gameMode == null) {
			return;
		}
		if (client.screen != null || client.player.isUsingItem() || client.player.isSpectator()) {
			return;
		}
		var player = client.player;
		BlockPos pos = player.blockPosition();
		if (client.level.getMaxLocalRawBrightness(pos) > 7) {
			return; // bright enough
		}
		BlockPos below = pos.below();
		if (!client.level.getBlockState(below).isSolidRender(client.level, below)) {
			return; // nothing to stand a torch on
		}
		if (!client.level.getBlockState(pos).isAir()) {
			return;
		}

		Inventory inventory = player.getInventory();
		int slot = -1;
		for (int i = 0; i < 9; i++) {
			if (inventory.getItem(i).is(Items.TORCH)) {
				slot = i;
				break;
			}
		}
		if (slot < 0) {
			return; // no torches in the hotbar
		}
		if (inventory.selected != slot) {
			inventory.selected = slot;
		}

		BlockHitResult hit = new BlockHitResult(
				Vec3.atCenterOf(below).add(0.0, 0.5, 0.0), Direction.UP, below, false);
		player.swing(InteractionHand.MAIN_HAND);
		client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
	}
}
