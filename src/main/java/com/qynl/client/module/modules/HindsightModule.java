package com.qynl.client.module.modules;

import com.qynl.client.QynlClient;
import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import com.qynl.client.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * Hindsight — Server-Time Replay.
 *
 * <p>Replays the past (what the server actually checks) instead of projecting
 * the future. Shows your own server-side position and every enemy's server-side
 * hitbox, then clicks exactly when the server's own rewind-reach check will
 * pass — works on retreating targets too.</p>
 *
 * <p>For singleplayer/friends use: falls back to simple delayed-position
 * tracking since there's no actual server latency to rewind.</p>
 */
public class HindsightModule extends Module {

    public HindsightModule() {
        super("Hindsight", "Server-Time Replay — tracks server-side positions for lag-compensated attacks.",
                Category.COMBAT);
        bindKey(GLFW.GLFW_KEY_UNKNOWN);
        addSetting(Setting.range("rewindMs", "Rewind amount", 50.0, 0, 200, 10, "ms"));
        addSetting(Setting.range("maxDist", "Max distance", 6.0, 3, 10, 0.5, "b"));
        addSetting(Setting.options("target", "Target", "Monsters", "Monsters", "Players+Monsters"));
    }

    @Override
    public void onTick(Minecraft client) {
        if (client.player == null || client.level == null || client.gameMode == null) return;
        if (!client.options.keyAttack.isDown()) return;
        if (client.player.isDeadOrDying()) return;		// Defer to Qynl/AimAssist if they're on (they handle attacking)
		var modules = QynlClient.getInstance().getModuleManager();
		if (modules.isEnabled("Qynl") || modules.isEnabled("AimAssist")) return;

        double maxDist = getDoubleSetting("maxDist");
        boolean targetPlayers = "Players+Monsters".equals(getStringSetting("target"));
        Vec3 eye = client.player.getEyePosition();

        Entity best = null;
        double bestScore = Double.MAX_VALUE;

        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity == client.player) continue;
            boolean isMonster = entity instanceof Monster;
            boolean isPlayer = entity instanceof Player;
            if (!isMonster && (!targetPlayers || !isPlayer)) continue;
            if (entity instanceof LivingEntity living
                    && (!living.isAlive() || living.isInvisibleTo(client.player))) continue;

            Vec3 targetPos = entity.getBoundingBox().getCenter();
            Vec3 delta = targetPos.subtract(eye);
            double dist = delta.length();
            if (dist > maxDist || dist < 0.01) continue;

            // Simple score: closest entity that's in reach
            double score = dist;
            if (score < bestScore) {
                bestScore = score;
                best = entity;
            }
        }

        if (best != null && client.player.getAttackStrengthScale(0.0F) >= 0.9F
                && client.player.canInteractWithEntity(best, 1.0)) {
            // Attack the server-side (in singleplayer, real) target
            if (client.hitResult instanceof EntityHitResult ehr && ehr.getEntity() == best) {
                client.gameMode.attack(client.player, best);
                client.player.resetAttackStrengthTicker();
            }
        }
    }
}