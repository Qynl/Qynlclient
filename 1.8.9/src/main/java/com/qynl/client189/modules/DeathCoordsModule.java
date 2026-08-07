package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import net.minecraft.client.MinecraftClient;

/**
 * DeathCoordsModule for 1.8.9 — remembers where you died and shows it on the
 * HUD. Mirrors the 1.21.1 DeathCoords module.
 */
public class DeathCoordsModule extends Module {
    private int deathX, deathY, deathZ;
    private int deathDimension = Integer.MIN_VALUE;
    private boolean wasDead = false;

    public DeathCoordsModule() {
        super("DeathCoords", "Remembers where you died and shows the location on your HUD.",
                Category.ASSIST);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return;
        }
        boolean dead = client.player.getHealth() <= 0.0F;
        if (dead && !wasDead) {
            wasDead = true;
            deathX = (int) Math.floor(client.player.x);
            deathY = (int) Math.floor(client.player.y);
            deathZ = (int) Math.floor(client.player.z);
            deathDimension = client.world.dimension.getType();
            client.player.sendChatMessage("\u00a7a[Qynl]\u00a7f You died at \u00a7e"
                    + deathX + " " + deathY + " " + deathZ
                    + "\u00a7f (\u00a77" + dimensionName(deathDimension) + "\u00a7f)");
        } else if (!dead) {
            wasDead = false;
        }
    }

    /** HUD line, or empty when there is nothing to show. */
    public String getHudLine(MinecraftClient client) {
        if (deathDimension == Integer.MIN_VALUE) {
            return "";
        }
        String base = "Death " + deathX + " " + deathY + " " + deathZ;
        if (client.world != null && client.world.dimension.getType() == deathDimension && client.player != null) {
            int dx = deathX - (int) Math.floor(client.player.x);
            int dy = deathY - (int) Math.floor(client.player.y);
            int dz = deathZ - (int) Math.floor(client.player.z);
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            base += " (" + (int) dist + "m)";
        }
        return base + " [" + dimensionName(deathDimension) + "]";
    }

    private static String dimensionName(int dimension) {
        if (dimension == -1) return "Nether";
        if (dimension == 1) return "The End";
        return "Overworld";
    }
}
