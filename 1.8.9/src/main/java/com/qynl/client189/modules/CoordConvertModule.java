package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import net.minecraft.client.MinecraftClient;

/**
 * CoordConvertModule for 1.8.9 — shows the matching Nether/Overworld
 * coordinates. Mirrors the 1.21.1 CoordConvert module.
 */
public class CoordConvertModule extends Module {

    public CoordConvertModule() {
        super("CoordConvert", "Shows the matching Nether/Overworld coordinates on your HUD.",
                Category.INFO);
    }

    /** HUD line, or empty when no conversion applies (e.g. The End). */
    public String getInfo(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return "";
        }
        int x = (int) Math.floor(client.player.x);
        int z = (int) Math.floor(client.player.z);
        int dim = client.world.dimension.getType();
        if (dim == 0) {
            return "Nether " + Math.floorDiv(x, 8) + " " + Math.floorDiv(z, 8);
        }
        if (dim == -1) {
            return "Overworld " + (x * 8) + " " + (z * 8);
        }
        return "";
    }
}
