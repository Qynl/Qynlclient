package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import com.qynl.client189.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.ClientPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import org.lwjgl.input.Keyboard;

/**
 * KeepSprint — in vanilla 1.8.9, landing a hit instantly ends your sprint,
 * which breaks combo momentum. KeepSprint re-enables sprint immediately
 * after each attack so your speed never dips mid-fight. Purely client-side
 * state: the server only ever sees a normal sprinting player.
 */
public class KeepSprintModule extends Module {
    /** Ticks after a swing during which sprint is re-asserted. */
    private int graceTicks = 0;

    public KeepSprintModule() {
        super("KeepSprint", "Keeps your sprint through attacks — no speed dip mid-combo.",
                Category.ASSIST);
        bindKey(Keyboard.KEY_F10);
        addSetting(Setting.range("grace", "Grace", 4.0, 1, 10, 1, "t"));
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            graceTicks = 0;
            return;
        }
        ClientPlayerEntity player = client.player;

        // Detect an attack: left click while the crosshair is on an entity.
        boolean attacking = client.options.keyAttack.isPressed()
                && client.result != null
                && client.result.type == BlockHitResult.Type.ENTITY;
        if (attacking) {
            graceTicks = (int) getDoubleSetting("grace");
        }

        if (graceTicks > 0) {
            graceTicks--;
            // Same vanilla sprint conditions, just re-asserted after a hit.
            boolean moving = player.input.movementForward > 0.0F;
            boolean canSprint = player.getHungerManager().getFoodLevel() > 6
                    && !player.isSneaking()
                    && !player.isUsingItem()
                    && !player.hasVehicle();
            if (moving && canSprint) {
                player.setSprinting(true);
            }
        }
    }
}
