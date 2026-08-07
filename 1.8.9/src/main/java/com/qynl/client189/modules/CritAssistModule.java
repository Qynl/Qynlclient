package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import com.qynl.client189.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.hit.BlockHitResult;
import org.lwjgl.input.Keyboard;

import java.util.Random;

/**
 * CritAssist — critical hits without jumping, for 1.8.9.
 *
 * <p>In 1.8.9, a critical hit happens when the server believes the player is
 * airborne (falling, onGround = false) at the moment of the attack. Two
 * techniques are available:</p>
 *
 * <ul>
 *   <li><b>MiniJump</b> (default) — right before each attack two tiny
 *       position packets are sent: one that moves you slightly up
 *       (Y + 0.06), then one that drops you slightly down (Y + 0.01),
 *       both with onGround = false. The server sees a tiny fall and grants
 *       the 1.5× crit, while your actual position and camera never move.</li>
 *   <li><b>Silent</b> — spoofs {@code onGround = false} on the normal
 *       outgoing movement packets while attacking (no extra packets).</li>
 * </ul>
 *
 * <p>Hooked via {@code CritAttackMixin} (pre-attack packets) and
 * {@link com.qynl.client189.mixin.ClientPlayerSendMovementMixin} (silent mode).</p>
 */
public class CritAssistModule extends Module {
    private static CritAssistModule instance;
    private static final Random RANDOM = new Random();

    /** Temporary storage for the silent mode mixin to save the real onGround value. */
    private static Boolean originalGround = null;

    public CritAssistModule() {
        super("CritAssist", "Mini-jump packet crits (Y+0.06 up, Y+0.01 down) before each hit — no jumping needed.", Category.ASSIST);
        instance = this;
        bindKey(Keyboard.KEY_C);
        addSetting(Setting.options("technique", "Technique", "MiniJump", "MiniJump", "Silent"));
        addSetting(Setting.range("upOffset",   "Up offset",   0.06, 0.02, 0.20, 0.01, "b"));
        addSetting(Setting.range("downOffset", "Down offset", 0.01, 0.00, 0.10, 0.01, "b"));
        addSetting(Setting.options("mode",      "Mode",      "Always", "Always", "Sprinting", "Moving"));
        addSetting(Setting.range("chance",      "Chance",    100.0, 50, 100, 5, "%"));
        addSetting(Setting.range("minHp",       "Min HP",      0.0,  0, 40, 2, "hp"));
    }

    // ── static API for the mixins ────────────────────────────────

    public static CritAssistModule getInstance() { return instance; }
    public static boolean isActive() { return instance != null && instance.isEnabled(); }

    /** Silent mode: whether outgoing movement packets should have onGround spoofed. */
    public static boolean shouldSpoof(MinecraftClient client) {
        if (instance == null || !instance.isEnabled()) return false;
        if (!"Silent".equals(instance.getStringSetting("technique"))) return false;
        return instance.checkCommon(client, true);
    }

    /**
     * MiniJump mode: called right before the attack executes (doAttack HEAD).
     * Sends the up/down position packets so the server sees a tiny fall.
     */
    public static void onPreAttack(MinecraftClient client) {
        if (instance == null || !instance.isEnabled()) return;
        if (!"MiniJump".equals(instance.getStringSetting("technique"))) return;
        if (client.player == null || client.getNetworkHandler() == null) return;
        if (!instance.checkCommon(client, false)) return;

        double up = instance.getDoubleSetting("upOffset");
        double down = instance.getDoubleSetting("downOffset");

        double x = client.player.x;
        double y = client.player.y;
        double z = client.player.z;

        // 1) Slightly up — becomes airborne.
        client.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionOnly(x, y + up, z, false));
        // 2) Slightly down — lands near the ground, still flagged airborne.
        client.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionOnly(x, y + down, z, false));
    }

    public static void captureOriginalGround(boolean current) {
        originalGround = current;
    }

    // ── shared checks ────────────────────────────────────────────

    /**
     * Shared pre-attack conditions.
     *
     * @param requireAttackKey when true (silent mode) the attack key must be held
     */
    private boolean checkCommon(MinecraftClient client, boolean requireAttackKey) {
        if (client.player == null || client.world == null) return false;
        if (!client.player.isAlive() || client.currentScreen != null) return false;

        if (requireAttackKey && !client.options.keyAttack.isPressed()) return false;

        // Must be on ground (spoofing is pointless if already airborne)
        if (!client.player.onGround) return false;

        // Must be aiming at a living enemy under the crosshair.
        if (client.result == null || client.result.type != BlockHitResult.Type.ENTITY) return false;
        if (!(client.result.entity instanceof LivingEntity)) return false;
        LivingEntity target = (LivingEntity) client.result.entity;
        if (!target.isAlive() || target.isInvisible()) return false;

        // Min HP check — don't waste crits on nearly-dead enemies.
        double minHp = getDoubleSetting("minHp");
        if (minHp > 0 && target.getHealth() < minHp) return false;

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
