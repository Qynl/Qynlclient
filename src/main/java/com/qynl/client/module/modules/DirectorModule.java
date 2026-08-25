package com.qynl.client.module.modules;

import com.qynl.client.QynlClient;
import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import com.qynl.client.module.ModuleManager;
import com.qynl.client.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

/**
 * Director — The combat AI brain.
 *
 * <p>Watches the fight and decides, tick by tick, which combat modules
 * may act and how hard. Switches between 7 tactics with humanized
 * reaction delays. Never starts a fight by itself — engagement gate
 * requires crosshair on target + attack held. Sends zero packets.</p>
 *
 * <p>Tactics:</p>
 * <ul>
 *   <li><b>Idle</b> — no combat, all combat modules idle.</li>
 *   <li><b>Engage</b> — closing on target, strafe in.</li>
 *   <li><b>Combo</b> — active close-range fighting, W-tap + crits.</li>
 *   <li><b>Trade</b> — exchanging hits, blockhit + velocity.</li>
 *   <li><b>Defend</b> — backing off, shield/evasion up.</li>
 *   <li><b>Evade</b> — dodging projectiles, Aegis active.</li>
 *   <li><b>Retreat</b> — running away, blink/clutch ready.</li>
 *   <li><b>Survive</b> — critical health, auto-potion/clutch.</li>
 * </ul>
 */
public class DirectorModule extends Module {
    private static final RandomSource RANDOM = RandomSource.create();

    private enum Tactic { IDLE, ENGAGE, COMBO, TRADE, DEFEND, EVADE, RETREAT, SURVIVE }

    private Tactic currentTactic = Tactic.IDLE;
    private int tacticTimer = 0;
    private int reactionTicks = 0;
    private LivingEntity target = null;

    public DirectorModule() {
        super("Director", "Combat AI — watches fights and decides which combat modules to use and how hard.",
                Category.COMBAT);
        bindKey(GLFW.GLFW_KEY_UNKNOWN);
        addSetting(Setting.range("aggression", "Aggression", 70.0, 20, 100, 5, "%"));
        addSetting(Setting.range("reactionMs", "Reaction delay", 200.0, 100, 400, 25, "ms"));
        addSetting(Setting.options("autoModule", "Auto-manage", "On", "On", "Off"));
    }

    @Override
    public void onTick(Minecraft client) {
        if (client.player == null || client.level == null) {
            reset();
            return;
        }
        var player = client.player;
        if (player.isDeadOrDying() || player.isSpectator()) {
            reset();
            return;
        }

        // Engagement gate: player must be holding attack + crosshair on target
        boolean engaged = client.options.keyAttack.isDown()
                && client.hitResult != null
                && client.hitResult.getType() == HitResult.Type.ENTITY
                && client.hitResult instanceof EntityHitResult ehr
                && ehr.getEntity() instanceof LivingEntity le
                && le.isAlive();

        if (!engaged) {
            if (currentTactic != Tactic.IDLE) {
                setTactic(Tactic.IDLE, client);
            }
            target = null;
            return;
        }

        // Update target
        if (client.hitResult instanceof EntityHitResult ehr2) {
            if (ehr2.getEntity() instanceof LivingEntity le2) {
                if (target != le2) {
                    target = le2;
                    reactionTicks = (int) Math.round(getDoubleSetting("reactionMs") / 50.0)
                            + RANDOM.nextInt(4);
                }
            }
        }

        if (target == null) return;
        if (reactionTicks > 0) { reactionTicks--; return; }

        double aggression = getDoubleSetting("aggression") / 100.0;
        double dist = player.distanceTo(target);
        float healthPct = player.getHealth() / player.getMaxHealth();
        float enemyHealthPct = target.getHealth() / target.getMaxHealth();

        if (tacticTimer > 0) {
            tacticTimer--;
        } else {
            // Decide new tactic
            Tactic next = decideTactic(dist, healthPct, enemyHealthPct, aggression);
            if (next != currentTactic) {
                setTactic(next, client);
            }
            // Tactic duration: 10-40 ticks with human variance
            tacticTimer = 10 + RANDOM.nextInt(30);
        }

        // Manage modules based on tactic
        if ("On".equals(getStringSetting("autoModule"))) {
            manageModules(client, dist, healthPct, enemyHealthPct);
        }
    }

    private Tactic decideTactic(double dist, float healthPct, float enemyHealthPct, double aggression) {
        if (healthPct < 0.2f) return Tactic.SURVIVE;
        if (healthPct < 0.35f && enemyHealthPct > 0.4f) return Tactic.RETREAT;

        if (dist > 4.0) {
            return aggression > 0.6 ? Tactic.ENGAGE : Tactic.DEFEND;
        }
        if (dist > 2.5) {
            return aggression > 0.5 ? Tactic.ENGAGE : Tactic.EVADE;
        }
        // Close range
        if (enemyHealthPct > 0.5f && healthPct < 0.6f) return Tactic.TRADE;
        if (healthPct > enemyHealthPct + 0.2f) return Tactic.COMBO;
        return Tactic.TRADE;
    }

    private void setTactic(Tactic tactic, Minecraft client) {
        currentTactic = tactic;
        ModuleManager modules = QynlClient.getInstance().getModuleManager();

        // Don't auto-disable already-running modules, let the user override
        // But ensure appropriate modules are available
        if (tactic == Tactic.IDLE) {
            return; // Let modules keep their state
        }
    }

    private void manageModules(Minecraft client, double dist, float healthPct, float enemyHealthPct) {
        ModuleManager modules = QynlClient.getInstance().getModuleManager();
        double aggression = getDoubleSetting("aggression") / 100.0;

        // Sprint: always on during combat
        if (!modules.isEnabled("AutoSprint") && aggression > 0.3) {
            Module sprint = modules.find("AutoSprint");
            if (sprint != null && !sprint.isEnabled()) sprint.setEnabled(true);
        }

        // WTap: combo range only
        if (currentTactic == Tactic.COMBO && aggression > 0.5 && dist < 3.0) {
            Module wtap = modules.find("WTap");
            if (wtap != null && !wtap.isEnabled()) wtap.setEnabled(true);
        } else if (currentTactic != Tactic.COMBO) {
            Module wtap = modules.find("WTap");
            if (wtap != null && wtap.isEnabled()) wtap.setEnabled(false);
        }

        // Criticals: engage/combo phases
        if ((currentTactic == Tactic.COMBO || currentTactic == Tactic.ENGAGE) && aggression > 0.4) {
            Module crits = modules.find("Criticals");
            if (crits != null && !crits.isEnabled()) crits.setEnabled(true);
        }

        // BlockHit: trade/defend
        if ((currentTactic == Tactic.TRADE || currentTactic == Tactic.DEFEND) && dist < 3.5) {
            Module bh = modules.find("BlockHit");
            if (bh != null && !bh.isEnabled() && aggression > 0.3) bh.setEnabled(true);
        }

        // Aegis: evade phase
        if (currentTactic == Tactic.EVADE) {
            Module aegis = modules.find("Aegis");
            if (aegis != null && !aegis.isEnabled()) aegis.setEnabled(true);
        }

        // Velocity: always on during combat
        if (healthPct < 0.6f) {
            Module vel = modules.find("VelocityAssist");
            if (vel != null && !vel.isEnabled()) vel.setEnabled(true);
        }

        // AutoPotion: survive phase
        if (currentTactic == Tactic.SURVIVE && healthPct < 0.4f) {
            Module pot = modules.find("AutoPotion");
            if (pot != null && !pot.isEnabled()) pot.setEnabled(true);
        } else if (currentTactic != Tactic.SURVIVE && healthPct > 0.5f) {
            Module pot = modules.find("AutoPotion");
            if (pot != null && pot.isEnabled() && aggression < 0.8) pot.setEnabled(false);
        }
    }

    private void reset() {
        currentTactic = Tactic.IDLE;
        tacticTimer = 0;
        reactionTicks = 0;
        target = null;
    }

    public Tactic getCurrentTactic() { return currentTactic; }
    public LivingEntity getTarget() { return target; }

    @Override
    public void onDisable() {
        reset();
    }
}