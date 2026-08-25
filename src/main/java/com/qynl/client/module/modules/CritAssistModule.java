package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import com.qynl.client.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

/**
 * CritAssist — critical hits without jumping.
 *
 * <p>Two techniques are available:</p>
 *
 * <ul>
 *   <li><b>MiniJump</b> (default) — right before each attack two tiny
 *       position packets are sent: one that moves you slightly up
 *       (Y + 0.06), then one that drops you slightly down (Y + 0.01), both
 *       flagged airborne (onGround = false). The server sees a tiny fall and
 *       grants the 1.5× critical, while your real position and camera never
 *       move. Hooked by {@link com.qynl.client.mixin.CritAttackMixin} in
 *       {@code MultiPlayerGameMode.attack()}.</li>
 *   <li><b>Silent</b> — spoofs {@code onGround = false} in the normal
 *       outgoing movement packets while attacking (no extra packets). Hooked
 *       by {@link com.qynl.client.mixin.CritAssistMixin} in
 *       {@code LocalPlayer.sendPosition()}.</li>
 * </ul>
 */
public class CritAssistModule extends Module {

    /** A single-instance snapshot so the mixins can forward calls without reflection. */
    private static CritAssistModule instance;

    /** Temporary storage so the HEAD/TAIL hooks in the silent mixin can save & restore. */
    private static Boolean capturedGround = null;

    public CritAssistModule() {
        super("CritAssist",
                "Mini-jump packet crits (Y+0.06 up, Y+0.01 down) before each hit — no jumping needed.",
                Category.COMBAT);
        instance = this;
        bindKey(GLFW.GLFW_KEY_F10);
        addSetting(Setting.options("technique", "Technique", "MiniJump", "MiniJump", "Silent"));
        addSetting(Setting.range("upOffset",   "Up offset",   0.06, 0.02, 0.20, 0.01, "b"));
        addSetting(Setting.range("downOffset", "Down offset", 0.01, 0.00, 0.10, 0.01, "b"));
        addSetting(Setting.options("mode",      "Mode",       "Always", "Always", "Sprinting", "Moving"));
        addSetting(Setting.range("minHealth",   "Min HP",      0.0,    0, 40, 2, "hp"));
        addSetting(Setting.range("chance",      "Chance",     100.0,  50, 100, 5, "%"));
    }

    // ── static API for the mixins ─────────────────────────────────

    public static CritAssistModule getInstance() {
        return instance;
    }

    public static boolean isActive() {
        return instance != null && instance.isEnabled();
    }

    /**
     * Silent mode gating — the {@code sendPosition} mixin only spoofs when the
     * Silent technique is selected.
     */
    public static boolean shouldSpoof(LocalPlayer player) {
        if (instance == null || !instance.isEnabled()) return false;
        if (!"Silent".equals(instance.getStringSetting("technique"))) return false;
        return instance.checkShouldSpoof(player);
    }

    /**
     * MiniJump mode — called right before the attack executes
     * ({@code MultiPlayerGameMode.attack} HEAD). Sends the up/down position
     * packets so the server sees a tiny fall and grants a critical hit.
     */
    public static void onPreAttack(Minecraft client) {
        if (instance == null || !instance.isEnabled()) return;
        if (!"MiniJump".equals(instance.getStringSetting("technique"))) return;
        if (client.player == null || client.getConnection() == null) return;
        if (!instance.checkShouldSpoof(client.player)) return;

        double up = instance.getDoubleSetting("upOffset");
        double down = instance.getDoubleSetting("downOffset");
        LocalPlayer player = client.player;

        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();

        // 1) Slightly up — becomes airborne.
        client.getConnection().send(new ServerboundMovePlayerPacket.Pos(x, y + up, z, false));
        // 2) Slightly down — lands near the ground, still flagged airborne.
        client.getConnection().send(new ServerboundMovePlayerPacket.Pos(x, y + down, z, false));
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

    private boolean checkShouldSpoof(LocalPlayer player) {
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
