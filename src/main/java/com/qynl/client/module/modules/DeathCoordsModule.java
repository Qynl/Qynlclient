package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * DeathCoords — remembers where you died and shows it on the HUD.
 * No more wandering around helplessly trying to remember which cave
 * you fell into: the location is announced in chat and kept on screen.
 */
public class DeathCoordsModule extends Module {
	private BlockPos lastDeath = null;
	private ResourceKey<Level> lastDimension = null;
	private boolean wasDead = false;

	public DeathCoordsModule() {
		super("DeathCoords", "Remembers where you died and shows the location on your HUD.",
				Category.ASSIST);
	}

	@Override
	public void onTick(Minecraft client) {
		if (client.player == null || client.level == null) {
			return;
		}
		if (client.player.isDeadOrDying()) {
			if (!wasDead) {
				wasDead = true;
				lastDeath = client.player.blockPosition();
				lastDimension = client.level.dimension();
				BlockPos pos = lastDeath;
				client.player.sendSystemMessage(Component.literal(
						"\u00a7a[Qynl]\u00a7f You died at \u00a7e" + pos.getX() + " " + pos.getY() + " " + pos.getZ()
								+ "\u00a7f (\u00a77" + dimensionName(lastDimension) + "\u00a7f)"));
			}
		} else {
			wasDead = false;
		}
	}

	/** HUD line, or empty when there is nothing to show. */
	public String getHudLine(Minecraft client) {
		if (lastDeath == null) {
			return "";
		}
		String base = "Death " + lastDeath.getX() + " " + lastDeath.getY() + " " + lastDeath.getZ();
		if (client.level != null && client.level.dimension().equals(lastDimension)) {
			var playerPos = client.player != null ? client.player.blockPosition() : null;
			if (playerPos != null) {
				double dist = Math.sqrt(playerPos.distSqr(lastDeath));
				base += " (\u00a7f" + (int) dist + "m\u00a7r)";
			}
		}
		return base + " [" + dimensionName(lastDimension) + "]";
	}

	private String dimensionName(ResourceKey<Level> key) {
		if (key == null) {
			return "?";
		}
		return switch (key.location().getPath()) {
			case "the_nether" -> "Nether";
			case "the_end" -> "The End";
			default -> "Overworld";
		};
	}
}
