package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import com.qynl.client189.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.ClientPlayerEntity;
import org.lwjgl.input.Keyboard;

/**
 * AutoSprint — sprint automatically while you move forward, without holding
 * the sprint key. Uses the same vanilla sprint rules (food, not sneaking,
 * not using an item) so the pattern matches a normal player perfectly.
 */
public class AutoSprintModule extends Module {

    public AutoSprintModule() {
        super("AutoSprint", "Sprint automatically while you move forward — no need to hold the sprint key.",
                Category.ASSIST);
        bindKey(Keyboard.KEY_R);
        addSetting(Setting.options("mode", "Mode", "Always", "Always", "Forward"));
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return;
        }
        ClientPlayerEntity player = client.player;
        if (player.isSprinting()) {
            return;
        }
        boolean moving = "Always".equals(getStringSetting("mode"))
                ? (player.input.movementForward > 0.0F || player.input.movementSideways != 0.0F)
                : player.input.movementForward > 0.0F;
        boolean canSprint = player.getHungerManager().getFoodLevel() > 6
                && !player.isSneaking()
                && !player.isUsingItem()
                && !player.hasVehicle();
        if (moving && canSprint) {
            player.setSprinting(true);
        }
    }
}
