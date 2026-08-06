package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public class KeystrokesModule extends Module {
	public static final int KEY_W = 0;
	public static final int KEY_A = 1;
	public static final int KEY_S = 2;
	public static final int KEY_D = 3;
	public static final int KEY_SPACE = 4;
	public static final int MOUSE_L = 5;
	public static final int MOUSE_R = 6;
	private static final int TRACKED = 7;
	private static final int HISTORY = 8;

	private final long[][] presses = new long[TRACKED][HISTORY];
	private final boolean[] prevDown = new boolean[TRACKED];

	public KeystrokesModule() {
		super("Keystrokes", "Show your keys and clicks-per-second on screen.", Category.INFO);
	}

	@Override
	public void onTick(Minecraft client) {
		for (int i = 0; i < TRACKED; i++) {
			boolean down = isKeyDown(client, i);
			if (down && !prevDown[i]) {
				recordPress(i);
			}
			prevDown[i] = down;
		}
	}

	public boolean isKeyDown(Minecraft client, int key) {
		if (client.options == null) {
			return false;
		}
		return switch (key) {
			case KEY_W -> client.options.keyUp.isDown();
			case KEY_A -> client.options.keyLeft.isDown();
			case KEY_S -> client.options.keyDown.isDown();
			case KEY_D -> client.options.keyRight.isDown();
			case KEY_SPACE -> client.options.keyJump.isDown();
			case MOUSE_L -> isMouseDown(client, GLFW.GLFW_MOUSE_BUTTON_LEFT);
			case MOUSE_R -> isMouseDown(client, GLFW.GLFW_MOUSE_BUTTON_RIGHT);
			default -> false;
		};
	}

	public int getCps(Minecraft client, int key) {
		long now = System.currentTimeMillis();
		int count = 0;
		for (long t : presses[key]) {
			if (t != 0 && now - t <= 1000) {
				count++;
			}
		}
		return count;
	}

	private void recordPress(int key) {
		long now = System.currentTimeMillis();
		long[] history = presses[key];
		int oldestIndex = 0;
		for (int i = 0; i < HISTORY; i++) {
			if (history[i] == 0) {
				history[i] = now;
				return;
			}
			if (history[i] < history[oldestIndex]) {
				oldestIndex = i;
			}
		}
		history[oldestIndex] = now;
	}

	private boolean isMouseDown(Minecraft client, int button) {
		long handle = client.getWindow().getWindow();
		return GLFW.glfwGetMouseButton(handle, button) == GLFW.GLFW_PRESS;
	}
}
