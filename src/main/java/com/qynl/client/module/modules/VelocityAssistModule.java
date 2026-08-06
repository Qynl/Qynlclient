package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import com.qynl.client.module.Setting;
import net.minecraft.util.RandomSource;
import org.lwjgl.glfw.GLFW;

/**
 * VelocityAssist — softens knockback by a percentage instead of removing it
 * completely. Fully blocking knockback looks mechanical and is exactly what
 * anti-cheat systems check for; keeping a natural, slightly varied portion of
 * the knockback helps players who cannot react in time without looking robotic.
 * Checked by {@link com.qynl.client.mixin.VelocityMixin}.
 */
public class VelocityAssistModule extends Module {
	private static VelocityAssistModule instance;
	private static final RandomSource RANDOM = RandomSource.create();

	public VelocityAssistModule() {
		super("VelocityAssist",
				"Softens knockback by a percentage (not fully blocked) so getting hit is easier to handle.",
				Category.ASSIST);
		instance = this;
		bindKey(GLFW.GLFW_KEY_H);
		addSetting(Setting.range("horizontal", "Horizontal reduce", 60.0, 0, 90, 5, "%"));
		addSetting(Setting.range("vertical", "Vertical reduce", 30.0, 0, 90, 5, "%"));
	}

	public static VelocityAssistModule getInstance() {
		return instance;
	}

	public static boolean isActive() {
		return instance != null && instance.isEnabled();
	}

	/**
	 * Multiplier (0..1) applied to horizontal knockback. A tiny random variation
	 * per hit keeps the dampening from being perfectly consistent.
	 */
	public double horizontalFactor() {
		return 1.0 - getDoubleSetting("horizontal") / 100.0 * (0.92 + RANDOM.nextDouble() * 0.16);
	}

	/** Multiplier (0..1) applied to vertical knockback. */
	public double verticalFactor() {
		return 1.0 - getDoubleSetting("vertical") / 100.0 * (0.92 + RANDOM.nextDouble() * 0.16);
	}
}
