package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import com.qynl.client.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

/**
 * Criticals — The Airborne Engine.
 *
 * <p>Manages jump timing so you're airborne when attacks land, producing
 * natural critical hits. Let AutoClicker handle the actual attacking.</p>
 *
 * <p>Modes:</p>
 * <ul>
 *   <li><b>Jump</b> — auto-jump before attacks to create airborne state.</li>
 *   <li><b>Always</b> — jump whenever attacking a living target.</li>
 *   <li><b>Knockback</b> — only jump when you've been knocked back (not self-jump).</li>
 * </ul>
 */
public class CriticalsModule extends Module {
    private static final RandomSource RANDOM = RandomSource.create();

    private boolean forcingJump = false;
    private int jumpTicks = 0;
    private int cooldownTicks = 0;

    public CriticalsModule() {
        super("Criticals", "Airborne Engine — auto-jumps before attacks for natural crits. Works with AutoClicker.",
                Category.COMBAT);
        bindKey(GLFW.GLFW_KEY_UNKNOWN);
        addSetting(Setting.options("mode", "Mode", "Jump", "Jump", "Always", "Knockback"));
        addSetting(Setting.range("chance", "Chance", 85.0, 50, 100, 5, "%"));
        addSetting(Setting.range("cooldownMs", "Cooldown", 350.0, 100, 600, 50, "ms"));
    }

    @Override
    public void onTick(Minecraft client) {
        if (client.player == null || client.level == null) {
            reset(client);
            return;
        }
        var player = client.player;
        if (player.isDeadOrDying() || player.isSpectator()) {
            reset(client);
            return;
        }

        if (cooldownTicks > 0) cooldownTicks--;

        // Must be attacking and aiming at a living target
        if (!client.options.keyAttack.isDown()) {
            if (forcingJump) reset(client);
            return;
        }
        if (client.hitResult == null || client.hitResult.getType() != HitResult.Type.ENTITY) {
            return;
        }
        if (!(client.hitResult instanceof EntityHitResult ehr)) return;
        if (!(ehr.getEntity() instanceof LivingEntity target) || !target.isAlive()) return;

        boolean onGround = player.onGround();
        String mode = getStringSetting("mode");
        double chance = getDoubleSetting("chance") / 100.0;

        // "Knockback" mode: don't initiate jump, only apply during knockback
        if ("Knockback".equals(mode)) {
            return; // Let natural knockback create crits; we don't force anything
        }

        // "Always" mode: jump any time we're attacking a target on solid ground
        // "Jump" mode: jump before attacks with cooldown and chance
        boolean shouldJump = false;

        if ("Always".equals(mode)) {
            shouldJump = onGround;
        } else { // Jump mode
            shouldJump = onGround && cooldownTicks <= 0 && player.getAttackStrengthScale(0.0F) >= 0.9F;
        }

        if (shouldJump && !forcingJump) {
            if ("Always".equals(mode) || RANDOM.nextDouble() < chance) {
                client.options.keyJump.setDown(true);
                forcingJump = true;
                jumpTicks = 0;
            }
        }

        if (forcingJump) {
            jumpTicks++;
            if (!player.onGround() && jumpTicks >= 2) {
                // We're airborne — release jump so we fall (and can then crit)
                if (client.options.keyJump.isDown()) {
                    client.options.keyJump.setDown(false);
                }
            }
            if (jumpTicks >= 10) {
                // Jump complete
                if (client.options.keyJump.isDown()) {
                    client.options.keyJump.setDown(false);
                }
                forcingJump = false;
                jumpTicks = 0;
                cooldownTicks = (int) Math.round(getDoubleSetting("cooldownMs") / 50.0);
            }
        }
    }

    private void reset(Minecraft client) {
        if (forcingJump && client.options != null) {
            client.options.keyJump.setDown(false);
        }
        forcingJump = false;
        jumpTicks = 0;
        cooldownTicks = 0;
    }

    @Override
    public void onDisable() {
        reset(Minecraft.getInstance());
    }
}