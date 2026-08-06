package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import com.qynl.client.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * BowAssist — when you draw a bow, this automatically releases the arrow
 * at the right moment (fully charged, or at your chosen charge %). You
 * only need to right-click once and the client holds and releases for you.
 *
 * <p>This is a critical assist for players with limited hand strength or
 * motor control who cannot steadily hold down the right mouse button: the
 * client handles the drawn phase while the player just points.</p>
 *
 * <p>The release timing includes a tiny human delay (80–200 ms) so it
 * never looks like a machine-perfect release.</p>
 */
public class BowAssistModule extends Module {
	private static final RandomSource RANDOM = RandomSource.create();

	private enum State { IDLE, DRAWING, HOLDING, RELEASING, COOLDOWN }

	private State state = State.IDLE;
	private int timer = 0;
	private boolean forcingUse = false;

	public BowAssistModule() {
		super("BowAssist",
				"Draw and release your bow automatically at full charge — for players who can't hold right-click steadily.",
				Category.ASSIST);
		bindKey(GLFW.GLFW_KEY_F11);
		addSetting(Setting.range("chargePct", "Release at", 100.0, 60, 100, 5, "%"));
		addSetting(Setting.range("releaseDelay", "Release delay", 120.0, 50, 300, 10, "ms"));
	}

	@Override
	public void onTick(Minecraft client) {
		if (client.player == null || client.level == null || client.gameMode == null) {
			reset(client);
			return;
		}
		if (client.player.isDeadOrDying() || client.screen != null) {
			reset(client);
			return;
		}

		ItemStack held = client.player.getMainHandItem();
		boolean hasBow = held.getItem() instanceof BowItem;
		boolean hasCrossbow = held.getItem() instanceof CrossbowItem;

		if (!hasBow && !hasCrossbow) {
			reset(client);
			return;
		}

		// Crossbow: just fire when fully charged (charged crossbows fire on use).
		if (hasCrossbow) {
			if (CrossbowItem.isCharged(held)) {
				// Crossbow is loaded — fire it once.
				if (state != State.RELEASING) {
					state = State.RELEASING;
					timer = (int) Math.round(getDoubleSetting("releaseDelay") / 50.0);
					if (timer < 1) timer = 1;
				}
				if (state == State.RELEASING) {
					if (timer > 0) {
						timer--;
						return;
					}
					client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
					reset(client);
				}
				return;
			}
			// Crossbow not charged → start charging by holding use.
			if (state == State.IDLE && !client.player.isUsingItem()) {
				client.options.keyUse.setDown(true);
				forcingUse = true;
				state = State.DRAWING;
			}
			return;
		}

		// --- Bow logic ---
		int useTicks = client.player.getTicksUsingItem();
		float chargeTarget = (float) getDoubleSetting("chargePct") / 100.0F;

		switch (state) {
			case IDLE -> {
				// Player clicked attack (or we auto-start): begin drawing the bow.
				if (client.options.keyUse.isDown() && !client.player.isUsingItem()) {
					state = State.DRAWING;
				}
				// Auto-start: if player isn't triggering it but has a bow out,
				// we auto-hold use so they can just point and shoot.
				if (!client.player.isUsingItem() && !client.options.keyUse.isDown()) {
					client.options.keyUse.setDown(true);
					forcingUse = true;
					state = State.DRAWING;
				}
			}
			case DRAWING -> {
				// Wait for the bow to reach the desired charge.
				if (!client.player.isUsingItem()) {
					// Use hasn't started yet — keep trying.
					return;
				}
				float currentCharge = BowItem.getPowerForTime(useTicks);
				if (currentCharge >= chargeTarget) {
					// Bow is charged enough — add human release delay.
					state = State.HOLDING;
					timer = (int) Math.round(getDoubleSetting("releaseDelay") / 50.0)
							+ RANDOM.nextInt(2);
					if (timer < 1) timer = 1;
				}
			}
			case HOLDING -> {
				if (!client.player.isUsingItem()) {
					// Player released manually — respect that.
					reset(client);
					return;
				}
				if (timer > 0) {
					timer--;
					return;
				}
				// Release the arrow!
				releaseUse(client);
				state = State.COOLDOWN;
				timer = 6 + RANDOM.nextInt(4); // short cooldown before next draw
			}
			case COOLDOWN -> {
				if (timer > 0) {
					timer--;
					return;
				}
				state = State.IDLE;
			}
		}
	}

	private void releaseUse(Minecraft client) {
		if (client.options != null) {
			client.options.keyUse.setDown(false);
		}
		forcingUse = false;
	}

	private void reset(Minecraft client) {
		releaseUse(client);
		state = State.IDLE;
		timer = 0;
	}

	@Override
	public void onDisable() {
		reset(Minecraft.getInstance());
	}
}
