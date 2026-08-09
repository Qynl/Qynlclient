package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import com.qynl.client189.Setting;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.input.Keyboard;

/**
 * Chronostasis — frame-splitting slow-motion for 1.8.9.
 *
 * <p><b>The problem this solves:</b> lowering {@code timer.timerSpeed} (or the
 * tick rate) slows down <i>both</i> rendering and the network tick, so the
 * server stops receiving packets and kicks you. Chronostasis does the exact
 * opposite of that:</p>
 *
 * <ul>
 *   <li><b>The server tick keeps running at 100%.</b> The game logic
 *       (movement, packets, combat) is never touched — the server sees a
 *       perfectly normal 20 TPS player, so there is nothing to kick or ban.
 *       The whole effect is achieved by re-scaling only the <i>display</i>
 *       interpolation factor (the timer's {@code tickDelta}, which the render
 *       pipeline uses to interpolate entity positions between ticks).</li>
 *   <li><b>Slow-motion view.</b> While active, entity and world interpolation
 *       advances at only {@code factor}× of its real speed (default 10%), so
 *       everything on screen moves in extreme slow-mo while the game itself
 *       runs normally underneath. You get all the aiming time you need.</li>
 *   <li><b>Cinematic aim (mouse decoupling).</b> Your real mouse rotation is
 *       still applied to the player entity every frame, so the movement/look
 *       packets that go out each tick carry your <i>real-time</i> aim. The
 *       rendered camera is fed a smoothly-panned version of that rotation
 *       instead — on screen the turn plays out buttery-slow, while the server
 *       already knows where you snapped to. (See {@link #beginCamera()}.)</li>
 *   <li><b>Catch-up ("Vorspulen").</b> On release/disable the factor ramps
 *       from the slow value up past 1.0× (~1.6×, brief fast-forward) and back
 *       to 1.0 over a handful of frames, so the view re-syncs with the real
 *       game state smoothly instead of snapping.</li>
 * </ul>
 *
 * <p>The heavy lifting lives in two mixins:</p>
 * <ul>
 *   <li>{@link com.qynl.client189.mixin.ChronoTimerMixin} — scales
 *       {@code ClientTickTracker.tickDelta} at the end of
 *       {@code ClientTickTracker.tick()}.</li>
 *   <li>{@link com.qynl.client189.mixin.ChronoCameraMixin} — swaps the
 *       player's rendered rotation for the smoothed pan around
 *       {@code GameRenderer.setupCamera(float, int)}.</li>
 * </ul>
 */
public class ChronostasisModule extends Module {
    private static ChronostasisModule instance;

    /** How many render frames the catch-up fast-forward lasts. */
    private static final int CATCHUP_FRAMES = 12;
    /** How many render frames it takes to ease from 1.0× down to the slow factor. */
    private static final int EASE_FRAMES = 5;
    /** Peak multiplier during the catch-up fast-forward (brief overshoot > 1). */
    private static final float CATCHUP_PEAK = 1.6F;

    private static int catchUpFrames;
    private static int easeInFrames;

    // Cinematic camera state — the rendered rotation is smoothed toward the
    // player's real rotation; the real one is restored after the camera is
    // drawn so packets still carry the true aim.
    private static boolean cameraCaptured;
    private static boolean firstCameraFrame = true;
    private static long lastCameraNanos;
    private static float realYaw;
    private static float realPitch;
    private static float smoothYaw;
    private static float smoothPitch;

    public ChronostasisModule() {
        super("Chronostasis",
                "Frame-splitting slow-motion: renders the world at a fraction of its speed while the "
                        + "server tick runs at 100% — no kicks, all the aiming time you need. Cinematic "
                        + "aim pans the view smoothly while your real rotation still reaches the server "
                        + "in real time.",
                Category.ASSIST);
        instance = this;
        bindKey(Keyboard.KEY_Z);
        addSetting(Setting.options("mode", "Mode", "Hold", "Hold", "Toggle"));
        addSetting(Setting.range("factor", "Slow-mo", 0.10, 0.05, 0.50, 0.05, "x"));
        addSetting(Setting.options("cinematic", "Cinematic aim", "On", "On", "Off"));
        addSetting(Setting.range("cam", "Pan speed", 14.0, 4.0, 40.0, 1.0));
        addSetting(Setting.options("catchup", "Catch-up", "On", "On", "Off"));
    }

    public static ChronostasisModule getInstance() { return instance; }
    public static boolean isActive() { return instance != null && instance.isEnabled(); }

    /**
     * The multiplier that should be applied to the render partial ticks this
     * frame. 1.0 = normal speed (module off / idle). Called once per render
     * frame from {@code ChronoTimerMixin}.
     */
    public static float currentFactor() {
        if (instance == null) return 1.0F;

        if (instance.isEnabled()) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.world == null) return 1.0F;
            if (easeInFrames < EASE_FRAMES) easeInFrames++;
            float slow = (float) instance.getDoubleSetting("factor");
            slow = Math.max(0.02F, Math.min(0.95F, slow));
            // Ease from 1.0× down to the slow factor so the world melts into
            // slow-mo instead of freezing instantly.
            float e = Math.min(1.0F, easeInFrames / (float) EASE_FRAMES);
            return 1.0F + (slow - 1.0F) * e;
        }

        if (catchUpFrames > 0) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.world == null) {
                catchUpFrames = 0;
                return 1.0F;
            }
            catchUpFrames--;
            // 0 → 1 across the catch-up window.
            float p = 1.0F - catchUpFrames / (float) CATCHUP_FRAMES;
            float slow = Math.max(0.02F, (float) instance.getDoubleSetting("factor"));
            float f;
            if (p < 0.5F) {
                // Slow → peak (the brief 160% fast-forward).
                f = slow + (CATCHUP_PEAK - slow) * (p * 2.0F);
            } else {
                // Peak → 1.0 (settle back to real time).
                f = CATCHUP_PEAK + (1.0F - CATCHUP_PEAK) * ((p - 0.5F) * 2.0F);
            }
            return Math.max(0.02F, Math.min(CATCHUP_PEAK, f));
        }

        return 1.0F;
    }

    /**
     * Called at the head of {@code GameRenderer.setupCamera}. The mouse look
     * has already been applied to the player this frame, so the entity holds
     * the <i>real</i> rotation. We capture it, blend the smoothed pan toward
     * it (time-based exponential smoothing, FPS-independent) and feed the
     * smoothed rotation to the camera. The real rotation is restored in
     * {@link #endCamera()}, so movement packets keep carrying the true aim.
     */
    public static void beginCamera() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || instance == null || !instance.isEnabled()) return;
        if (!"On".equals(instance.getStringSetting("cinematic"))) return;

        realYaw = mc.player.yaw;
        realPitch = mc.player.pitch;
        cameraCaptured = true;

        long now = System.nanoTime();
        float dt = lastCameraNanos == 0L ? 1.0F / 60.0F
                : Math.min(0.05F, (now - lastCameraNanos) / 1.0e9F);
        lastCameraNanos = now;

        if (firstCameraFrame) {
            smoothYaw = realYaw;
            smoothPitch = realPitch;
            firstCameraFrame = false;
        }

        float rate = (float) instance.getDoubleSetting("cam");
        float s = 1.0F - (float) Math.exp(-dt * rate);

        // Yaw wrap-around safe blend (shortest angular path across ±180°).
        float dy = ((realYaw - smoothYaw) % 360.0F + 540.0F) % 360.0F - 180.0F;
        smoothYaw += dy * s;
        smoothPitch += (realPitch - smoothPitch) * s;

        mc.player.yaw = smoothYaw;
        mc.player.pitch = smoothPitch;
    }

    /** Restores the player's real rotation after the camera has been drawn. */
    public static void endCamera() {
        if (!cameraCaptured) return;
        cameraCaptured = false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.yaw = realYaw;
            mc.player.pitch = realPitch;
        }
    }

    @Override
    public void onEnable() {
        easeInFrames = 0;
        firstCameraFrame = true;
        lastCameraNanos = 0L;
    }

    @Override
    public void onDisable() {
        easeInFrames = EASE_FRAMES;
        if ("On".equals(getStringSetting("catchup"))) {
            MinecraftClient mc = MinecraftClient.getInstance();
            catchUpFrames = (mc != null && mc.player != null && mc.world != null)
                    ? CATCHUP_FRAMES : 0;
        } else {
            catchUpFrames = 0;
        }
        firstCameraFrame = true;
        lastCameraNanos = 0L;
    }

    @Override
    public void onTick(MinecraftClient client) {
        // Hold mode: the module only stays active while the bind key is held.
        if (getKeyBinding() == null) return;
        if ("Hold".equals(getStringSetting("mode"))
                && !getKeyBinding().isPressed() && isEnabled()) {
            setEnabled(false);
        }
    }
}
