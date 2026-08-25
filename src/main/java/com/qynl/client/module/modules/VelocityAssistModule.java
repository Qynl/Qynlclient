package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import com.qynl.client.module.Setting;
import net.minecraft.util.RandomSource;
import org.lwjgl.glfw.GLFW;

/**
 * VelocityAssist — softens knockback by a percentage with per-hit chance.
 * Not every hit is softened, which mimics real connection jitter.
 */
public class VelocityAssistModule extends Module {
	private static VelocityAssistModule instance;
	private static final RandomSource RANDOM = RandomSource.create();

	public VelocityAssistModule() {
		super("VelocityAssist",
				"Softens knockback by a percentage with per-hit chance — some hits take full KB, like real jitter.",
				Category.COMBAT);
		instance = this;
		bindKey(GLFW.GLFW_KEY_H);
		addSetting(Setting.range("horizontal", "Horizontal reduce", 45.0, 0, 90, 5, "%"));
		addSetting(Setting.range("vertical", "Vertical reduce", 20.0, 0, 90, 5, "%"));
		addSetting(Setting.range("chance", "Per-hit chance", 75.0, 40, 100, 5, "%"));
	}

	public static VelocityAssistModule getInstance() {
		return instance;
	}

	public static boolean isActive() {
		return instance != null && instance.isEnabled();
	}

	/**
	 * Multiplier (0..1) applied to horizontal knockback.
	 * Per-hit chance: some hits take full knockback.
	 */
	public double horizontalFactor() {
		double chance = getDoubleSetting("chance") / 100.0;
		if (RANDOM.nextDouble() > chance) return 1.0; // full knockback this hit
		return 1.0 - getDoubleSetting("horizontal") / 100.0 * (0.92 + RANDOM.nextDouble() * 0.16);
	}

	/** Multiplier (0..1) applied to vertical knockback. */
	public double verticalFactor() {
		double chance = getDoubleSetting("chance") / 100.0;
		if (RANDOM.nextDouble() > chance) return 1.0;
		return 1.0 - getDoubleSetting("vertical") / 100.0 * (0.92 + RANDOM.nextDouble() * 0.16);
	}
}