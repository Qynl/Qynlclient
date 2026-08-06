package com.qynl.client.module.modules;

import com.qynl.client.QynlClient;
import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import com.qynl.client.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.lwjgl.glfw.GLFW;

/**
 * ReachAssist — lets you hit mobs and reach blocks a little bit further away
 * than normal. The extra reach is not a fixed value: it fluctuates slightly
 * over time (a small random walk), so it never looks like a perfectly constant,
 * mechanical extension. Checked by {@link com.qynl.client.mixin.PlayerReachMixin}.
 */
public class ReachAssistModule extends Module {
	private static final RandomSource RANDOM = RandomSource.create();
	private static double currentBonus = 0.8;
	private int walkTimer = 0;

	public ReachAssistModule() {
		super("ReachAssist",
				"Extends your reach a little, with a natural slight fluctuation so hits look normal.",
				Category.ASSIST);
		bindKey(GLFW.GLFW_KEY_I);
	}

	@Override
	public void onTick(Minecraft client) {
		if (--walkTimer <= 0) {
			walkTimer = 4 + RANDOM.nextInt(4);
			// Capped so that base reach (3.0) + bonus stays under the ~4.0 blocks
			// that a vanilla server accepts for attacks — otherwise distant hits
			// would be silently rejected on multiplayer.
			currentBonus = Mth.clamp(currentBonus + (RANDOM.nextDouble() - 0.5) * 0.2, 0.5, 0.95);
		}
	}

	/** The current reach extension in blocks, fluctuating between ~0.5 and ~0.95. */
	public static double currentBonus() {
		return currentBonus;
	}

	/** Checked by PlayerReachMixin so the mixin stays decoupled from the module list. */
	public static boolean isActive() {
		Minecraft client = Minecraft.getInstance();
		if (client == null) {
			return false;
		}
		QynlClient qynl = QynlClient.getInstance();
		if (qynl == null) {
			return false;
		}
		ModuleManager modules = qynl.getModuleManager();
		return modules != null && modules.isEnabled("ReachAssist");
	}
}
