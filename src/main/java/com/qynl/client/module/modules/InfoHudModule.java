package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

import java.util.Optional;

public class InfoHudModule extends Module {
	public InfoHudModule() {
		super("InfoHUD", "Show FPS, coordinates, direction, biome, time of day and ping.", Category.INFO);
	}

	public String fps(Minecraft client) {
		return "FPS " + client.getFps();
	}

	public String coords(Minecraft client) {
		if (client.player == null) {
			return "";
		}
		var pos = client.player.blockPosition();
		return String.format("XYZ %d %d %d", pos.getX(), pos.getY(), pos.getZ());
	}

	public String direction(Minecraft client) {
		if (client.player == null) {
			return "";
		}
		Direction facing = client.player.getDirection();
		String label = switch (facing) {
			case NORTH -> "North";
			case SOUTH -> "South";
			case EAST -> "East";
			case WEST -> "West";
			case UP -> "Up";
			case DOWN -> "Down";
		};
		int yaw = Math.round(((client.player.getYRot() % 360) + 360) % 360);
		return "Facing " + label + " (" + yaw + "\u00b0)";
	}

	public String biome(Minecraft client) {
		if (client.player == null || client.level == null) {
			return "";
		}
		var holder = client.level.getBiome(client.player.blockPosition());
		Optional<ResourceKey<Biome>> key = holder.unwrapKey();
		if (key.isPresent()) {
			ResourceLocation id = key.get().location();
			return "Biome " + id.getPath();
		}
		return "";
	}

	public String time(Minecraft client) {
		if (client.level == null) {
			return "";
		}
		long dayTime = client.level.getDayTime() % 24000L;
		int hours = (int) ((dayTime / 1000 + 6) % 24);
		int minutes = (int) ((dayTime % 1000) / 1000.0 * 60);
		return String.format("Time %02d:%02d", hours, minutes);
	}

	public String ping(Minecraft client) {
		if (client.getConnection() == null || client.player == null) {
			return "Ping --";
		}
		var info = client.getConnection().getPlayerInfo(client.player.getUUID());
		return info != null ? "Ping " + info.getLatency() + "ms" : "Ping --";
	}
}
