package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import com.qynl.client189.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import org.lwjgl.input.Keyboard;

import java.util.Random;

/**
 * CritAssist — silent, packet-based critical hits for 1.8.9.
 *
 * <p>In 1.8.9, critical hits occur when the player is airborne
 * (onGround = false) and not in water/on a ladder/etc. Instead of
 * jumping, this module spoofs {@code onGround = false} in outgoing
 * movement packets so the server grants crits on every swing — your
 * camera and movement stay completely natural.</p>
 *
 * <p>Hooked via {@link com.qynl.client189.mixin.ClientPlayerSendMovementMixin}.</p>
 */
public class CritAssistModule extends Module {
    private static CritAssistModule instance;
    private static final Random RANDOM = new Random();

    /** Temporary storage for the mixin to save the real onGround value. */
    private static Boolean originalGround = null;

    public CritAssistModule() {
        super("CritAssist", "Silent crits — spoofs onGround in movement packets. No jumping needed.", Category.ASSIST);
        instance = this;
        bindKey(Keyboard.KEY_C);
        addSetting(Setting.options("mode",    "Mode",     "Always", "Always", "Sprinting", "Moving"));
        addSetting(Setting.range("chance",    "Chance",   100.0, 50, 100, 5, "%"));
        addSetting(Setting.range("minHp",     "Min HP",    0.0,  0, 40, 2, "hp"));
    }

    // ── static API for the mixin ─────────────────────────────────

    public static CritAssistModule getInstance() { return instance; }
    public static boolean isActive() { return instance != null && instance.isEnabled(); }

    public static boolean shouldSpoof(MinecraftClient client) {
        if (instance == null || !instance.isEnabled()) return false;
        return instance.checkShouldSpoof(client);
    }

    public static void captureOriginalGround(boolean current) {
        originalGround = current;
    }

    // ── instance logic ───────────────────────────────────────────

    private boolean checkShouldSpoof(MinecraftClient client) {
        if (client.player == null || client.world == null) return false;
        if (!client.player.isAlive() || client.currentScreen != null) return false;

        // Must be attacking
        if (!client.options.keyAttack.isPressed()) return false;

        // Must be on ground (spoofing is pointless if already airborne)
        if (!client.player.onGround) return false;		// Min HP check — don't waste crits on nearly-dead enemies.
        // 1.8.9 MinecraftClient has a public targetedEntity field holding
        // the entity under the crosshair while attacking.
        double minHp = getDoubleSetting("minHp");
        if (minHp > 0 && client.targetedEntity instanceof LivingEntity) {
            LivingEntity targeted = (LivingEntity) client.targetedEntity;
            if (targeted.getHealth() < minHp) return false;
        }

        // Mode filters
        String mode = getStringSetting("mode");
        if ("Sprinting".equals(mode) && !client.player.isSprinting()) return false;
        if ("Moving".equals(mode)) {
            double dx = client.player.x - client.player.prevX;
            double dz = client.player.z - client.player.prevZ;
            if (dx * dx + dz * dz < 0.0001) return false;
        }

        // Chance check — adds randomness so not every swing is a crit
        double chance = getDoubleSetting("chance") / 100.0;
        if (chance < 1.0 && RANDOM.nextDouble() > chance) return false;

        return true;
    }

    @Override
    public void onDisable() {
        originalGround = null;
    }
}
