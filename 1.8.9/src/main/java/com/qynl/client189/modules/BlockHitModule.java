package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import com.qynl.client189.Setting;
import com.qynl.client189.mixin.KeyBindingAccessor;
import com.qynl.client189.mixin.LivingEntityAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.SwordItem;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.input.Keyboard;

import java.util.List;
import java.util.Random;

public class BlockHitModule extends Module {
    private static final Random RANDOM = new Random();
    private boolean forcingBlock = false;
    private int reactionTicks = 0;
    private int blockHoldTicks = 0;
    private int cooldownTicks = 0;
    private int lastSwingingEntity = -1;

    public BlockHitModule() {
        super("BlockHit", "Real 1.8.9 sword blocking - blocks when enemy swings, releases to keep attacking.", Category.ASSIST);
        bindKey(Keyboard.KEY_F);
        addSetting(Setting.range("reactionMs", "Reaction delay", 150.0, 50, 300, 10, "ms"));
        addSetting(Setting.range("blockTicks", "Block duration", 3.0, 2, 6, 1, "t"));
        addSetting(Setting.range("minDist", "Max distance", 3.5, 2.0, 6.0, 0.5, "b"));
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) { reset(client); return; }
        if (((LivingEntityAccessor) client.player).getDead() || client.currentScreen != null) { reset(client); return; }

        if (!(client.player.getMainHandStack().getItem() instanceof SwordItem)) { reset(client); return; }

        if (cooldownTicks > 0) { cooldownTicks--; return; }

        if (forcingBlock) {
            if (blockHoldTicks > 0) {
                blockHoldTicks--;
                if (!client.options.keyUse.isPressed()) {
                    ((KeyBindingAccessor) client.options.keyUse).setPressed(true);
                }
                return;
            }
            releaseBlock(client);
            cooldownTicks = 4 + RANDOM.nextInt(3);
            return;
        }

        if (!client.options.keyAttack.isPressed()) return;
        if (client.options.keyUse.isPressed() || client.player.isUsingItem()) return;
        if (reactionTicks > 0) { reactionTicks--; return; }

        LivingEntity attacker = findSwingingEnemy(client);
        if (attacker == null) { lastSwingingEntity = -1; return; }
        if (attacker.getEntityId() == lastSwingingEntity) return;
        lastSwingingEntity = attacker.getEntityId();

        ((KeyBindingAccessor) client.options.keyUse).setPressed(true);
        forcingBlock = true;
        blockHoldTicks = (int) getDoubleSetting("blockTicks") + RANDOM.nextInt(3) - 1;
        if (blockHoldTicks < 2) blockHoldTicks = 2;
    }

    @SuppressWarnings("unchecked")
    private LivingEntity findSwingingEnemy(MinecraftClient client) {
        PlayerEntity player = client.player;
        double maxDist = getDoubleSetting("minDist");
        Box searchBox = player.getBoundingBox().expand(maxDist + 1, maxDist + 1, maxDist + 1);

        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;

        List<Entity> entities = client.world.entities;
        for (Entity obj : entities) {
            if (!(obj instanceof LivingEntity)) continue;
            LivingEntity living = (LivingEntity) obj;
            if (!(living instanceof MobEntity || living instanceof PlayerEntity)) continue;
            if (living == player || !living.isAlive()) continue;

            double dx = living.x - player.x, dy = living.y - player.y, dz = living.z - player.z;
            double dist = dx * dx + dy * dy + dz * dz;
            if (dist > maxDist * maxDist) continue;

            boolean isSwinging = living.handSwinging;
            Vec3d toPlayer = new Vec3d(-dx, -dy, -dz).normalize();
            float yaw = living.yaw * 0.017453292F;
            float pitch = living.pitch * 0.017453292F;
            Vec3d lookDir = new Vec3d(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
            double dot = lookDir.dotProduct(toPlayer);
            double realDist = Math.sqrt(dist);

            if (isSwinging && dot > 0.3 && realDist < bestDist) {
                double ms = getDoubleSetting("reactionMs");
                reactionTicks = Math.max(1, (int) Math.round(ms / 50.0) + RANDOM.nextInt(3) - 1);
                bestDist = realDist;
                best = living;
            }
            if (realDist <= 2.5 && dot > 0.6 && best == null) {
                double ms = getDoubleSetting("reactionMs");
                reactionTicks = Math.max(1, (int) Math.round(ms / 50.0));
                bestDist = realDist;
                best = living;
            }
        }
        return best;
    }

    private void releaseBlock(MinecraftClient client) {
        ((KeyBindingAccessor) client.options.keyUse).setPressed(false);
        forcingBlock = false;
        blockHoldTicks = 0;
    }

    private void reset(MinecraftClient client) {
        releaseBlock(client);
        reactionTicks = 0;
        cooldownTicks = 0;
    }

    @Override public void onDisable() { reset(MinecraftClient.getInstance()); }
}
