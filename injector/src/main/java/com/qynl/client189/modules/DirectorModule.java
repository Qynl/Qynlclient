package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import com.qynl.client189.QynlClient189;
import com.qynl.client189.Setting;
import com.qynl.client189.WorldDraw;
import net.minecraft.block.Material;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.AbstractArrowEntity;
import net.minecraft.entity.thrown.EnderPearlEntity;
import net.minecraft.entity.thrown.PotionEntity;
import net.minecraft.entity.thrown.SnowballEntity;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.input.Keyboard;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * DIRECTOR — the combat AI. Not another muscle: the brain. It watches the
 * fight and decides, tick by tick, <b>which</b> of the client's combat
 * modules may act and <b>how hard</b> — so nothing ever runs constantly.
 *
 * <p>That is the entire safety idea: an anti-cheat's statistical heuristics
 * fingerprint modules that behave identically in every fight (block-hitting
 * at the same rate forever, sprint-resetting on every hit, crit-jumping on a
 * fixed rhythm). A human adapts — aggressive when the enemy is airborne,
 * defensive when being combo'd, evasive under fire, gone at low HP. DIRECTOR
 * reproduces that adaptation by switching <b>tactics</b> with humanized
 * reaction delays and only ever enabling the modules whose behavior is
 * plausible in the current situation:</p>
 * <ul>
 *   <li><b>Engage</b> — enemy closing: perfect-timing hits (Qynl), crit
 *       jumps (Criticals), strafing, gentle reach.</li>
 *   <li><b>Combo</b> — enemy airborne/knocked (or a 3+ hit winning streak):
 *       sustained DPS (AutoClicker), W-tap sprint-resets, enemy-air hits.</li>
 *   <li><b>Trade</b> — both grounded: timing + block-hit + medium velocity.</li>
 *   <li><b>Defend</b> — being combo'd: reactive block, heavy velocity
 *       reduction, evasion, no reckless jumps.</li>
 *   <li><b>Evade</b> — projectiles incoming: dodge, keep moving, no fighting.</li>
 *   <li><b>Retreat</b> — low health: sprint away, max damage reduction, no
 *       attacking.</li>
 *   <li><b>Survive</b> — fall/void/lava danger: Clutch is force-enabled, every
 *       combat module is stood down, nothing moves you off the save column.</li>
 * </ul>
 *
 * <p>Survival is layered on top of everything: the instant Clutch reports a
 * latched lethal fall or an active save, the DIRECTOR pre-empts its own
 * tactic and runs the Survive profile — combat modules can never fight a
 * falling player, and the save can never be sabotaged by a strafe or a
 * block-hit. Aegis is force-enabled the moment any projectile threatens,
 * inside any tactic. The DIRECTOR sends zero packets — it only decides. The
 * user's real module loadout is snapshotted on enable and restored on
 * disable, and saves always persist the user loadout, never the transient
 * tactic state.</p>
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
     *  profiles never run both at once (Qynl wins; it already defers to
     *  AutoClicker, and Hindsight defers to Qynl). Clutch is a survival
     *  module: the Director force-enables it in the Survive tactic and stands
     *  every combat module down while a save is in progress. */
    private static final String[] MANAGED = {
            "AimAssist", "AutoClicker", "Reach", "Velocity", "Sprint",
            "WTap", "BlockHit", "Qynl", "Hindsight", "Criticals", "Aegis",
            "StrafeAssist", "Clutch"
    };

    /** {module, setting, value} tuning per tactic. */
    private static final String[][] TUNE_ENGAGE = {
            {"Reach", "mode", "Normal"}, {"Reach", "choke", "On"},
            {"Velocity", "horizontal", "35"}, {"Velocity", "chance", "80"},
            {"WTap", "chance", "55"}, {"Criticals", "jumpRange", "3.6"},
            {"Criticals", "jump", "On"}, {"Criticals", "jumpChance", "75"},
            {"AimAssist", "strength", "80"}, {"AimAssist", "smoothness", "60"},
            {"StrafeAssist", "interval", "18"}
    };
    private static final String[][] TUNE_COMBO = {
            {"Reach", "mode", "Normal"}, {"Reach", "choke", "On"},
            {"Velocity", "horizontal", "20"}, {"Velocity", "vertical", "15"},
            {"Velocity", "chance", "80"}, {"WTap", "chance", "90"},
            {"WTap", "delay", "2"}, {"Criticals", "cps", "9"},
            {"Criticals", "jumpRange", "3.4"}, {"Criticals", "jumpChance", "80"},
            {"AimAssist", "strength", "95"}, {"AimAssist", "smoothness", "75"},
            {"StrafeAssist", "interval", "12"}
    };
    private static final String[][] TUNE_TRADE = {
            {"Reach", "mode", "Normal"}, {"Reach", "choke", "On"},
            {"Velocity", "horizontal", "45"}, {"Velocity", "vertical", "20"},
            {"Velocity", "chance", "75"}, {"WTap", "chance", "70"},
            {"BlockHit", "mode", "Both"}, {"BlockHit", "chance", "75"},
            {"BlockHit", "reactionMs", "130"}, {"Criticals", "jumpRange", "3.4"},
            {"Criticals", "jumpChance", "70"}, {"AimAssist", "strength", "85"},
            {"AimAssist", "smoothness", "65"}
    };
    private static final String[][] TUNE_DEFEND = {
            {"Reach", "mode", "Subtle"}, {"Reach", "choke", "Off"},
            {"Velocity", "horizontal", "65"}, {"Velocity", "vertical", "35"},
            {"Velocity", "chance", "90"}, {"BlockHit", "mode", "Reactive"},
            {"BlockHit", "chance", "90"}, {"BlockHit", "reactionMs", "100"},
            {"Criticals", "jump", "Off"}
    };
    private static final String[][] TUNE_EVADE = {
            {"Velocity", "horizontal", "50"}, {"Velocity", "vertical", "30"},
            {"Velocity", "chance", "85"}
    };
    private static final String[][] TUNE_RETREAT = {
            {"Velocity", "horizontal", "75"}, {"Velocity", "vertical", "40"},
            {"Velocity", "chance", "95"}, {"BlockHit", "mode", "Reactive"},
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
        PROFILES[T_NONE] = new Profile(new String[]{"Sprint"}, new String[]{
                "AimAssist", "AutoClicker", "Reach", "Velocity", "WTap",
                "BlockHit", "Qynl", "Hindsight", "Criticals", "Aegis",
                "StrafeAssist", "Clutch"}, new String[0][0]);
        PROFILES[T_ENGAGE] = new Profile(new String[]{
                "Sprint", "AimAssist", "StrafeAssist", "Criticals", "Qynl", "Reach"},
                new String[]{"AutoClicker", "WTap", "BlockHit", "Aegis",
                        "Hindsight", "Clutch"}, TUNE_ENGAGE);
        PROFILES[T_COMBO] = new Profile(new String[]{
                "Sprint", "AimAssist", "AutoClicker", "Criticals", "WTap",
                "StrafeAssist", "Reach", "Velocity"},
                new String[]{"Qynl", "Hindsight", "BlockHit", "Aegis", "Clutch"},
                TUNE_COMBO);
        PROFILES[T_TRADE] = new Profile(new String[]{
                "Sprint", "AimAssist", "Qynl", "BlockHit", "Reach", "Velocity",
                "Criticals", "StrafeAssist", "WTap"},
                new String[]{"AutoClicker", "Aegis", "Hindsight", "Clutch"},
                TUNE_TRADE);
        PROFILES[T_DEFEND] = new Profile(new String[]{
                "Sprint", "Velocity", "BlockHit", "Qynl", "Aegis", "StrafeAssist"},
                new String[]{"AutoClicker", "WTap", "Criticals", "Reach",
                        "AimAssist", "Hindsight", "Clutch"}, TUNE_DEFEND);
        PROFILES[T_EVADE] = new Profile(new String[]{
                "Sprint", "Aegis", "Velocity", "StrafeAssist"},
                new String[]{"AutoClicker", "WTap", "BlockHit", "Criticals",
                        "Qynl", "Hindsight", "Reach", "AimAssist", "Clutch"},
                TUNE_EVADE);
        PROFILES[T_RETREAT] = new Profile(new String[]{
                "Sprint", "Aegis", "Velocity", "BlockHit"},
                new String[]{"AutoClicker", "WTap", "Criticals", "Reach",
                        "StrafeAssist", "AimAssist", "Qynl", "Hindsight", "Clutch"},
                TUNE_RETREAT);
        // Survival: Clutch + Aegis only. No sprint (sprint = momentum = harder
        // to edge-save), no combat, no strafing off the save column.
        PROFILES[T_SURVIVE] = new Profile(new String[]{"Clutch", "Aegis"},
                new String[]{"Sprint", "AimAssist", "AutoClicker", "Reach",
                        "Velocity", "WTap", "BlockHit", "Qynl", "Hindsight",
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

    // pressure: how the current exchange is going (0 = clean, 3 = losing).
    // Rising edges of our own hurt vs. the focus enemy's hurt move it —
    // losing the exchange forces a defensive posture, winning allows
    // aggression. This is what makes the Director adapt mid-fight instead
    // of just switching on distance thresholds.
    private int pressure = 0;
    private boolean wasHurt = false;
    private boolean wasEnemyHurt = false;
    private int pressureDecayTimer = 0;

    // combo: consecutive hits we landed without being hit back. A 3+ streak
    // keeps the Director in the aggressive kit even on the ground — that is
    // what a real player does when they are winning the trade.
    private int combo = 0;
    private int exchangeIdleTicks = 0;

    // engagement 0..1: only the player's own intent — crosshair on the focus
    // enemy AND the attack key held — opens a fight. The Director never
    // starts combat by itself; outside a fight it 'sleeps' (T_NONE) until
    // the player actually decides to engage.
    private double engagement = 0.0;

    public DirectorModule() {
        super("Director",
                "Combat AI — reads the fight and decides which combat modules may act and how hard, per tactic (engage/combo/trade/defend/evade/retreat/survive). Nothing runs constantly, every switch is humanized, zero packets sent. Modules only ever activate in situations where their behavior is human-plausible — that is what keeps them unflagged. Clutch is force-enabled the moment a fall turns lethal and every combat module is stood down while you are saving yourself.",
                Category.COMBAT);
        instance = this;
        bindKey(Keyboard.KEY_B);
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
            Module m = QynlClient189.getInstance().getModuleManager().find(name);
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
        MinecraftClient client = MinecraftClient.getInstance();
        for (Map.Entry<String, Boolean> e : directorEnabled.entrySet()) {
            Boolean userWanted = userStates.get(e.getKey());
            if (userWanted == null) continue;
            if (e.getValue().booleanValue() && !userWanted.booleanValue()) {
                QynlClient189.getInstance().getModuleManager().setEnabled(e.getKey(), false);
            }
        }
        // Restore every setting the tactic tuning touched (Reach mode, Velocity
        // %, BlockHit mode/reaction, Criticals jump/cps, AimAssist strength,
        // ...) so the user's config is exactly as it was before the director
        // ran. applySetting ignores unknown keys, so a stale snapshot is safe.
        for (Map.Entry<String, Map<String, String>> e : userSettings.entrySet()) {
            Module m = QynlClient189.getInstance().getModuleManager().find(e.getKey());
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
    public void onTick(MinecraftClient client) {
        focus = null;
        if (client.player == null || client.world == null) return;
        // Freeze the tactic while a GUI screen is open: toggling combat
        // modules behind the ClickGUI would fight the user's own clicks in
        // the settings panel, and there is no fight to direct while a screen
        // is up anyway. State stays frozen and resumes on close.
        if (client.currentScreen != null) return;

        tickCounter++;
        assess(client);
        int want = decideTactic(client);

        // Human reaction: never switch tactics instantly; urgent defense
        // (being hit right now) and survival (falling right now) are the only
        // things that can preempt — a falling player doesn't wait to react.
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
            return; // still "thinking" — current tactic stays active
        }
        // Minimum hold so tactics don't flutter; urgent beats it.
        if (pendingTactic != tactic && (tacticHoldTicks >= 4
                || (hurtActive && enemyDist < 3.5)
                || fallDanger || clutchActive)) {
            tactic = pendingTactic;
            getSetting("mode").setFromString(TACTIC_NAMES[tactic]);
            applyTuning(client, PROFILES[tactic], false);
            tacticHoldTicks = 0;
        }
        tacticHoldTicks++;

        // Periodic re-tuning with a skip chance — parameters drift like a
        // player adjusting, never a static config. Numeric values get a
        // small random nudge so even the tuned numbers wander (Intave's
        // combat-flow analysis cannot lock onto a constant setup).
        if (--retuneTimer <= 0) {
            retuneTimer = 40 + RANDOM.nextInt(41);
            if (RANDOM.nextInt(100) >= 20) {
                applyTuning(client, PROFILES[tactic], true);
            }
        }

        applyProfile(client);
    }

    // ── situation assessment ─────────────────────────────────────

    private void assess(MinecraftClient client) {
        enemyDist = 999.0;
        enemyAirborne = false;
        enemyHpFrac = 1.0F;
        ownHpFrac = 1.0F;
        hurtActive = false;

        // Survival layer — read before the combat scan so a lethal fall or an
        // active save pre-empts every tactic decision below.
        fallDanger = ClutchModule.isInDanger();
        clutchActive = ClutchModule.isSaving();
        fireThreat = client.player.isOnFire() || client.player.isSubmergedIn(Material.LAVA);

        if (client.player.isAlive()) {
            ownHpFrac = client.player.getHealth() / client.player.getMaxHealth();
            hurtActive = client.player.hurtTime > 0;
            // Rising edge of being hit -> pressure up, combo broken.
            if (hurtActive && !wasHurt) {
                pressure = Math.min(3, pressure + 1);
                combo = 0;
            }
            wasHurt = hurtActive;
        }
        // Slow decay so pressure never sticks forever.
        if (--pressureDecayTimer <= 0) {
            pressureDecayTimer = 40;
            if (pressure > 0) pressure--;
        }

        // Nearest non-friend enemy.
        double bestSq = Double.MAX_VALUE;
        for (Entity entity : client.world.entities) {
            if (!(entity instanceof LivingEntity)) continue;
            LivingEntity living = (LivingEntity) entity;
            if (living == client.player || !living.isAlive()) continue;
            if (!(living instanceof MobEntity) && !(living instanceof PlayerEntity)) continue;
            if (FriendsModule.isFriend(FriendsModule.entityName(living))) continue;
            double dx = living.x - client.player.x;
            double dy = living.y - client.player.y;
            double dz = living.z - client.player.z;
            double dSq = dx * dx + dy * dy + dz * dz;
            if (dSq < bestSq) {
                bestSq = dSq;
                focus = living;
                enemyAirborne = !living.onGround || (living.y - living.prevY) < -0.2;
                enemyHpFrac = living.getHealth() / living.getMaxHealth();
            }
        }
        if (focus != null) {
            enemyDist = Math.sqrt(bestSq);
        }

        // Rising edge of the focus enemy's hurt animation -> pressure down
        // (we (or an ally) are landing hits on them). Runs AFTER the scan so
        // it evaluates the CURRENT tick's focus — onTick nulls focus before
        // assess() runs, so placed earlier this tracker could never fire.
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
        // A winning streak fades when the exchange ends (enemy gone for a
        // while) — never carry aggression into the next fight.
        if (focus == null) {
            if (++exchangeIdleTicks > 40) combo = 0;
        } else {
            exchangeIdleTicks = 0;
        }

        assessEngagement(client);

        // Any inbound projectile nearby? (Aegis force-enables inside any tactic.)
        projectileThreat = false;
        for (Entity e : client.world.entities) {
            if (!(e instanceof AbstractArrowEntity || e instanceof SnowballEntity
                    || e instanceof PotionEntity || e instanceof EnderPearlEntity)) continue;
            if (e.onGround) continue;
            double dx = e.x - client.player.x;
            double dz = e.z - client.player.z;
            if (dx * dx + dz * dz > 10.0 * 10.0) continue;
            double speed = Math.sqrt(e.velocityX * e.velocityX
                    + e.velocityY * e.velocityY + e.velocityZ * e.velocityZ);
            if (speed > 0.3) {
                projectileThreat = true;
                break;
            }
        }
    }

    private int decideTactic(MinecraftClient client) {
        // Survival pre-empts everything: while a lethal fall is latched or a
        // save is in progress, no combat module may act.
        if (fallDanger || clutchActive) return T_SURVIVE;
        if (focus == null) return T_NONE;
        double aggression = getDoubleSetting("aggression");
        double retreatHp = getDoubleSetting("retreatHp") / 100.0;

        if (ownHpFrac < retreatHp && enemyHpFrac > ownHpFrac + 0.05) {
            return T_RETREAT;
        }
        // Burning in lava at low HP: same as retreat — a player on fire runs,
        // they don't trade.
        if (fireThreat && ownHpFrac < 0.6) {
            return T_RETREAT;
        }
        // Never fight aggressively from water — humans don't jump-crit or
        // combo in a lake; they keep their distance and dodge.
        if (client.player.isSubmergedIn(Material.WATER)) {
            return enemyDist < 5.0 ? T_EVADE : T_NONE;
        }
        if (projectileThreat && enemyDist > 3.5) {
            return T_EVADE;
        }
        if (hurtActive && enemyDist < 3.5) {
            return T_DEFEND; // being combo'd right now
        }

        // ── fight-open gate ──────────────────────────────────────────
        // The Director never opens a fight by itself: entering the offensive
        // melee tactics (Engage/Combo/Trade) requires the engagement factor
        // above threshold — crosshair on the enemy + attack held for a few
        // ticks. Walking past an enemy, or pausing mid-fight, leaves the
        // client 'sleeping' (T_NONE) instead of flickering into combat.
        // Every defensive reaction above is exempt and never waits here.
        if (engagement < 0.5) {
            return T_NONE;
        }

        // Losing the exchange: two recent hits against us force a defensive
        // posture — no combos, no trades, just block + reduce + reposition.
        if (pressure >= 2) {
            return enemyDist < 5.0 ? T_DEFEND : T_EVADE;
        }
        if (enemyAirborne) {
            return T_COMBO; // they're knocked — press the advantage
        }
        // Winning streak on the ground: keep the aggressive kit going instead
        // of dropping to Trade — real players press when they're winning.
        if (combo >= 3 && enemyDist < 3.8) {
            return T_COMBO;
        }
        // Hysteresis on the melee boundary: once engaged at trade range, the
        // enemy has to clearly back off (or clearly close in) before the
        // tactic flutters — a human doesn't oscillate on a 3.4-block line.
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

    /**
     * Ramps the fight-engagement factor: it rises only while the crosshair is
     * on the focus enemy AND the attack key is held — the only signal that
     * the player actually opened the fight. It decays whenever the player
     * stops committing (click released, crosshair off, target gone). The
     * humanized ramp (not a fixed rate) is what keeps the gate from looking
     * like a boolean: the client wakes up after a few committed ticks and
     * falls back asleep after a short release.
     */
    private void assessEngagement(MinecraftClient client) {
        boolean aiming = false;
        if (focus != null && enemyDist < 6.0) {
            Vec3d eye = client.player.getCameraPosVec(1.0F);
            double dx = focus.x - eye.x;
            double dy = focus.y + focus.getEyeHeight() * 0.5 - eye.y;
            double dz = focus.z - eye.z;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > 0.01) {
                float yaw = client.player.yaw * 0.017453292F;
                float pitch = client.player.pitch * 0.017453292F;
                double lx = -Math.sin(yaw) * Math.cos(pitch);
                double ly = -Math.sin(pitch);
                double lz = Math.cos(yaw) * Math.cos(pitch);
                // ~6° cone — a human keeping their crosshair on the target.
                aiming = (lx * dx + ly * dy + lz * dz) / dist
                        >= Math.cos(Math.toRadians(6.0));
            }
        }
        boolean attacking = client.options.keyAttack.isPressed();
        if (aiming && attacking) {
            engagement = Math.min(1.0, engagement + 0.18 + RANDOM.nextDouble() * 0.14);
        } else {
            engagement = Math.max(0.0, engagement - 0.08 - RANDOM.nextDouble() * 0.06);
        }
    }

    // ── module orchestration ─────────────────────────────────────

    private void applyProfile(MinecraftClient client) {
        Set<String> desiredOn = new HashSet<>();
        if (fallDanger || clutchActive) {
            // Survival override: only Clutch (+ Aegis when projectiles are
            // inbound) may be on. Combat modules are stood down regardless of
            // which tactic is displayed — this runs every tick, so it also
            // covers the reaction-delay window before the tactic switches.
            desiredOn.add("Clutch");
            if (projectileThreat) desiredOn.add("Aegis");
        } else {
            Profile profile = PROFILES[tactic];
            for (String name : profile.on) desiredOn.add(name);
            if (projectileThreat) desiredOn.add("Aegis"); // dodge inside ANY tactic
        }

        boolean aegisModifier = projectileThreat && !desiredOn.contains("Aegis");

        for (String name : MANAGED) {
            if (manualModules.contains(name)) continue;
            Module m = QynlClient189.getInstance().getModuleManager().find(name);
            if (m == null) continue;
            // Aegis counts as wanted whenever a projectile threatens, even
            // in tactics whose profile does not list it (modifier).
            boolean wantOn = desiredOn.contains(name)
                    || (aegisModifier && "Aegis".equals(name));
            boolean dirOn = directorEnabled.containsKey(name) && directorEnabled.get(name);

            if (wantOn) {
                if (!m.isEnabled()) {
                    toggleTimers.put(name, 1 + RANDOM.nextInt(3)); // staggered
                } else {
                    toggleTimers.remove(name);
                    if (!directorEnabled.containsKey(name)) {
                        directorEnabled.put(name, Boolean.FALSE); // user-owned
                    }
                }
            } else {
                // Only switch off what the director itself turned on — a
                // module the user enabled manually stays as the user set it.
                if (dirOn && m.isEnabled()) {
                    toggleTimers.put(name, 1 + RANDOM.nextInt(3));
                } else {
                    toggleTimers.remove(name);
                }
            }
        }

        // Apply due toggles (never during iteration).
        java.util.List<String> due = new java.util.ArrayList<>();
        for (Map.Entry<String, Integer> e : toggleTimers.entrySet()) {
            if (e.getValue() - 1 <= 0) due.add(e.getKey());
        }
        for (String name : due) {
            toggleTimers.remove(name);
            boolean wantOn = desiredOn.contains(name)
                    || (aegisModifier && "Aegis".equals(name));
            QynlClient189.getInstance().getModuleManager().setEnabled(name, wantOn);
            directorEnabled.put(name, Boolean.valueOf(wantOn));
        }
    }

    /** Applies a tactic's parameter tuning to the live modules. */
    private void applyTuning(MinecraftClient client, Profile profile, boolean jitter) {
        if (profile == null || profile.tune.length == 0) return;
        // Retreat/Evade tuning stays exact — when you are running or dodging,
        // the reduction values are deliberately high and the aggression
        // slider must not weaken them (or push them past 100).
        boolean scaleAgg = tactic != T_EVADE && tactic != T_RETREAT;
        for (String[] entry : profile.tune) {
            Module m = QynlClient189.getInstance().getModuleManager().find(entry[0]);
            if (m != null && !manualModules.contains(entry[0])) {
                String value = jitter ? jittered(entry[2]) : entry[2];
                if (scaleAgg) value = scaleByAggression(entry[1], value);
                m.applySetting(entry[1], value);
            }
        }
    }

    /**
     * Scales the numeric probability/strength knobs by the aggression slider
     * (0.55× at 0% aggression → 1.15× at 100%), clamped to 0–100. This is
     * what makes the slider live during a fight: high aggression means the
     * tuned modules act more often and reduce knockback harder; low aggression
     * sands everything down into the human-typical range. Modes and ranges
     * (Reach mode, BlockHit mode, jump on/off) are never scaled.
     */
    private String scaleByAggression(String settingKey, String value) {
        if (!settingKey.equals("chance") && !settingKey.equals("jumpChance")
                && !settingKey.equals("horizontal") && !settingKey.equals("vertical")
                && !settingKey.equals("strength") && !settingKey.equals("cps")) {
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
            return value; // options like "On"/"Normal" stay exact
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
            return value; // options like "On"/"Normal" stay exact
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

    public static void render(float partialTicks) {
        if (instance == null || !instance.isEnabled()
                || "Off".equals(instance.getStringSetting("render"))
                || instance.focus == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

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

        double camX = client.player.prevX + (client.player.x - client.player.prevX) * partialTicks;
        double camY = client.player.prevY + (client.player.y - client.player.prevY) * partialTicks;
        double camZ = client.player.prevZ + (client.player.z - client.player.prevZ) * partialTicks;

        LivingEntity t = instance.focus;
        double ex = t.x - camX;
        double ey = t.y - camY;
        double ez = t.z - camZ;

        WorldDraw.begin(false);
        // Vertical pole so the focus marker stays readable through fights.
        WorldDraw.line(ex, ey + 0.05, ez, ex, ey + 2.0, ez, r, g, b, 0.5f);
        int segments = 20;
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0 * i / segments;
            double a1 = Math.PI * 2.0 * (i + 1) / segments;
            WorldDraw.line(
                    ex + Math.cos(a0) * 0.7, ey + 0.05, ez + Math.sin(a0) * 0.7,
                    ex + Math.cos(a1) * 0.7, ey + 0.05, ez + Math.sin(a1) * 0.7,
                    r, g, b, 0.85f);
        }
        WorldDraw.end();
    }
}
