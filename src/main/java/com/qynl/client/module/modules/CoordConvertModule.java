package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * CoordConvert — shows the matching Nether/Overworld coordinates.
 * No more mental division by eight when you are trying to find your
 * portal: the converted coords are always on your HUD.
 */
public class CoordConvertModule extends Module {
	public CoordConvertModule() {
		super("CoordConvert", "Shows the matching Nether/Overworld coordinates on your HUD.",
				Category.INFO);
	}

	/** HUD line, or empty when no conversion applies (e.g. The End). */
	public String getInfo(Minecraft client) {
		if (client.player == null || client.level == null) {
			return "";
		}
		ResourceKey<Level> dim = client.level.dimension();
		var pos = client.player.blockPosition();
		if (dim == Level.OVERWORLD) {
			return "Nether " + Math.floorDiv(pos.getX(), 8) + " " + Math.floorDiv(pos.getZ(), 8);
		}
		if (dim == Level.NETHER) {
			return "Overworld " + (pos.getX() * 8) + " " + (pos.getZ() * 8);
		}
		return "";
	}
}
