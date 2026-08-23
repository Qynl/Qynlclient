package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import com.qynl.client189.Setting;
import com.qynl.client189.WorldDraw;
import com.qynl.client189.mixin.MinecraftClientInvoker;
import net.minecraft.block.Material;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.input.Keyboard;

import java.util.Random;

/**
 * CRITICALS — the Airborne Engine. The one combat axis nothing else in the
 * client touches: <b>gravity itself</b>. It needs no modified packet, no
 * onGround spoof, no impossible movement — it only attacks inside the
 * airtime that vanilla physics already gives you, and times jumps so that
 * airtime is always there when you need it.
 *
 * <p><b>Own-air (crit).</b> Any attack while falling (and not in water) is
 * a critical hit. CRITICALS fires exactly there — while you fall from your
 * own jumps, from knockback (turning a combo against you into a chain of
 * crits), or from walking off an edge — and never while grounded. On top of
 * that it <b>predicts</b>: when an enemy is about to step into reach it
 * jumps a beat early (humanized chance + cooldown, only on safe ground with
 * a solid block below), so the fall window is already open when the fight
 * starts. Every packet is a vanilla jump and a vanilla click — a perfect
 * player, nothing to flag.</p>
 *
 * <p><b>Enemy-air (air assault).</b> A mid-air enemy cannot block-hit,
 * cannot sprint-reset, and takes the full knockback — the most punishing
 * moment to hit anyone. The module tracks the crosshair target's airborne
 * state client-side and clicks exactly while they are off the ground.</p>
 *
 * <p>Safe by construction: never attacks during a dangerous fall (fall
 * damage gate), never in water, never while sneaking/using an item, never
 * jumps over air. Defers its own clicking to AutoClicker / Qynl / Hindsight
 * when one of them is running — it then only provides the jumps.</p>
 */
public class CriticalsModule extends Module {
    private static final Random RANDOM = new Random();
    private static CriticalsModule instance;

    private int tickCounter = 0;
    private int clickTimer = 0;
    private int nextInterval = 3;
    private int jumpCooldown = 0;
    private boolean wasFalling = false;
    /** Distance to the target last tick — the predictive jump only fires
     *  while the gap is shrinking (approaching). Hopping toward a target
     *  that is backing away is a rhythm Intave's fight-flow analysis picks
     *  up, and it wastes the jump window anyway. */
    private double lastTargetDist = Double.MAX_VALUE;

    /** Best airborne target under the crosshair (for render + air mode). */
    private LivingEntity target;
    private boolean targetAirborne;

    public CriticalsModule() {
        super("Criticals",
                "Airborne Engine — every hit becomes a crit by attacking only while legitimately falling (own jumps, knockback, edges), auto-jumps before fights start, and punishes enemies the moment they leave the ground. Zero modified packets.",
                Category.COMBAT);
        instance = this;
        bindKey(Keyboard.KEY_F10);
        addSetting(Setting.options("mode",       "Mode",        "Both",   "Crit", "Air", "Both"));
        addSetting(Setting.options("jump",       "Predict jump","On",     "On",   "Off"));
        addSetting(Setting.range("jumpRange",    "Jump range",   3.6,    2.5,  5.0, 0.1, "b"));
        addSetting(Setting.range("cps",          "CPS",          8.0,    4,    12,   1));
        addSetting(Setting.range("chance",       "Chance",      90.0,    0,   100,   5,  "%"));
        addSetting(Setting.range("jumpChance",   "Jump chance",  70.0,    0,   100,   5,  "%"));
        addSetting(Setting.options("onlyAttack", "While attacking", "On", "On", "Off"));
        addSetting(Setting.options("render",     "Render",      "On",     "On",  "Off"));
    }

    public static CriticalsModule getInstance() { return instance; }
    public static boolean isActive() { return instance != null && instance.isEnabled(); }

    @Override
    public void onTick(MinecraftClient client) {
        target = null;
        targetAirborne = false;
        if (client.player == null || client.world == null || client.interactionManager == null) {
            return;
        }
        if (client.currentScreen != null || !client.player.isAlive()) {
            return;
        }

        tickCounter++;
        if (jumpCooldown > 0) jumpCooldown--;

        String mode = getStringSetting("mode");
        boolean doCrit = "Both".equals(mode) || "Crit".equals(mode);
        boolean doAir = "Both".equals(mode) || "Air".equals(mode);

        // ── crosshair target ─────────────────────────────────
        Entity pointed = client.targetedEntity;
        if (pointed instanceof LivingEntity && pointed != client.player) {
            LivingEntity living = (LivingEntity) pointed;
            if (living.isAlive()
                    && (living instanceof MobEntity || living instanceof PlayerEntity)
                    && !FriendsModule.isFriend(FriendsModule.entityName(living))) {
                target = living;
                // Airborne = off the ground, OR already descending — the
                // client's onGround flag lags the server by half a ping, so
                // after your knockback the server already has the enemy in
                // the air while he still renders grounded. His y-velocity
                // (from the last two position updates) reveals it a tick
                // earlier: attack the moment the descent starts.
                targetAirborne = !living.onGround
                        || (living.y - living.prevY) < -0.2;
            }
        }

        boolean attacking = client.options.keyAttack.isPressed();
        if ("On".equals(getStringSetting("onlyAttack")) && !attacking) {
            return;
        }

        // ── predictive jump: open the fall window before the fight ──
        if (doCrit && "On".equals(getStringSetting("jump")) && target != null
                && jumpCooldown <= 0 && canJump(client)) {
            double dx = target.x - client.player.x;
            double dy = target.y - client.player.y;
            double dz = target.z - client.player.z;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            boolean closing = dist < lastTargetDist + 0.05;
            if (dist <= getDoubleSetting("jumpRange") && closing
                    && (RANDOM.nextDouble() * 100.0) < getDoubleSetting("jumpChance")) {
                client.player.jump();
                jumpCooldown = 12 + RANDOM.nextInt(7); // 12–18 tick gap
            }
        }
        if (target != null) {
            lastTargetDist = Math.sqrt(
                    (target.x - client.player.x) * (target.x - client.player.x)
                            + (target.y - client.player.y) * (target.y - client.player.y)
                            + (target.z - client.player.z) * (target.z - client.player.z));
        } else {
            lastTargetDist = Double.MAX_VALUE;
        }

        // ── falling window (own-air crits) ───────────────────
        boolean falling = doCrit && isFalling(client);
        if (falling && !wasFalling) {
            clickTimer = 0; // fresh window starts clicking immediately
        }
        wasFalling = falling;

        boolean airTarget = doAir && target != null && targetAirborne;

        // ── clicking ─────────────────────────────────────────
        boolean externalClicker = AutoClickerModule.isActive()
                || QynlModule.isActive()
                || HindsightModule.isActive();
        if (externalClicker || (!falling && !airTarget)) {
            return;
        }

        clickTimer++;
        if (clickTimer < nextInterval) return;
        clickTimer = 0;

        double baseCps = getDoubleSetting("cps");
        nextInterval = Math.max(1, (int) Math.round(20.0 / baseCps) + RANDOM.nextInt(4) - 1);
        // Occasional missed click — human streams have gaps.
        if (RANDOM.nextInt(100) < 4) {
            nextInterval *= 2;
        }
        if ((RANDOM.nextDouble() * 100.0) >= getDoubleSetting("chance")) return;

        ((MinecraftClientInvoker) client).invokeDoAttack();
    }

    // ── window checks ─────────────────────────────────────────────

    /** True while the player is in a safe falling window (crit-eligible). */
    private boolean isFalling(MinecraftClient client) {
        if (client.player.isSubmergedIn(Material.WATER)) return false;
        if (client.player.isUsingItem()) return false;
        // Must actually be falling, and never on a dangerous drop.
        return client.player.velocityY < -0.01 && client.player.fallDistance < 3.0;
    }

    /** True when a predictive jump is physically safe. */
    private boolean canJump(MinecraftClient client) {
        if (!client.player.onGround) return false;
        if (client.player.isSneaking() || client.player.isUsingItem()) return false;
        if (client.player.isSubmergedIn(Material.WATER)) return false;
        if (Math.abs(client.player.velocityY) > 0.01) return false;
        if (client.player.y < 4.0) return false; // don't hop over the void
        // Solid block under the feet — never jump while standing over air.
        BlockPos under = new BlockPos(
                client.player.x,
                client.player.getBoundingBox().minY - 0.1,
                client.player.z);
        if (client.world.isAir(under)) return false;

        // Idiot-proof edge guard: look 1.5 blocks ahead along the movement
        // direction (falling back to the look direction when idle). At the
        // jump start fallDistance is still 0 even when jumping into a deep
        // pit — so probe the landing zone: if it drops more than ~3 blocks,
        // don't jump. Bridges and shallow steps stay jumpable.
        double mx = client.player.x - client.player.prevX;
        double mz = client.player.z - client.player.prevZ;
        double dirX = mx, dirZ = mz;
        if (dirX == 0.0 && dirZ == 0.0) {
            float yawRad = client.player.yaw * 0.017453292F;
            dirX = -Math.sin(yawRad);
            dirZ = Math.cos(yawRad);
        }
        double len = Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (len < 0.01) return true;
        dirX /= len;
        dirZ /= len;

        BlockPos ahead = new BlockPos(
                client.player.x + dirX * 1.5,
                client.player.getBoundingBox().minY - 0.1,
                client.player.z + dirZ * 1.5);
        if (client.world.isAir(ahead)) {
            int depth = 0;
            BlockPos probe = ahead.down();
            while (depth < 4 && client.world.isAir(probe)) {
                depth++;
                probe = probe.down();
            }
            if (depth >= 3) return false; // deep drop ahead — no jump
        }
        return true;
    }

    // ── rendering ─────────────────────────────────────────────────

    public static void render(float partialTicks) {
        if (instance == null || !instance.isEnabled()
                || "Off".equals(instance.getStringSetting("render"))) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        double camX = client.player.prevX + (client.player.x - client.player.prevX) * partialTicks;
        double camY = client.player.prevY + (client.player.y - client.player.prevY) * partialTicks;
        double camZ = client.player.prevZ + (client.player.z - client.player.prevZ) * partialTicks;

        WorldDraw.begin(false);

        // Own-air window: cyan ring at the feet while crits are live.
        if (instance.isFalling(client)) {
            drawRing(client.player.x - camX, client.player.y - camY + 0.1,
                    client.player.z - camZ, 0.5,
                    0.20f, 0.80f, 1.00f, 0.9f);
        }
        // Airborne enemies within 8 blocks: red rings.
        double rangeSq = 8.0 * 8.0;
        for (Entity entity : client.world.entities) {
            if (!(entity instanceof LivingEntity)) continue;
            LivingEntity living = (LivingEntity) entity;
            if (living == client.player || !living.isAlive() || living.onGround) continue;
            if (!(living instanceof MobEntity) && !(living instanceof PlayerEntity)) continue;
            if (FriendsModule.isFriend(FriendsModule.entityName(living))) continue;
            double dx = living.x - client.player.x;
            double dy = living.y - client.player.y;
            double dz = living.z - client.player.z;
            if (dx * dx + dy * dy + dz * dz > rangeSq) continue;
            drawRing(living.x - camX, living.y - camY + 0.1, living.z - camZ, 0.55,
                    1.00f, 0.35f, 0.35f, 0.7f);
        }
        WorldDraw.end();
    }

    private static void drawRing(double x, double y, double z, double radius,
                                 float r, float g, float b, float a) {
        int segments = 16;
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0 * i / segments;
            double a1 = Math.PI * 2.0 * (i + 1) / segments;
            WorldDraw.line(
                    x + Math.cos(a0) * radius, y, z + Math.sin(a0) * radius,
                    x + Math.cos(a1) * radius, y, z + Math.sin(a1) * radius,
                    r, g, b, a);
        }
    }

    @Override
    public void onDisable() {
        target = null;
        targetAirborne = false;
        wasFalling = false;
        jumpCooldown = 0;
        lastTargetDist = Double.MAX_VALUE;
    }
}
