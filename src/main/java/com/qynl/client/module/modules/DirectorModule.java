package com.qynl.client.module.modules;

import com.qynl.client.QynlClient;
import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import com.qynl.client.module.ModuleManager;
import com.qynl.client.module.Setting;
import com.qynl.client.util.WorldDraw;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * DIRECTOR — the combat AI. Not another muscle: the brain. It watches the
 * fight and decides, tick by tick, <b>which</b> of the client's combat
 * modules may act and <b>how hard</b> — so nothing ever runs constantly.
 *
 * <p>An anti-cheat's statistical heuristics fingerprint modules that behave
 * identically in every fight. A human adapts — aggressive when the enemy is
 * airborne, defensive when being combo'd, evasive under fire, gone at low HP.
 * DIRECTOR reproduces that adaptation by switching <b>tactics</b> with
 * humanized reaction delays and only ever enabling the modules whose behavior
 * is plausible in the current situation:</p>
 * <ul>
 *   <li><b>Engage</b> — enemy closing: perfect-timing hits (Qynl), crit
 *       jumps (Criticals), strafing, gentle reach.</li>
 *   <li><b>Combo</b> — enemy airborne/knocked: sustained DPS (AutoClicker),
 *       W-tap sprint-resets, enemy-air hits.</li>
 *   <li><b>Trade</b> — both grounded: timing + block-hit + medium velocity.</li>
 *   <li><b>Defend</b> — being combo'd: reactive block, heavy velocity
 *       reduction, evasion, no reckless jumps.</li>
 *   <li><b>Evade</b> — projectiles incoming: dodge, keep moving, no fighting.</li>
 *   <li><b>Retreat</b> — low health: sprint away, max damage reduction, no
 *       attacking.</li>
 *   <li><b>Survive</b> — fall/void/lava danger: Clutch is force-enabled, every
 *       combat module is stood down.</li>
 * </ul>
 *
 * <p>Survival is layered on top: the instant Clutch reports a latched lethal
 * fall or an active save, the DIRECTOR pre-empts its own tactic and runs the
 * Survive profile. Aegis is force-enabled the moment any projectile threatens.
 * The DIRECTOR sends zero packets — it only decides. The user's real module
 * loadout is snapshotted on enable and restored on disable, and saves always
 * persist the user loadout, never the transient tactic state.</p>
 */
public class DirectorModule extends Module {
    private static final Random RANDOM = new Random();
    private static DirectorModule instance;

    // ── tactics ──────────────────────────────────────────────────
    private static final int T_NONE = 0;
    private static final int T_ENGAGE = 1;
    private static final int T_COMBO = 2;
    private static final int T_TRADE = 3;
    private static final int T_DEFEND = 4;
    private static final int T_EVADE = 5;
    private static final int T_RETREAT = 6;
    private static final int T_SURVIVE = 7;

    /** Modules under Director control. Hindsight is Qynl's rewind twin — the
     *  profiles never run both at once (Qynl wins). Clutch is a survival
     *  module: force-enabled in Survive, everything stood down while saving. */
    private static final String[] MANAGED = {
            "AimAssist", "AutoClicker", "ReachAssist", "VelocityAssist", "AutoSprint",
            "WTap", "BlockHit", "Qynl", "Hindsight", "Criticals", "Aegis",
            "StrafeAssist", "Clutch"
    };

    /** {module, setting, value} tuning per tactic — adapted to the 1.21.1
     *  modules' actual setting keys. applySetting ignores unknown keys, so a
     *  stale or renamed key is always safe. */	private static final String[][] TUNE_ENGAGE = {
			{"ReachAssist", "mode", "Normal"}, {"ReachAssist", "choke", "On"},
			{"VelocityAssist", "horizontal", "35"}, {"VelocityAssist", "chance", "80"},
            {"WTap", "chance", "55"},
            {"Criticals", "mode", "Jump"}, {"Criticals", "chance", "75"},
            {"AimAssist", "strength", "80"},
            {"StrafeAssist", "intervalMs", "350"}
    };	private static final String[][] TUNE_COMBO = {
			{"ReachAssist", "mode", "Normal"}, {"ReachAssist", "choke", "On"},
			{"VelocityAssist", "horizontal", "20"}, {"VelocityAssist", "vertical", "15"},
            {"VelocityAssist", "chance", "80"}, {"WTap", "chance", "90"},
            {"WTap", "tapTicks", "2"}, {"Criticals", "mode", "Jump"},
            {"Criticals", "chance", "80"}, {"AimAssist", "strength", "95"},
            {"StrafeAssist", "intervalMs", "220"}
    };	private static final String[][] TUNE_TRADE = {
			{"ReachAssist", "mode", "Normal"}, {"ReachAssist", "choke", "On"},
			{"VelocityAssist", "horizontal", "45"}, {"VelocityAssist", "vertical", "20"},
            {"VelocityAssist", "chance", "75"}, {"WTap", "chance", "70"},
            {"BlockHit", "mode", "Reactive"}, {"BlockHit", "chance", "75"},
            {"BlockHit", "reactionMs", "130"}, {"Criticals", "mode", "Jump"},
            {"Criticals", "chance", "70"}, {"AimAssist", "strength", "85"}
    };	private static final String[][] TUNE_DEFEND = {
			{"ReachAssist", "mode", "Subtle"}, {"ReachAssist", "choke", "Off"},
			{"VelocityAssist", "horizontal", "65"}, {"VelocityAssist", "vertical", "35"},
            {"VelocityAssist", "chance", "90"}, {"BlockHit", "mode", "Reactive"},
            {"BlockHit", "chance", "90"}, {"BlockHit", "reactionMs", "100"},
            {"Criticals", "chance", "0"}
    };
    private static final String[][] TUNE_EVADE = {
            {"VelocityAssist", "horizontal", "50"}, {"VelocityAssist", "vertical", "30"},
            {"VelocityAssist", "chance", "85"}
    };
    private static final String[][] TUNE_RETREAT = {
            {"VelocityAssist", "horizontal", "75"}, {"VelocityAssist", "vertical", "40"},
            {"VelocityAssist", "chance", "95"}, {"BlockHit", "mode", "Reactive"},
            {"BlockHit", "chance", "95"}, {"BlockHit", "reactionMs", "90"}
    };

    private static final String[] TACTIC_NAMES = {
            "Idle", "Engage", "Combo", "Trade", "Defend", "Evade", "Retreat", "Survive"
    };

    private static final Profile[] PROFILES = new Profile[8];

    private static final class Profile {
        final String[] on;
        final String[] off;
        final String[][] tune;

        Profile(String[] on, String[] off, String[][] tune) {
            this.on = on;
            this.off = off;
            this.tune = tune;
        }
    }

    static {
        PROFILES[T_NONE] = new Profile(new String[]{"AutoSprint"}, new String[]{
                "AimAssist", "AutoClicker", "ReachAssist", "VelocityAssist", "WTap",
                "BlockHit", "Qynl", "Hindsight", "Criticals", "Aegis",
                "StrafeAssist", "Clutch"}, new String[0][0]);
        PROFILES[T_ENGAGE] = new Profile(new String[]{
                "AutoSprint", "AimAssist", "StrafeAssist", "Criticals", "Qynl", "ReachAssist"},
                new String[]{"AutoClicker", "WTap", "BlockHit", "Aegis",
                        "Hindsight", "Clutch"}, TUNE_ENGAGE);
        PROFILES[T_COMBO] = new Profile(new String[]{
                "AutoSprint", "AimAssist", "AutoClicker", "Criticals", "WTap",
                "StrafeAssist", "ReachAssist", "VelocityAssist"},
                new String[]{"Qynl", "Hindsight", "BlockHit", "Aegis", "Clutch"},
                TUNE_COMBO);
        PROFILES[T_TRADE] = new Profile(new String[]{
                "AutoSprint", "AimAssist", "Qynl", "BlockHit", "ReachAssist", "VelocityAssist",
                "Criticals", "StrafeAssist", "WTap"},
                new String[]{"AutoClicker", "Aegis", "Hindsight", "Clutch"},
                TUNE_TRADE);
        PROFILES[T_DEFEND] = new Profile(new String[]{
                "AutoSprint", "VelocityAssist", "BlockHit", "Qynl", "Aegis", "StrafeAssist"},
                new String[]{"AutoClicker", "WTap", "Criticals", "ReachAssist",
                        "AimAssist", "Hindsight", "Clutch"}, TUNE_DEFEND);
        PROFILES[T_EVADE] = new Profile(new String[]{
                "AutoSprint", "Aegis", "VelocityAssist", "StrafeAssist"},
                new String[]{"AutoClicker", "WTap", "BlockHit", "Criticals",
                        "Qynl", "Hindsight", "ReachAssist", "AimAssist", "Clutch"},
                TUNE_EVADE);
        PROFILES[T_RETREAT] = new Profile(new String[]{
                "AutoSprint", "Aegis", "VelocityAssist", "BlockHit"},
                new String[]{"AutoClicker", "WTap", "Criticals", "ReachAssist",
                        "StrafeAssist", "AimAssist", "Qynl", "Hindsight", "Clutch"},
                TUNE_RETREAT);
        PROFILES[T_SURVIVE] = new Profile(new String[]{"Clutch", "Aegis"},
                new String[]{"AutoSprint", "AimAssist", "AutoClicker", "ReachAssist",
                        "VelocityAssist", "WTap", "BlockHit", "Qynl", "Hindsight",
                        "Criticals", "StrafeAssist"}, new String[0][0]);
    }

    // ── state ────────────────────────────────────────────────────
    private final Map<String, Boolean> userStates = new HashMap<>();
    private final Map<String, Map<String, String>> userSettings = new HashMap<>();
    private final Map<String, Boolean> directorEnabled = new HashMap<>();
    private final Map<String, Integer> toggleTimers = new HashMap<>();
    private final Set<String> manualModules = new HashSet<>();

    private int tactic = T_NONE;
    private int pendingTactic = T_NONE;
    private int reactionTicks = 0;
    private int tacticHoldTicks = 0;
    private int retuneTimer = 0;
    private int tickCounter = 0;

    // assessment
    private LivingEntity focus;
    private double enemyDist = 999.0;
    private boolean enemyAirborne = false;
    private float enemyHpFrac = 1.0F;
    private float ownHpFrac = 1.0F;
    private boolean hurtActive = false;
    private boolean projectileThreat = false;

    // survival layer (fed by Clutch, read before everything else)
    private boolean fallDanger = false;
    private boolean clutchActive = false;
    private boolean fireThreat = false;

    // pressure 0..3, combo streak, engagement 0..1
    private int pressure = 0;
    private boolean wasHurt = false;
    private boolean wasEnemyHurt = false;
    private int pressureDecayTimer = 0;
    private int combo = 0;
    private int exchangeIdleTicks = 0;
    private double engagement = 0.0;

    public DirectorModule() {
        super("Director",
                "Combat AI — reads the fight and decides which combat modules may act and how hard, per tactic (engage/combo/trade/defend/evade/retreat/survive). Nothing runs constantly, every switch is humanized, zero packets sent. Clutch is force-enabled the moment a fall turns lethal and every combat module is stood down while you are saving yourself.",
                Category.COMBAT);
        instance = this;
        bindKey(GLFW.GLFW_KEY_B);
        addSetting(Setting.range("aggression", "Aggression", 65.0, 0, 100, 5, "%"));
        addSetting(Setting.range("retreatHp",  "Retreat HP", 25.0, 10, 50, 5, "%"));
        addSetting(Setting.options("humanize", "Humanize",   "On",   "On", "Off"));
        addSetting(Setting.options("render",   "Render",     "On",   "On", "Off"));
        addSetting(Setting.text("manual",      "Manual modules", ""));
        // Live tactic display — the Text GUI shows "Director · Combo" etc.
        addSetting(Setting.options("mode", "Tactic", "Idle", "Idle", "Engage",
                "Combo", "Trade", "Defend", "Evade", "Retreat", "Survive"));
    }

    public static DirectorModule getInstance() { return instance; }
    public static boolean isActive() { return instance != null && instance.isEnabled(); }

    /** User's real state for a managed module, or null when not managed/active. */
    public static Boolean userState(String moduleName) {
        if (instance == null || !instance.isEnabled()) return null;
        return instance.userStates.get(moduleName);
    }

    /** User's real settings for a managed module, or null. */
    public static Map<String, String> userSettings(String moduleName) {
        if (instance == null || !instance.isEnabled()) return null;
        return instance.userSettings.get(moduleName);
    }

    // ── lifecycle ────────────────────────────────────────────────

    @Override
    public void onEnable() {
        parseManual();
        userStates.clear();
        userSettings.clear();
        directorEnabled.clear();
        toggleTimers.clear();
        for (String name : MANAGED) {
            Module m = QynlClient.getInstance().getModuleManager().find(name);
            if (m == null) continue;
            userStates.put(name, m.isEnabled());
            Map<String, String> settings = new HashMap<>();
            for (Setting<?> s : m.getSettings()) {
                settings.put(s.getKey(), s.valueAsString());
            }
            userSettings.put(name, settings);
        }
        tactic = T_NONE;
        pendingTactic = T_NONE;
        reactionTicks = 0;
        tacticHoldTicks = 0;
        retuneTimer = 30 + RANDOM.nextInt(30);
        pressure = 0;
        combo = 0;
        wasHurt = false;
        wasEnemyHurt = false;
        exchangeIdleTicks = 0;
        engagement = 0.0;
    }

    @Override
    public void onDisable() {
        // Restore the user's real loadout for everything the director owned.
        Minecraft client = Minecraft.getInstance();
        for (Map.Entry<String, Boolean> e : directorEnabled.entrySet()) {
            Boolean userWanted = userStates.get(e.getKey());
            if (userWanted == null) continue;
            if (e.getValue() && !userWanted) {
                Module m = QynlClient.getInstance().getModuleManager().find(e.getKey());
                if (m != null && m.isEnabled()) m.setEnabled(false);
            }
        }
        // Restore every setting the tactic tuning touched.
        for (Map.Entry<String, Map<String, String>> e : userSettings.entrySet()) {
            Module m = QynlClient.getInstance().getModuleManager().find(e.getKey());
            if (m == null) continue;
            for (Map.Entry<String, String> s : e.getValue().entrySet()) {
                m.applySetting(s.getKey(), s.getValue());
            }
        }
        userStates.clear();
        userSettings.clear();
        directorEnabled.clear();
        toggleTimers.clear();
        tactic = T_NONE;
        pendingTactic = T_NONE;
        focus = null;
        engagement = 0.0;
    }

    // ── per-tick: assess → decide → act ──────────────────────────

    @Override
    public void onTick(Minecraft client) {
        focus = null;
        if (client.player == null || client.level == null) return;
        if (client.screen != null) return;

        tickCounter++;
        assess(client);
        int want = decideTactic(client);

        if (want != pendingTactic) {
            boolean urgent = (hurtActive && enemyDist < 3.5) || fallDanger || clutchActive;
            if (urgent && "On".equals(getStringSetting("humanize"))) {
                pendingTactic = want;
                reactionTicks = 1;
            } else if ("On".equals(getStringSetting("humanize"))) {
                pendingTactic = want;
                reactionTicks = 2 + RANDOM.nextInt(4);
            } else {
                pendingTactic = want;
                reactionTicks = 0;
            }
        }
        if (reactionTicks > 0) {
            reactionTicks--;
            return;
        }
        if (pendingTactic != tactic && (tacticHoldTicks >= 4
                || (hurtActive && enemyDist < 3.5)
                || fallDanger || clutchActive)) {
            tactic = pendingTactic;
            getSetting("mode").setFromString(TACTIC_NAMES[tactic]);
            applyTuning(client, PROFILES[tactic], false);
            tacticHoldTicks = 0;
        }
        tacticHoldTicks++;

        if (--retuneTimer <= 0) {
            retuneTimer = 40 + RANDOM.nextInt(41);
            if (RANDOM.nextInt(100) >= 20) {
                applyTuning(client, PROFILES[tactic], true);
            }
        }

        applyProfile(client);
    }

    // ── situation assessment ─────────────────────────────────────

    private void assess(Minecraft client) {
        enemyDist = 999.0;
        enemyAirborne = false;
        enemyHpFrac = 1.0F;
        ownHpFrac = 1.0F;
        hurtActive = false;

        fallDanger = ClutchModule.isInDanger();
        clutchActive = ClutchModule.isSaving();
        fireThreat = client.player.isOnFire() || client.player.isInLava();

        if (client.player.isAlive()) {
            ownHpFrac = client.player.getHealth() / client.player.getMaxHealth();
            hurtActive = client.player.hurtTime > 0;
            if (hurtActive && !wasHurt) {
                pressure = Math.min(3, pressure + 1);
                combo = 0;
            }
            wasHurt = hurtActive;
        }
        if (--pressureDecayTimer <= 0) {
            pressureDecayTimer = 40;
            if (pressure > 0) pressure--;
        }

        // Nearest non-friend enemy.
        double bestSq = Double.MAX_VALUE;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity)) continue;
            LivingEntity living = (LivingEntity) entity;
            if (living == client.player || !living.isAlive()) continue;
            if (!(living instanceof Monster) && !(living instanceof Player)) continue;
            if (FriendsModule.isFriend(FriendsModule.entityName(living))) continue;
            double dx = living.getX() - client.player.getX();
            double dy = living.getY() - client.player.getY();
            double dz = living.getZ() - client.player.getZ();
            double dSq = dx * dx + dy * dy + dz * dz;
            if (dSq < bestSq) {
                bestSq = dSq;
                focus = living;
                enemyAirborne = !living.onGround() || (living.getY() - living.yOld) < -0.2;
                enemyHpFrac = living.getHealth() / living.getMaxHealth();
            }
        }
        if (focus != null) {
            enemyDist = Math.sqrt(bestSq);
        }

        if (focus != null) {
            boolean enemyHurt = focus.hurtTime > 0;
            if (enemyHurt && !wasEnemyHurt) {
                pressure = Math.max(0, pressure - 1);
                combo++;
            }
            wasEnemyHurt = enemyHurt;
        } else {
            wasEnemyHurt = false;
        }
        if (focus == null) {
            if (++exchangeIdleTicks > 40) combo = 0;
        } else {
            exchangeIdleTicks = 0;
        }

        assessEngagement(client);

        // Any inbound projectile nearby? (Aegis force-enables inside any tactic.)
        projectileThreat = false;
        for (Entity e : client.level.entitiesForRendering()) {
            if (!(e instanceof AbstractArrow || e instanceof Snowball
                    || e instanceof ThrownPotion || e instanceof ThrownEnderpearl)) continue;
            if (e.onGround()) continue;
            double dx = e.getX() - client.player.getX();
            double dz = e.getZ() - client.player.getZ();
            if (dx * dx + dz * dz > 10.0 * 10.0) continue;
            Vec3 vel = e.getDeltaMovement();
            double speed = Math.sqrt(vel.x * vel.x + vel.y * vel.y + vel.z * vel.z);
            if (speed > 0.3) {
                projectileThreat = true;
                break;
            }
        }
    }

    private int decideTactic(Minecraft client) {
        if (fallDanger || clutchActive) return T_SURVIVE;
        if (focus == null) return T_NONE;
        double aggression = getDoubleSetting("aggression");
        double retreatHp = getDoubleSetting("retreatHp") / 100.0;

        if (ownHpFrac < retreatHp && enemyHpFrac > ownHpFrac + 0.05) {
            return T_RETREAT;
        }
        if (fireThreat && ownHpFrac < 0.6) {
            return T_RETREAT;
        }
        if (client.player.isInWater()) {
            return enemyDist < 5.0 ? T_EVADE : T_NONE;
        }
        if (projectileThreat && enemyDist > 3.5) {
            return T_EVADE;
        }
        if (hurtActive && enemyDist < 3.5) {
            return T_DEFEND;
        }

        // ── fight-open gate ──────────────────────────────────────────
        if (engagement < 0.5) {
            return T_NONE;
        }

        if (pressure >= 2) {
            return enemyDist < 5.0 ? T_DEFEND : T_EVADE;
        }
        if (enemyAirborne) {
            return T_COMBO;
        }
        if (combo >= 3 && enemyDist < 3.8) {
            return T_COMBO;
        }
        if (tactic == T_TRADE && enemyDist < 3.9) {
            return T_TRADE;
        }
        if (tactic == T_ENGAGE && enemyDist < 5.5 + aggression / 20.0) {
            return T_ENGAGE;
        }
        if (enemyDist < 3.4) {
            return T_TRADE;
        }
        if (enemyDist < 6.0 + aggression / 20.0) {
            return T_ENGAGE;
        }
        return T_NONE;
    }

    private void assessEngagement(Minecraft client) {
        boolean aiming = false;
        if (focus != null && enemyDist < 6.0) {
            Vec3 eye = client.player.getEyePosition();
            double dx = focus.getX() - eye.x;
            double dy = focus.getY() + focus.getEyeHeight() * 0.5 - eye.y;
            double dz = focus.getZ() - eye.z;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > 0.01) {
                float yaw = client.player.getYRot() * 0.017453292F;
                float pitch = client.player.getXRot() * 0.017453292F;
                double lx = -Math.sin(yaw) * Math.cos(pitch);
                double ly = -Math.sin(pitch);
                double lz = Math.cos(yaw) * Math.cos(pitch);
                // ~6° cone — a human keeping their crosshair on the target.
                aiming = (lx * dx + ly * dy + lz * dz) / dist
                        >= Math.cos(Math.toRadians(6.0));
            }
        }
        boolean attacking = client.options.keyAttack.isDown();
        if (aiming && attacking) {
            engagement = Math.min(1.0, engagement + 0.18 + RANDOM.nextDouble() * 0.14);
        } else {
            engagement = Math.max(0.0, engagement - 0.08 - RANDOM.nextDouble() * 0.06);
        }
    }

    // ── module orchestration ─────────────────────────────────────

    private void applyProfile(Minecraft client) {
        Set<String> desiredOn = new HashSet<>();
        if (fallDanger || clutchActive) {
            desiredOn.add("Clutch");
            if (projectileThreat) desiredOn.add("Aegis");
        } else {
            Profile profile = PROFILES[tactic];
            for (String name : profile.on) desiredOn.add(name);
            if (projectileThreat) desiredOn.add("Aegis");
        }

        boolean aegisModifier = projectileThreat && !desiredOn.contains("Aegis");

        for (String name : MANAGED) {
            if (manualModules.contains(name)) continue;
            Module m = QynlClient.getInstance().getModuleManager().find(name);
            if (m == null) continue;
            boolean wantOn = desiredOn.contains(name)
                    || (aegisModifier && "Aegis".equals(name));
            boolean dirOn = directorEnabled.containsKey(name) && directorEnabled.get(name);

            if (wantOn) {
                if (!m.isEnabled()) {
                    toggleTimers.put(name, 1 + RANDOM.nextInt(3));
                } else {
                    toggleTimers.remove(name);
                    if (!directorEnabled.containsKey(name)) {
                        directorEnabled.put(name, Boolean.FALSE); // user-owned
                    }
                }
            } else {
                if (dirOn && m.isEnabled()) {
                    toggleTimers.put(name, 1 + RANDOM.nextInt(3));
                } else {
                    toggleTimers.remove(name);
                }
            }
        }

        List<String> due = new ArrayList<>();
        for (Map.Entry<String, Integer> e : toggleTimers.entrySet()) {
            if (e.getValue() - 1 <= 0) due.add(e.getKey());
        }
        for (String name : due) {
            toggleTimers.remove(name);
            boolean wantOn = desiredOn.contains(name)
                    || (aegisModifier && "Aegis".equals(name));
            Module m = QynlClient.getInstance().getModuleManager().find(name);
            if (m != null && m.isEnabled() != wantOn) {
                m.setEnabled(wantOn);
                directorEnabled.put(name, wantOn);
            }
        }
    }

    /** Applies a tactic's parameter tuning to the live modules. */
    private void applyTuning(Minecraft client, Profile profile, boolean jitter) {
        if (profile == null || profile.tune.length == 0) return;
        boolean scaleAgg = tactic != T_EVADE && tactic != T_RETREAT;
        for (String[] entry : profile.tune) {
            Module m = QynlClient.getInstance().getModuleManager().find(entry[0]);
            if (m != null && !manualModules.contains(entry[0])) {
                String value = jitter ? jittered(entry[2]) : entry[2];
                if (scaleAgg) value = scaleByAggression(entry[1], value);
                m.applySetting(entry[1], value);
            }
        }
    }

    /** Scales numeric probability/strength knobs by the aggression slider. */
    private String scaleByAggression(String settingKey, String value) {
        if (!settingKey.equals("chance")
                && !settingKey.equals("horizontal") && !settingKey.equals("vertical")
                && !settingKey.equals("strength")) {
            return value;
        }
        try {
            double v = Double.parseDouble(value);
            double agg = getDoubleSetting("aggression");
            double k = 0.55 + (agg / 100.0) * 0.6;
            double scaled = Math.max(0.0, Math.min(100.0, v * k));
            return scaled == Math.floor(scaled)
                    ? String.valueOf((long) scaled)
                    : String.format("%.1f", scaled);
        } catch (NumberFormatException ignored) {
            return value;
        }
    }

    /** Nudges a numeric tuning value by ±3 so retunes never repeat exactly. */
    private static String jittered(String value) {
        try {
            double d = Double.parseDouble(value);
            double v = Math.max(0.0, d + (RANDOM.nextDouble() - 0.5) * 6.0);
            return v == Math.floor(v)
                    ? String.valueOf((long) v)
                    : String.format("%.1f", v);
        } catch (NumberFormatException ignored) {
            return value;
        }
    }

    private void parseManual() {
        manualModules.clear();
        String raw = getStringSetting("manual");
        if (raw == null) return;
        for (String part : raw.split(",")) {
            String name = part.trim();
            if (!name.isEmpty()) manualModules.add(name);
        }
    }

    // ── rendering: focus ring colored by tactic ──────────────────

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 camPos) {
        if (instance == null || !instance.isEnabled()
                || "Off".equals(instance.getStringSetting("render"))
                || instance.focus == null) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        float r, g, b;
        switch (instance.tactic) {
            case T_ENGAGE: r = 0.20f; g = 0.80f; b = 1.00f; break; // cyan
            case T_COMBO:  r = 0.30f; g = 0.90f; b = 0.50f; break; // green
            case T_TRADE:  r = 1.00f; g = 0.65f; b = 0.20f; break; // orange
            case T_DEFEND: r = 1.00f; g = 0.30f; b = 0.30f; break; // red
            case T_EVADE:  r = 1.00f; g = 0.85f; b = 0.25f; break; // yellow
            case T_RETREAT:r = 0.75f; g = 0.35f; b = 1.00f; break; // purple
            case T_SURVIVE:r = 0.55f; g = 1.00f; b = 0.85f; break; // mint
            default: return;
        }

        LivingEntity t = instance.focus;
        WorldDraw.line(poseStack, bufferSource, camPos,
                t.getX(), t.getY() + 0.05, t.getZ(),
                t.getX(), t.getY() + 2.0, t.getZ(),
                r, g, b, 0.5f);
        int segments = 20;
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0 * i / segments;
            double a1 = Math.PI * 2.0 * (i + 1) / segments;
            WorldDraw.line(poseStack, bufferSource, camPos,
                    t.getX() + Math.cos(a0) * 0.7, t.getY() + 0.05, t.getZ() + Math.sin(a0) * 0.7,
                    t.getX() + Math.cos(a1) * 0.7, t.getY() + 0.05, t.getZ() + Math.sin(a1) * 0.7,
                    r, g, b, 0.85f);
        }
    }
}
