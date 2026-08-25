package com.qynl.client.module.modules;

import com.qynl.client.QynlClient;
import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import com.qynl.client.module.ModuleManager;
import com.qynl.client.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * ReachAssist — extends your reach with natural fluctuation.
 *
 * <p>Modes:</p>
 * <ul>
 *   <li><b>Subtle</b> — 0.25–0.55 extra reach.</li>
 *   <li><b>Normal</b> — 0.40–0.90 extra reach.</li>
 *   <li><b>Aggressive</b> — 0.65–1.15 extra reach.</li>
 *   <li><b>Silent</b> (Pack-Choke) — only extends when closing on a target
 *       from the ground, never mid-air or while sprinting.</li>
 * </ul>
 */
public class ReachAssistModule extends Module {
	private static final RandomSource RANDOM = RandomSource.create();
	private static double currentBonus = 0.7;
	private static double minBonus = 0.4;
	private static double maxBonus = 0.9;
	private int walkTimer = 0;

	public ReachAssistModule() {
		super("ReachAssist",
				"Extends your reach with natural fluctuation. Silent Pack-Choke mode for ghost servers.",
				Category.COMBAT);
		bindKey(GLFW.GLFW_KEY_I);
		addSetting(Setting.options("mode", "Mode", "Normal", "Subtle", "Normal", "Aggressive", "Silent"));
		addSetting(Setting.options("fluctuation", "Fluctuation", "Medium", "Low", "Medium", "High"));
	}

	@Override
	public void onEnable() {
		applyModeBounds();
	}

	@Override
	public void onTick(Minecraft client) {
		applyModeBounds();

		// Silent Pack-Choke: only extend reach when on ground and closing on target
		if ("Silent".equals(getStringSetting("mode"))) {
			if (client.player == null) return;
			if (!client.player.onGround()) {
				currentBonus = 0;
				return;
			}
			if (client.player.isSprinting()) {
				currentBonus = 0;
				return;
			}
			// Check if moving toward target
			Vec3 delta = client.player.getDeltaMovement();
			double speed = delta.x * delta.x + delta.z * delta.z;
			if (speed < 0.001) {
				currentBonus = 0;
				return;
			}
			// Grant small bonus when approaching on ground
			currentBonus = Mth.clamp(currentBonus + (RANDOM.nextDouble() - 0.5) * 0.1, 0.2, 0.5);
			return;
		}

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
			case "Silent" -> { minBonus = 0; maxBonus = 0.5; }
			default -> { minBonus = 0.40; maxBonus = 0.90; } // Normal
		}
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