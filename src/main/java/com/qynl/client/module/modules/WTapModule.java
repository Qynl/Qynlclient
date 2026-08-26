package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import com.qynl.client.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import org.lwjgl.glfw.GLFW;

/**
 * WTap — the classic 1.8 combo technique, automated. Right after you land
 * a hit, the forward input is released for one or two ticks (the "tap"),
 * which resets your sprint. The server sees your sprint stop and restart
 * around every hit — exactly like a real W-tap player — which gives you
 * the knockback and combo advantage without any impossible movement.
 *
 * <p>The tap is applied through the {@code InputMixin} (forward impulse is
 * zeroed while tapping), never by toggling the W {@code KeyMapping} — so
 * the player's real keyboard state is never corrupted.</p>
 */
public class WTapModule extends Module {
    private static WTapModule instance;
    private static final RandomSource RANDOM = RandomSource.create();
    private int tapTicks = 0;
    private int lastAttackCooldown = 0;
    private Entity lastAttacked;
    private long lastAttackMs = -1000;

    public WTapModule() {
        super("WTap", "Taps W after each hit to reset sprint — extra knockback on the enemy.",
                Category.COMBAT);
        instance = this;
        bindKey(GLFW.GLFW_KEY_UNKNOWN);
        addSetting(Setting.range("tapTicks", "Tap duration", 2.0, 1, 4, 1, "t"));
        addSetting(Setting.range("chance", "Chance", 85.0, 0, 100, 5, "%"));
        addSetting(Setting.range("cooldownMs", "Cooldown", 400.0, 200, 800, 50, "ms"));
    }

    public static WTapModule getInstance() { return instance; }

    /** True while the forward input should be released (InputMixin). */
    public static boolean isTapping() {
        return instance != null && instance.isEnabled() && instance.tapTicks > 0;
    }

    /**
     * Fired by the AttackMixin for EVERY {@code MultiPlayerGameMode.attack}
     * call — manual clicks, AutoClicker (Legacy included, which never builds
     * the 1.9 swing cooldown) and AimAssist's silent fire all go through it.
     * The old {@code attackStrengthScale >= 0.9} gate never fired when Legacy
     * clicks were on, which is why WTap felt dead.
     */
    public static void onPlayerAttack(Entity target) {
        if (instance != null) {
            instance.lastAttacked = target;
            instance.lastAttackMs = System.currentTimeMillis();
        }
    }

    @Override
    public void onTick(Minecraft client) {
        if (client.player == null || client.level == null) {
            tapTicks = 0;
            return;
        }
        var player = client.player;
        if (tapTicks > 0) {
            tapTicks--;
            return;
        }
        if (lastAttackCooldown > 0) {
            lastAttackCooldown--;
        }

        boolean justAttacked = lastAttacked != null
                && (System.currentTimeMillis() - lastAttackMs) < 150L;
        if (!justAttacked) {
            return;
        }
        lastAttacked = null;
        if (lastAttackCooldown > 0) {
            return;
        }

        // A real W-tap only makes sense while sprinting and moving forward —
        // never tap while standing still, walking or mid-air.
        if (!player.isSprinting() || player.input.forwardImpulse <= 0.0F || !player.onGround()) {
            return;
        }
        // Humans don't W-tap on every single hit.
        if (RANDOM.nextDouble() * 100.0 >= getDoubleSetting("chance")) {
            return;
        }

        int delay = (int) getDoubleSetting("tapTicks");
        if (delay > 1 && RANDOM.nextBoolean()) delay--; // humanize ±1 tick
        tapTicks = delay;
        lastAttackCooldown = (int) Math.round(getDoubleSetting("cooldownMs") / 50.0)
                + RANDOM.nextInt(3) - 1;
        // Sprint stops with the tap; it resumes naturally when forward is held.
        player.setSprinting(false);
    }

    @Override
    public void onDisable() {
        tapTicks = 0;
        lastAttackCooldown = 0;
        lastAttacked = null;
    }
}
