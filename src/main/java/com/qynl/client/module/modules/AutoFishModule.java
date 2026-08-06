package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class AutoFishModule extends Module {
	private static final int WAITING = 0;
	private static final int REELING = 1;
	private static final int RECAST_COOLDOWN = 2;

	private int state = WAITING;
	private int timer = 0;
	private int waterTicks = 0;

	public AutoFishModule() {
		super("AutoFish", "Reel in automatically when a fish bites, then re-cast for you.", Category.ASSIST);
		bindKey(GLFW.GLFW_KEY_R);
	}

	@Override
	public void onTick(Minecraft client) {
		if (client.player == null || client.level == null || client.screen != null) {
			return;
		}
		LocalPlayer player = client.player;
		ItemStack held = player.getMainHandItem();
		if (!(held.getItem() instanceof FishingRodItem) || player.isUsingItem()) {
			reset();
			return;
		}

		if (state == REELING) {
			if (--timer <= 0) {
				// Re-cast the rod after the catch has been pulled in.
				client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
				state = RECAST_COOLDOWN;
				timer = 12;
			}
			return;
		}
		if (state == RECAST_COOLDOWN) {
			if (--timer <= 0) {
				state = WAITING;
			}
			return;
		}

		FishingHook hook = findHook(client, player);
		if (hook == null) {
			waterTicks = 0;
			return;
		}
		if (hook.isInWater()) {
			waterTicks++;
			// A bite makes the bobber plunge underwater.
			if (waterTicks > 20 && hook.getDeltaMovement().y < -0.05) {
				client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
				state = REELING;
				timer = 6;
			}
		} else {
			waterTicks = 0;
		}
	}

	private FishingHook findHook(Minecraft client, LocalPlayer player) {
		List<Entity> entities = client.level.getEntities(player, player.getBoundingBox().inflate(8.0),
				e -> e instanceof FishingHook);
		for (Entity entity : entities) {
			if (entity instanceof FishingHook hook && hook.getOwner() == player) {
				return hook;
			}
		}
		return null;
	}

	private void reset() {
		state = WAITING;
		timer = 0;
		waterTicks = 0;
	}
}
