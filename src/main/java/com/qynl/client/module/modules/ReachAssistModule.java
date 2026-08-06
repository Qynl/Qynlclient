package com.qynl.client.module.modules;

import com.qynl.client.QynlClient;
import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import com.qynl.client.module.ModuleManager;
import com.qynl.client.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.lwjgl.glfw.GLFW;

/**
 * ReachAssist — extends your reach so you can hit mobs and interact with
 * blocks a little further away than normal.
 *
 * <p>The extension is not a fixed value: it fluctuates slightly using a
 * random walk, so it never looks like a perfectly constant, mechanical
 * reach hack. Server anti-cheat systems check for consistent, machine-like
 * reach patterns — this deliberately varies.</p>
 *
 * <p>Settings:</p>
 * <ul>
 *   <li><b>Mode</b> — Subtle (0.3–0.6 extra), Normal (0.5–0.95 extra),
 *       or Aggressive (0.7–1.2 extra, riskier).</li>
 *   <li><b>Fluctuation</b> — how much it varies (Low/Med/High). Higher
 *       is more natural but less consistent.</li>
 * </ul>
 */
public class ReachAssistModule extends Module {
	private static final RandomSource RANDOM = RandomSource.create();
	private static double currentBonus = 0.7;
	private static double minBonus = 0.4;
	private static double maxBonus = 0.9;
	private int walkTimer = 0;
	private int fluctuationSteps = 4;

	public ReachAssistModule() {
		super("ReachAssist",
				"Extends your reach with natural fluctuation — configurable range so anti-cheat sees a normal player.",
				Category.ASSIST);
		bindKey(GLFW.GLFW_KEY_I);
		addSetting(Setting.options("mode", "Mode", "Normal", "Subtle", "Normal", "Aggressive"));
		addSetting(Setting.options("fluctuation", "Fluctuation", "Medium", "Low", "Medium", "High"));
	}

	@Override
	public void onEnable() {
		applyModeBounds();
	}

	@Override
	public void onTick(Minecraft client) {
		applyModeBounds();

		int stepCount = switch (getStringSetting("fluctuation")) {
			case "Low" -> 6;
			case "High" -> 3;
			default -> 4;
		};

		if (--walkTimer <= 0) {
			walkTimer = stepCount + RANDOM.nextInt(stepCount);
			double fluctuation = switch (getStringSetting("fluctuation")) {
				case "Low" -> 0.08;
				case "High" -> 0.25;
				default -> 0.15;
			};
			currentBonus = Mth.clamp(
					currentBonus + (RANDOM.nextDouble() - 0.5) * fluctuation * 2.0,
					minBonus, maxBonus);
		}
	}

	private void applyModeBounds() {
		switch (getStringSetting("mode")) {
			case "Subtle" -> { minBonus = 0.25; maxBonus = 0.55; }
			case "Aggressive" -> { minBonus = 0.65; maxBonus = 1.15; }
			default -> { minBonus = 0.40; maxBonus = 0.90; } // Normal
		}
		// Clamp current bonus to new bounds.
		currentBonus = Mth.clamp(currentBonus, minBonus, maxBonus);
	}

	public static double currentBonus() {
		return currentBonus;
	}

	public static boolean isActive() {
		Minecraft client = Minecraft.getInstance();
		if (client == null) return false;
		QynlClient qynl = QynlClient.getInstance();
		if (qynl == null) return false;
		ModuleManager modules = qynl.getModuleManager();
		return modules != null && modules.isEnabled("ReachAssist");
	}
}
