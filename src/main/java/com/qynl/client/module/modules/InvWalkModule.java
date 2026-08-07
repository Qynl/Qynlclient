package com.qynl.client.module.modules;

import com.qynl.client.mixin.KeyMappingAccessor;
import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import org.lwjgl.glfw.GLFW;

/**
 * InvWalk — keep moving while your inventory or any menu is open.
 * Walking while browsing your hotbar is a small thing that makes a
 * big difference when multitasking is hard.
 *
 * <p>Key bindings do not update while a screen is open (the screen consumes
 * the keyboard events), so {@link #apply} reads the raw GLFW key state and
 * writes it into the player's {@link Input} — at the correct moment via
 * InputMixin, after vanilla {@code Input.tick} has run.</p>
 */
public class InvWalkModule extends Module {
	private static InvWalkModule instance;

	public InvWalkModule() {
		super("InvWalk", "Keep walking while your inventory or any menu is open.",
				Category.ASSIST);
		instance = this;
		bindKey(GLFW.GLFW_KEY_P);
	}

	/** Called by InputMixin while a screen is open — copies raw key state into the input. */
	public static void apply(Minecraft client, Input input) {
		if (instance == null || !instance.isEnabled()) {
			return;
		}
		if (client.player == null || client.options == null || client.screen == null) {
			return;
		}
		long handle = client.getWindow().getWindow();
		input.up = isRawDown(handle, currentKey(client.options.keyUp));
		input.down = isRawDown(handle, currentKey(client.options.keyDown));
		input.left = isRawDown(handle, currentKey(client.options.keyLeft));
		input.right = isRawDown(handle, currentKey(client.options.keyRight));
		input.jumping = isRawDown(handle, currentKey(client.options.keyJump));
		input.shiftKeyDown = isRawDown(handle, currentKey(client.options.keyShift));
		input.forwardImpulse = input.up ? 1.0F : input.down ? -1.0F : 0.0F;
		input.leftImpulse = input.left ? 1.0F : input.right ? -1.0F : 0.0F;
	}

	/** Reads the player's currently-bound key (respects rebinds in the Controls screen). */
	private static int currentKey(KeyMapping mapping) {
		return ((KeyMappingAccessor) mapping).getCurrentKey().getValue();
	}

	private static boolean isRawDown(long handle, int key) {
		return GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS;
	}
}
