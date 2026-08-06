package com.qynl.legacy.module.modules;

import com.qynl.legacy.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;

/**
 * BlockHit — automatically blocks with your sword between attacks so you
 * deal damage AND take less damage at the same time.  This is the classic
 * PvP technique that skilled players do by muscle memory; this module
 * does it for players who cannot time their blocks manually.
 *
 * <p>How it works: while you hold a sword, right-click (block) is held for
 * you automatically.  When you press the attack button the block is
 * released for exactly one tick so the swing lands, then the block
 * resumes — every hit becomes a block-hit.
 */
public class BlockHitModule extends Module {
	private boolean justAttacked;

	public BlockHitModule() {
		super("BlockHit",
				"Auto-blocks with your sword between attacks — every swing is a block-hit.",
				Keyboard.KEY_R);
	}

	@Override
	public void onTick(MinecraftClient client) {
		if (client.player == null) return;

		ItemStack mainHand = client.player.getMainHandStack();
		if (mainHand == null || !(mainHand.getItem() instanceof SwordItem)) {
			justAttacked = false;
			if (client.options.keyUse.isPressed()) {
				client.options.keyUse.setPressed(false);
			}
			return;
		}

		// Default: hold block.
		client.options.keyUse.setPressed(true);

		if (justAttacked) {
			// We unblocked last tick — check if the player is still
			// holding attack; if not, just go back to blocking normally.
			if (!client.options.keyAttack.isPressed()) {
				justAttacked = false;
			}
		}

		if (client.options.keyAttack.wasPressed() || client.options.keyAttack.isPressed()) {
			if (!justAttacked) {
				// Unblock for this tick so the attack connects.
				client.options.keyUse.setPressed(false);
				justAttacked = true;

				// Swing the arm so the player sees the hit.
				client.player.swingHand();
			}
		} else {
			justAttacked = false;
		}
	}

	@Override
	public void onDisable() {
		justAttacked = false;
	}
}
