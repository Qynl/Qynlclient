package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import com.qynl.client.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

/**
 * CritAssist — silent, packet-based critical hits.
 *
 * <p>Instead of jumping to trigger crits (which looks obvious and can get
 * you banned), this module spoofs {@code onGround = false} in the
 * movement packets that the server uses to determine whether a hit is
 * critical. The server sees you as airborne, grants the 1.5× crit
 * damage, but your camera stays perfectly still — no jumping required.</p>
 *
 * <p>Checked by {@link com.qynl.client.mixin.CritAssistMixin} which
 * hooks {@code LocalPlayer.sendPosition()}.</p>
 */
public class CritAssistModule extends Module {

    /** A single-instance snapshot so the mixin can forward calls without reflection. */
    private static CritAssistModule instance;

    /** Temporary storage so the HEAD/TAIL hooks in the mixin can save & restore. */
    private static Boolean capturedGround = null;

    public CritAssistModule() {
        super("CritAssist",
                "Silent crits — spoofs onGround in packets so every hit is a crit. No jumping needed.",
                Category.ASSIST);
        instance = this;
        bindKey(GLFW.GLFW_KEY_F10);
        addSetting(Setting.options("mode",      "Mode",       "Always", "Always", "Sprinting", "Moving"));
        addSetting(Setting.range("minHealth",   "Min HP",      0.0,    0, 40, 2, "hp"));
        addSetting(Setting.range("chance",      "Chance",     100.0,  50, 100, 5, "%"));
    }

    // ── static API for the mixin ─────────────────────────────────

    public static CritAssistModule getInstance() {
        return instance;
    }

    public static boolean isActive() {
        return instance != null && instance.isEnabled();
    }

    /**
     * Called by the mixin HEAD hook — decides whether this position
     * update should be spoofed.
     */
    public static boolean shouldSpoof(net.minecraft.client.player.LocalPlayer player) {
        if (instance == null || !instance.isEnabled()) return false;
        return instance.checkShouldSpoof(player);
    }

    public static void captureGround(boolean current) {
        capturedGround = current;
    }

    public static boolean hasCapturedGround() {
        return capturedGround != null;
    }

    public static boolean releaseGround() {
        boolean val = capturedGround != null ? capturedGround : true;
        capturedGround = null;
        return val;
    }

    // ── instance logic ───────────────────────────────────────────

    private boolean checkShouldSpoof(net.minecraft.client.player.LocalPlayer player) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || player == null) return false;

        // Must be attacking
        if (!client.options.keyAttack.isDown()) return false;

        // Must be aiming at a living entity
        if (client.hitResult == null || client.hitResult.getType() != HitResult.Type.ENTITY) return false;
        if (!(client.hitResult instanceof EntityHitResult ehr)) return false;
        if (!(ehr.getEntity() instanceof LivingEntity target)) return false;
        if (!target.isAlive()) return false;

        // Min health check — don't waste crits on nearly-dead mobs
        double minHp = getDoubleSetting("minHealth");
        if (minHp > 0 && target.getHealth() < minHp) return false;

        // Mode checks
        String mode = getStringSetting("mode");
        if ("Sprinting".equals(mode) && !player.isSprinting()) return false;
        if ("Moving".equals(mode)) {
            double dx = player.getX() - player.xOld;
            double dz = player.getZ() - player.zOld;
            if (dx * dx + dz * dz < 0.0001) return false;
        }

        // Chance check — random variation so not every hit is a crit
        double chance = getDoubleSetting("chance") / 100.0;
        if (chance < 1.0) {
            if (Math.random() > chance) return false;
        }

        // Player must actually be on the ground for spoofing to make sense
        // (spoofing airborne when you're already airborne is pointless and might flag)
        if (!player.onGround()) return false;

        return true;
    }

    @Override
    public void onDisable() {
        capturedGround = null;
    }
}
