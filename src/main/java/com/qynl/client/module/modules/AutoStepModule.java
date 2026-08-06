package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import com.qynl.client.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * AutoStep — when you walk into a one-block ledge, QynlClient jumps for
 * you. Climbing stairs, hills and chest-high ledges becomes effortless,
 * so one missed jump never stops you from getting somewhere.
 */
public class AutoStepModule extends Module {
	public AutoStepModule() {
		super("AutoStep", "Automatically climbs ledges while you walk.",
				Category.ASSIST);
		bindKey(GLFW.GLFW_KEY_F6);
		addSetting(Setting.options("height", "Height", "1.0", "1.0", "1.25"));
	}

	@Override
	public void onTick(Minecraft client) {
		if (client.player == null || client.level == null) {
			return;
		}
		var player = client.player;
		if (!player.onGround() || player.isCrouching() || player.isPassenger()
				|| player.isInWater() || player.isSpectator() || player.isUsingItem()) {
			return;
		}
		if (player.input.forwardImpulse <= 0.0F) {
			return;
		}

		Vec3 look = player.getLookAngle();
		double len = Math.sqrt(look.x * look.x + look.z * look.z);
		if (len < 0.01) {
			return;
		}
		double dx = look.x / len;
		double dz = look.z / len;
		BlockPos ahead = player.blockPosition().offset(
				(int) Math.round(dx * 1.5), 0, (int) Math.round(dz * 1.5));
		BlockState state = client.level.getBlockState(ahead);
		// Check the step height: solid at foot level, open air above.
		if (state.getCollisionShape(client.level, ahead).isEmpty()) {
			return;
		}
		if (!client.level.getBlockState(ahead.above()).isAir()) {
			return;
		}
		// 1.25 mode: also check one block higher.
		if ("1.25".equals(getStringSetting("height"))) {
			BlockPos higher = ahead.above();
			BlockState higherState = client.level.getBlockState(higher);
			if (!higherState.getCollisionShape(client.level, higher).isEmpty()
					|| !client.level.getBlockState(higher.above()).isAir()) {
				return;
			}
		}
		player.input.jumping = true;
	}
}
