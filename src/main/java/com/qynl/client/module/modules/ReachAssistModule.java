package com.qynl.client.module.modules;

import com.qynl.client.QynlClient;
import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import com.qynl.client.module.ModuleManager;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * ReachAssist — lets you hit mobs and reach blocks a little bit further
 * away than normal. It gives players who find precise movement or quick
 * reactions hard a bit more room to aim, and stays well within what
 * vanilla servers accept.
 */
public class ReachAssistModule extends Module {
	/** Extra blocks added to both attack and block reach. */
	public static final double BONUS = 1.0;

	public ReachAssistModule() {
		super("ReachAssist", "Extends your reach, so hitting mobs and touching blocks is easier.",
				Category.ASSIST);
		bindKey(GLFW.GLFW_KEY_I);
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
