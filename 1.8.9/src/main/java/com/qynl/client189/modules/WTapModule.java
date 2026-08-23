package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import com.qynl.client189.Setting;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.input.Keyboard;

import java.util.Random;

/**
 * WTap — the classic 1.8 combo technique, automated. Right after you land
 * a hit, the forward key is released for one or two ticks (the "tap"),
 * which resets your sprint. The server sees your sprint stop and restart
 * around every hit — exactly like a real W-tap player — which gives you
 * the knockback and combo advantage without any impossible movement.
 *
 * <p>Hooked via {@code WTapMixin} on {@code MinecraftClient.doAttack}, so
 * it fires on every real attack, including AutoClicker's.</p>
 */
public class WTapModule extends Module {
    private static WTapModule instance;
    private static final Random RANDOM = new Random();
    private int tapTicks = 0;

    public WTapModule() {
        super("WTap", "Taps W after each hit to reset sprint — extra knockback on the enemy.", Category.COMBAT);
        instance = this;
        bindKey(Keyboard.KEY_NONE);
        addSetting(Setting.range("delay",  "Delay",  1.0,  1,  3,  1, "t"));
        addSetting(Setting.range("chance", "Chance", 85.0, 0, 100, 5, "%"));
    }

    public static WTapModule getInstance() { return instance; }

    /** Called from the attack mixin right before a hit executes. */
    public static void onAttack(MinecraftClient client) {
        if (instance == null || !instance.isEnabled()) return;
        if (client.player == null || client.world == null) return;
        // A real W-tap only makes sense while sprinting and moving forward —
        // never tap while standing still or walking.
        if (!client.player.isSprinting() || client.player.input.movementForward <= 0.0F) return;
        // Humans don't W-tap on every single hit.
        if (RANDOM.nextDouble() * 100.0 >= instance.getDoubleSetting("chance")) return;
        int delay = (int) instance.getDoubleSetting("delay");
        if (delay > 1 && RANDOM.nextBoolean()) delay--; // humanize ±1 tick
        instance.tapTicks = delay;
        // Sprint stops with the tap; it resumes naturally when forward is held.
        client.player.setSprinting(false);
    }

    /** True while the forward input should be released (Input mixin). */
    public static boolean isTapping() {
        return instance != null && instance.isEnabled() && instance.tapTicks > 0;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (tapTicks > 0) {
            tapTicks--;
        }
    }

    @Override
    public void onDisable() {
        tapTicks = 0;
    }
}
