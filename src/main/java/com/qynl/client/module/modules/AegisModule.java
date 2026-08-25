package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import com.qynl.client.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Aegis — The Evasion Engine.
 *
 * <p>Integrates every projectile's trajectory (arrows, snowballs, splash pots,
 * pearls) against your own motion and sidesteps out of the way with pure
 * vanilla input. Dodge direction is the weighted vector sum of all inbound
 * projectiles — one decisive strafe, never a nervous flicker.</p>
 */
public class AegisModule extends Module {
    private static final RandomSource RANDOM = RandomSource.create();

    private boolean dodging = false;
    private int dodgeTimer = 0;
    private int cooldownTicks = 0;
    private double dodgeX = 0;
    private double dodgeZ = 0;

    public AegisModule() {
        super("Aegis", "Evasion Engine — dodges arrows, snowballs, and projectiles with weighted vector sidesteps.",
                Category.COMBAT);
        bindKey(GLFW.GLFW_KEY_UNKNOWN);
        addSetting(Setting.range("reactionMs", "Reaction delay", 100.0, 50, 250, 10, "ms"));
        addSetting(Setting.range("dodgeDist", "Dodge distance", 2.5, 1.5, 4.0, 0.25, "b"));
        addSetting(Setting.range("cooldownMs", "Cooldown", 200.0, 100, 500, 50, "ms"));
        addSetting(Setting.options("voidGuard", "Void guard", "On", "On", "Off"));
        addSetting(Setting.range("inaccuracy", "Human inaccuracy", 15.0, 0, 30, 5, "%"));
    }

    @Override
    public void onTick(Minecraft client) {
        if (client.player == null || client.level == null) {
            reset();
            return;
        }
        var player = client.player;
        if (player.isDeadOrDying() || player.isSpectator() || player.isPassenger()) {
            reset();
            return;
        }

        if (cooldownTicks > 0) cooldownTicks--;

        if (dodging) {
            if (dodgeTimer > 0) {
                dodgeTimer--;
                // Maintain dodge direction
                return;
            }
            // Dodge complete — release keys
            releaseDodge(client);
            cooldownTicks = (int) Math.round(getDoubleSetting("cooldownMs") / 50.0) + RANDOM.nextInt(4);
            return;
        }

        if (cooldownTicks > 0) return;

        // Scan for inbound projectiles
        List<ProjectileDanger> dangers = scanProjectiles(client);
        if (dangers.isEmpty()) return;

        // Void guard: don't dodge into the void
        if ("On".equals(getStringSetting("voidGuard")) && isNearVoid(client)) return;

        // Compute weighted dodge direction
        double totalX = 0, totalZ = 0;
        double totalWeight = 0;
        Vec3 myPos = player.position();

        for (ProjectileDanger d : dangers) {
            Vec3 toMe = myPos.subtract(d.pos);
            double dist = toMe.length();
            if (dist < 0.01) continue;
            double weight = 1.0 / Math.max(0.5, dist) * d.threat;
            totalX += toMe.x / dist * weight;
            totalZ += toMe.z / dist * weight;
            totalWeight += weight;
        }

        if (totalWeight < 0.001) return;

        // Normalize
        double len = Math.sqrt(totalX * totalX + totalZ * totalZ);
        if (len < 0.001) return;

        // Apply human inaccuracy
        double inaccuracy = getDoubleSetting("inaccuracy") / 100.0;
        double angle = (RANDOM.nextDouble() - 0.5) * inaccuracy * Math.PI;
        double cos = Math.cos(angle), sin = Math.sin(angle);
        double nx = totalX / len * cos - totalZ / len * sin;
        double nz = totalX / len * sin + totalZ / len * cos;

        // Start dodge
        dodgeX = nx;
        dodgeZ = nz;
        dodging = true;
        dodgeTimer = (int) Math.round(getDoubleSetting("dodgeDist") / 0.3); // ticks based on move speed
        if (dodgeTimer < 2) dodgeTimer = 2;
        if (dodgeTimer > 12) dodgeTimer = 12;
    }

    /** Called by InputMixin to apply dodge direction. */
    public boolean shouldDodgeLeft() {
        if (!isEnabled() || !dodging) return false;
        // Compute strafe relative to player's facing
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return false;
        float yawRad = (float) Math.toRadians(client.player.getYRot());
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);
        double strafeX = forwardZ;
        double strafeZ = -forwardX;
        double dotStrafe = dodgeX * strafeX + dodgeZ * strafeZ;
        return dotStrafe < 0; // dodge is to the left of facing
    }

    public boolean isDodging() {
        return isEnabled() && dodging;
    }

    public double getForwardDodge() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return 0;
        float yawRad = (float) Math.toRadians(client.player.getYRot());
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);
        return dodgeX * forwardX + dodgeZ * forwardZ;
    }

    public double getStrafeDodge() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return 0;
        float yawRad = (float) Math.toRadians(client.player.getYRot());
        double strafeX = Math.cos(yawRad);
        double strafeZ = Math.sin(yawRad);
        return dodgeX * strafeX + dodgeZ * strafeZ;
    }

    private void releaseDodge(Minecraft client) {
        dodging = false;
        dodgeTimer = 0;
        dodgeX = 0;
        dodgeZ = 0;
    }

    private void reset() {
        Minecraft client = Minecraft.getInstance();
        releaseDodge(client);
        cooldownTicks = 0;
    }

    private List<ProjectileDanger> scanProjectiles(Minecraft client) {
        List<ProjectileDanger> dangers = new ArrayList<>();
        AABB scanBox = client.player.getBoundingBox().inflate(getDoubleSetting("dodgeDist") + 2.0);

        for (var entity : client.level.getEntities(null, scanBox,
                e -> e instanceof Projectile && e.isAlive())) {
            if (!(entity instanceof Projectile proj)) continue;
            if (proj.getOwner() == client.player) continue; // don't dodge own projectiles

            Vec3 pos = proj.position();
            Vec3 vel = proj.getDeltaMovement();
            if (vel.lengthSqr() < 0.001) continue;

            // Check if projectile is heading roughly toward us
            Vec3 toPlayer = client.player.position().subtract(pos);
            double dot = vel.normalize().dot(toPlayer.normalize());
            if (dot < 0.3) continue; // not heading toward us

            double threat = 1.0;
            if (proj instanceof Arrow) threat = 2.0;
            else if (proj instanceof ThrownEgg || proj instanceof Snowball) threat = 0.5;
            else if (proj instanceof ThrownPotion || proj instanceof ThrownEnderpearl) threat = 0.3;

            dangers.add(new ProjectileDanger(pos, threat));
        }
        return dangers;
    }

    private boolean isNearVoid(Minecraft client) {
        return client.player.getY() < client.player.level().getMinBuildHeight() + 8;
    }

    private record ProjectileDanger(Vec3 pos, double threat) {}

    @Override
    public void onDisable() {
        reset();
    }
}