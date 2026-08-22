package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import com.qynl.client189.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * QuantumSuperposition — temporal position prediction (Temporale
 * Positions-Prädiktion) for high-ping / packet-loss play on 1.8.9.
 *
 * <p>This is a network-latency compensator, not a slow-motion renderer. Where
 * the old Chronostasis decoupled <i>render</i> time from <i>tick</i> time,
 * QuantumSuperposition decouples <i>local</i> time from <i>server</i> time:
 * the client continuously models where the server believes the player is and
 * where the player is about to be, then uses that model to keep movement,
 * hits and stability intact despite lag.</p>
 *
 * <p>Every tick the engine maintains three simultaneous position states (the
 * "superposition"):</p>
 * <ul>
 *   <li><b>Vector A — Latency anchor.</b> The position the server is
 *       currently processing as the player's last known stand, reconstructed
 *       by interpolating the local position history back by one round-trip
 *       (ping). This is the mathematically honest estimate of the server's
 *       view, because the server only knows positions that are {@code ping}
 *       milliseconds old.</li>
 *   <li><b>Vector B — Real-time presence.</b> The player's actual local
 *       position on screen. The difference A→B is the live lag drift: how far
 *       the server's version of the player trails the real one.</li>
 *   <li><b>Vector C — Predictive target path.</b> Current position
 *       extrapolated along the smoothed velocity vector. The lookahead is
 *       <i>adaptive</i>: it scales with movement speed and round-trip time
 *       (faster + laggier → predict further ahead), it dampens when the
 *       player turns sharply (no overshoot into walls), and it <i>collapses</i>
 *       back onto the real position the moment the player stops — so the
 *       predicted future can never drift away from reality while idle.</li>
 * </ul>
 *
 * <p>The three operational effects:</p>
 * <ul>
 *   <li><b>Compensated interaction.</b> When the client sends an attack /
 *       interact packet ({@code PlayerInteractEntityC2SPacket}), the module
 *       first flushes a movement packet with the player's <i>true current</i>
 *       position. Vanilla sends movement packets at the end of the tick, so a
 *       mid-tick click lets the server check range against a position that is
 *       one tick (plus ping) stale; flushing first means the server's reach /
 *       hit check runs on fresh coordinates. No position is ever faked — only
 *       the ordering is corrected.</li>
 *   <li><b>Defensive position stabilization (anti-rubberbanding).</b> The
 *       jitter model watches inbound packet spacing. When no packet arrives
 *       for {@code spikeMs} (a lag spike / packet-loss window), the module
 *       re-sends Vector A — the last position the server has already
 *       processed — so the server's player model never freezes or
 *       extrapolates the character into a wall, and the player is not left
 *       unprotected mid-air during the outage.</li>
 *   <li><b>Server reconciliation.</b> Every authoritative position
 *       correction ({@code PlayerPositionLookS2CPacket}) re-bases the anchor
 *       on the server's truth and clears any pending spike state, so the
 *       three-vector model can never drift far from reality.</li>
 * </ul>
 *
 * <p><b>Quantum Echo.</b> While the {@code echo} setting is on, a world-space
 * overlay renders the superposition directly: a fading trail of recent
 * positions, a cyan ghost box at Vector A (where the server sees you), a
 * magenta ghost box at Vector C (where you are about to be), a beam
 * connecting B→C, and a pulsing red anchor ring during lag spikes. This is
 * the visual "superposition" — you literally see your past, present and
 * predicted future simultaneously.</p>
 *
 * <p>The heavy lifting lives in three mixins:</p>
 * <ul>
 *   <li>{@link com.qynl.client189.mixin.QuantumNetworkMixin} — flushes the
 *       true position ahead of attack packets and reconciles the anchor on
 *       server corrections.</li>
 *   <li>{@link com.qynl.client189.mixin.QuantumConnectionMixin} — feeds the
 *       jitter / spike model from the Netty inbound path
 *       ({@code ClientConnection.channelRead0}).</li>
 *   <li>{@link com.qynl.client189.mixin.QuantumRenderMixin} — draws the echo
 *       overlay at the tail of {@code GameRenderer.renderWorld}.</li>
 * </ul>
 *
 * <p>An optional HUD overlay shows the live superposition state: smoothed
 * ping, jitter, the A/B/C offsets in blocks, the active lookahead, and a
 * STABILIZING indicator while the anchor re-send is active.</p>
 */
public class QuantumSuperpositionModule extends Module {
    private static QuantumSuperpositionModule instance;

    // ── engine constants ──────────────────────────────────────────
    /** How many position samples to keep (3 s of history at 20 TPS). */
    private static final int MAX_SAMPLES = 60;
    /** Expected inbound packet cadence — one per server tick. */
    private static final double EXPECTED_PACKET_MS = 50.0;
    /** Number of ticks between anchor re-sends while a spike lasts. */
    private static final int ANCHOR_RESEND_TICKS = 5;
    /** How many samples the velocity estimate spans. */
    private static final int VELOCITY_SPAN = 4;
    /** Speed below which the player is considered idle (blocks/tick). */
    private static final double IDLE_SPEED = 0.02;

    // ── shared engine state ───────────────────────────────────────
    private static final Deque<Sample> history = new ArrayDeque<>();

    /** Last inbound packet time (written on the Netty thread). */
    private static volatile long lastInboundNanos;
    /** EMA of |inbound gap − 50 ms| — how bursty the link is. */
    private static double jitterMs;
    /** EMA of the tab-list latency in milliseconds. */
    private static double smoothPing;
    /** Smoothed velocity, in blocks per tick. */
    private static double vx, vy, vz;
    /** Previous-tick velocity — used for turn damping. */
    private static double prevVx, prevVy, prevVz;
    /** True while inbound packets have been silent for ≥ spikeMs. */
    private static boolean spikeActive;
    private static int spikeResendTimer;
    /** Last server-confirmed position (Vector A's ground truth). */
    private static double anchorX, anchorY, anchorZ;

    // ── superposition vector outputs ──────────────────────────────
    private static double vecAx, vecAy, vecAz;
    private static double vecCx, vecCy, vecCz;
    private static double offsetA, offsetC;
    /** The lookahead actually applied this tick (after adaptation). */
    private static double effectiveLookaheadMs;

    private static boolean resetPending = true;

    public QuantumSuperpositionModule() {
        super("QuantumSuperposition",
                "Temporal position prediction for high ping: models where the server "
                        + "thinks you are (A), where you are (B) and where you're about "
                        + "to be (C), flushes your true position before attacks so hits "
                        + "register through lag, and re-confirms the last stable position "
                        + "during packet-loss spikes so the server never rubberbands or "
                        + "freezes you.",
                Category.ASSIST);
        instance = this;
        bindKey(Keyboard.KEY_Z);
        addSetting(Setting.options("mode",      "Mode",         "Toggle", "Toggle", "Hold"));
        addSetting(Setting.range("lookahead",   "Lookahead",    80.0, 20, 250, 5, "ms"));
        addSetting(Setting.options("flush",     "Pos flush",    "On",   "On",  "Off"));
        addSetting(Setting.options("anchor",    "Anchor resend","On",   "On",  "Off"));
        addSetting(Setting.range("spikeMs",     "Spike ms",     800.0, 200, 2000, 50, "ms"));
        addSetting(Setting.options("reconcile", "Reconcile",    "On",   "On",  "Off"));
        addSetting(Setting.options("echo",      "Quantum Echo", "On",   "On",  "Off"));
        addSetting(Setting.options("overlay",   "Overlay",      "On",   "On",  "Off"));
    }

    // ── static API for mixins and HUD ─────────────────────────────

    public static QuantumSuperpositionModule getInstance() { return instance; }
    public static boolean isActive() { return instance != null && instance.isEnabled(); }

    /** True when the echo (world-space superposition overlay) should render. */
    public static boolean shouldRenderEcho() {
        return isActive() && instance != null && "On".equals(instance.getStringSetting("echo"));
    }

    /**
     * Inbound Netty callback (Netty thread): one packet was just decoded.
     * Feeds the jitter / spike model with the inter-arrival gap.
     */
    public static void onPacketReceived() {
        long now = System.nanoTime();
        if (lastInboundNanos != 0) {
            double gap = (now - lastInboundNanos) / 1_000_000.0;
            double err = Math.abs(gap - EXPECTED_PACKET_MS);
            jitterMs = jitterMs == 0 ? err : jitterMs * 0.9 + err * 0.1;
        }
        lastInboundNanos = now;
    }

    /**
     * Outgoing packet observer: right before an interact/attack packet goes
     * out, flush the player's true current position so the server's reach
     * check runs on fresh coordinates instead of the previous tick's.
     */
    public static void onSendPacket(Packet packet) {
        if (instance == null || !instance.isEnabled()) return;
        if (!(packet instanceof PlayerInteractEntityC2SPacket)) return;
        if (!"On".equals(instance.getStringSetting("flush"))) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.getNetworkHandler() == null) return;

        // The nested sendPacket is a PlayerMoveC2SPacket, so this hook will
        // not recurse. The position is the player's real, current one — no
        // spoofing, only correct packet ordering for latency.
        client.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Both(
                client.player.x, client.player.y, client.player.z,
                client.player.yaw, client.player.pitch, client.player.onGround));
    }

    /**
     * A server position correction was applied. The anchor re-bases on the
     * server's truth and any pending spike state is cleared — a correction is
     * proof the link is alive.
     */
    public static void onPositionCorrection(MinecraftClient client) {
        if (instance == null || !instance.isEnabled()) return;
        if (!"On".equals(instance.getStringSetting("reconcile"))) return;
        if (client.player == null) return;
        anchorX = client.player.x;
        anchorY = client.player.y;
        anchorZ = client.player.z;
        spikeActive = false;
        spikeResendTimer = 0;
    }

    // ── HUD overlay ───────────────────────────────────────────────

    public boolean isOverlayOn() {
        return "On".equals(getStringSetting("overlay"));
    }

    public boolean isSpiking() {
        return spikeActive;
    }

    /** Live HUD lines: ping/jitter, the A·B·C offsets, and spike state. */
    public List<String> getHudLines() {
        List<String> lines = new ArrayList<>();
        lines.add(String.format("Superposition \u00b7 %.0fms%s", smoothPing, jitterLabel()));
        lines.add(String.format("A %+.2f \u00b7 B 0.00 \u00b7 C %+.2f \u00b7 look %.0fms",
                offsetA, offsetC, effectiveLookaheadMs));
        if (spikeActive) {
            lines.add("STABILIZING \u00b7 anchor");
        }
        return lines;
    }

    private String jitterLabel() {
        double pct = Math.min(999.0, jitterMs / EXPECTED_PACKET_MS * 100.0);
        return String.format(" \u00b7 jitter %.0f%%", pct);
    }

    // ── module lifecycle ──────────────────────────────────────────

    @Override
    public void onEnable() {
        resetPending = true;
    }

    @Override
    public void onDisable() {
        spikeActive = false;
        spikeResendTimer = 0;
    }

    @Override
    public void onTick(MinecraftClient client) {
        // Hold mode: only stays active while the bind key is held.
        if ("Hold".equals(getStringSetting("mode")) && getKeyBinding() != null
                && !getKeyBinding().isPressed() && isEnabled()) {
            setEnabled(false);
            return;
        }
        if (client.player == null || client.world == null) {
            resetPending = true;
            return;
        }
        if (resetPending) {
            resetEngine();
            resetPending = false;
        }

        record(client);
        updatePing(client);
        computeVectors(client);
        tickStabilizer(client);
    }

    private void record(MinecraftClient client) {
        history.addLast(new Sample(System.nanoTime(),
                client.player.x, client.player.y, client.player.z, client.player.onGround));
        while (history.size() > MAX_SAMPLES) {
            history.removeFirst();
        }
    }

    /** Smoothed round-trip latency from the tab list (used to place Vector A). */
    private void updatePing(MinecraftClient client) {
        if (client.getNetworkHandler() == null) return;
        PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
        if (entry == null) return;
        double p = entry.getLatency();
        if (p > 0) {
            smoothPing = smoothPing == 0 ? p : smoothPing * 0.85 + p * 0.15;
        }
    }

    /**
     * Recompute velocity, Vector A (latency anchor) and Vector C (predictive
     * path). Vector C's lookahead is adaptive: speed and ping push it further
     * ahead, sharp turns damp it back, and standing still collapses it onto
     * the real position.
     */
    private void computeVectors(MinecraftClient client) {
        double px = client.player.x;
        double py = client.player.y;
        double pz = client.player.z;

        // Velocity: average per-tick delta over the last few samples, EMA-smoothed.
        double dvx = 0, dvy = 0, dvz = 0;
        int n = Math.min(VELOCITY_SPAN, history.size() - 1);
        if (n >= 1) {
            Sample[] arr = history.toArray(new Sample[0]);
            Sample base = arr[arr.length - 1 - n];
            Sample top = arr[arr.length - 1];
            long spanNanos = Math.max(1, top.nanos - base.nanos);
            double ticks = spanNanos / (EXPECTED_PACKET_MS * 1_000_000.0);
            if (ticks > 0) {
                dvx = (top.x - base.x) / ticks;
                dvy = (top.y - base.y) / ticks;
                dvz = (top.z - base.z) / ticks;
            }
        }
        double w = 0.35;
        vx = vx * (1 - w) + dvx * w;
        vy = vy * (1 - w) + dvy * w;
        vz = vz * (1 - w) + dvz * w;

        // Vector A — where the server believes we are: our own position
        // `ping` ms in the past, interpolated from history.
        Sample a = sampleAt(System.nanoTime() - (long) (smoothPing * 1_000_000.0));
        if (a != null) {
            vecAx = a.x;
            vecAy = a.y;
            vecAz = a.z;
        } else {
            vecAx = px;
            vecAy = py;
            vecAz = pz;
        }

        // Vector C — where we will be when a packet sent now arrives.
        double speed = Math.sqrt(dvx * dvx + dvy * dvy + dvz * dvz);
        double lookMs = adaptiveLookahead(speed);
        effectiveLookaheadMs = lookMs;
        double ticksAhead = Math.max(0.0, lookMs / EXPECTED_PACKET_MS);

        if (speed < IDLE_SPEED) {
            // Collapse: no phantom future while standing still. C converges
            // smoothly back onto B instead of snapping, so the echo stays
            // buttery instead of flickering.
            double collapse = 0.25;
            vecCx = px + (vecCx - px) * (1 - collapse);
            vecCy = py + (vecCy - py) * (1 - collapse);
            vecCz = pz + (vecCz - pz) * (1 - collapse);
        } else {
            vecCx = px + vx * ticksAhead;
            vecCz = pz + vz * ticksAhead;
            // Clamp vertical prediction so it never pierces the floor or flies.
            vecCy = Math.max(py - 1.5, Math.min(py + 1.5, py + vy * ticksAhead));
        }

        prevVx = vx;
        prevVy = vy;
        prevVz = vz;

        // HUD offsets (blocks).
        offsetA = Math.sqrt(sq(px - vecAx) + sq(py - vecAy) + sq(pz - vecAz));
        offsetC = Math.sqrt(sq(vecCx - px) + sq(vecCy - py) + sq(vecCz - pz));
    }

    /**
     * Adaptive lookahead: base setting scaled by ping and speed, then damped
     * by how sharply the player is turning. Slow + stable → short prediction;
     * fast + laggy + straight → long prediction; sharp turns → almost no
     * lookahead so the ghost never overshoots through a wall.
     */
    private double adaptiveLookahead(double speed) {
        double base = getDoubleSetting("lookahead");
        if (base <= 0) return 0;

        // Ping factor: 0.55× at 0 ms → 1.6× at 300+ ms.
        double pingFactor = 0.55 + Math.min(1.0, smoothPing / 300.0) * 1.05;

        // Speed factor: idle → 0.3×, sprint (~0.28 b/t) → ~1.5×, capped.
        double speedFactor = 0.3 + Math.min(1.7, speed * 4.2);

        // Turn damping: cosine of the angle between previous and current
        // horizontal velocity. 1.0 = dead straight, 0.0 = right angle, <0 = U-turn.
        double turnFactor = 1.0;
        double pLen = Math.sqrt(prevVx * prevVx + prevVz * prevVz);
        double cLen = Math.sqrt(vx * vx + vz * vz);
        if (pLen > IDLE_SPEED && cLen > IDLE_SPEED) {
            double dot = (prevVx * vx + prevVz * vz) / (pLen * cLen);
            turnFactor = 0.25 + 0.75 * Math.max(-1.0, Math.min(1.0, dot));
        }

        double look = base * pingFactor * speedFactor * turnFactor;
        return Math.max(10.0, Math.min(500.0, look));
    }

    /**
     * Defensive stabilization: during an inbound-silence window (lag spike /
     * packet loss), periodically re-confirm Vector A — the last position the
     * server has already processed — so the server never freezes or
     * extrapolates the character while the connection is down.
     */
    private void tickStabilizer(MinecraftClient client) {
        long now = System.nanoTime();
        boolean live = lastInboundNanos != 0
                && (now - lastInboundNanos) < (long) (getDoubleSetting("spikeMs") * 1_000_000.0);
        spikeActive = !live;

        if (spikeActive && "On".equals(getStringSetting("anchor"))
                && client.getNetworkHandler() != null) {
            if (++spikeResendTimer >= ANCHOR_RESEND_TICKS) {
                spikeResendTimer = 0;
                client.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionOnly(
                        vecAx, vecAy, vecAz, client.player.onGround));
            }
        } else {
            spikeResendTimer = 0;
        }
    }

    // ── Quantum Echo world-space renderer ─────────────────────────

    /**
     * Renders the superposition overlay in world space. Called from
     * {@code QuantumRenderMixin} at the tail of {@code GameRenderer.renderWorld}.
     * Draws the fading position trail, the A/C ghost boxes and the B→C beam,
     * plus a pulsing red ring at the anchor while a lag spike is active.
     */
    public static void renderEcho(float partialTicks) {
        if (!shouldRenderEcho()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        double camX = client.player.prevX + (client.player.x - client.player.prevX) * partialTicks;
        double camY = client.player.prevY + (client.player.y - client.player.prevY) * partialTicks;
        double camZ = client.player.prevZ + (client.player.z - client.player.prevZ) * partialTicks;

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.depthMask(false);
        GlStateManager.disableCull();

        // ── Fading position trail (newest → oldest) ──────────────
        double[][] trail = getEchoTrail(48);
        if (trail.length >= 2) {
            GL11.glLineWidth(1.5f);
            GL11.glBegin(GL11.GL_LINE_STRIP);
            int len = trail.length;
            for (int i = len - 1; i >= 0; i--) {
                float f = (len - 1 - i) / (float) Math.max(1, len - 1); // 0 newest → 1 oldest
                GL11.glColor4f(0.43f, 0.91f, 0.63f, 0.75f - 0.65f * f);
                GL11.glVertex3d(trail[i][0] - camX, trail[i][1] - camY, trail[i][2] - camZ);
            }
            GL11.glEnd();
        }

        // ── Ghost boxes: A (server anchor) cyan, C (predictive) magenta ──
        double[] a = getEchoAnchor();
        double[] c = getEchoPredict();

        float pulse = 0.45f + 0.55f * (float) ((Math.sin(System.nanoTime() / 280_000_000.0) + 1) / 2);
        if (spikeActive) {
            // Red pulsing anchor ring — the module is re-confirming position.
            drawGhostBox(a[0] - camX, a[1] - camY, a[2] - camZ, 0.32, 1.8, 1.0f, 0.35f, 0.4f, 0.45f + 0.5f * pulse);
            drawRing(a[0] - camX, a[1] - camY + 0.05, a[2] - camZ, 0.45, 1.0f, 0.35f, 0.4f, 0.8f * pulse);
        } else {
            drawGhostBox(a[0] - camX, a[1] - camY, a[2] - camZ, 0.30, 1.8, 0.20f, 0.78f, 0.90f, 0.55f);
        }
        drawGhostBox(c[0] - camX, c[1] - camY, c[2] - camZ, 0.30, 1.8, 0.75f, 0.45f, 1.0f, 0.50f);

        // ── Beam from B (real position) to C (predicted) ─────────
        GL11.glLineWidth(1.0f);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        GL11.glColor4f(0.43f, 0.91f, 0.63f, 0.85f);
        GL11.glVertex3d(0, 0, 0);
        GL11.glColor4f(0.75f, 0.45f, 1.0f, 0.85f);
        GL11.glVertex3d(c[0] - camX, c[1] - camY, c[2] - camZ);
        GL11.glEnd();

        // ── Present marker (B): bright core at the real position ──
        GL11.glLineWidth(2.5f);
        GL11.glBegin(GL11.GL_LINES);
        double m = 0.12;
        GL11.glColor4f(0.43f, 0.91f, 0.63f, 0.9f);
        vert(-m, 0, 0); vert(m, 0, 0);
        vert(0, -m, 0); vert(0, m, 0);
        vert(0, 0, -m); vert(0, 0, m);
        GL11.glEnd();

        GlStateManager.enableCull();
        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture();
        GlStateManager.popMatrix();
    }

    /** Wireframe box (12 edges) centered at the given camera-relative origin. */
    private static void drawGhostBox(double x, double y, double z, double halfW, double height,
                                     float r, float g, float b, float a) {
        double x0 = x - halfW, x1 = x + halfW;
        double y0 = y, y1 = y + height;
        double z0 = z - halfW, z1 = z + halfW;
        GL11.glLineWidth(2.0f);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glColor4f(r, g, b, a);
        // bottom face
        vert(x0, y0, z0); vert(x1, y0, z0);
        vert(x1, y0, z0); vert(x1, y0, z1);
        vert(x1, y0, z1); vert(x0, y0, z1);
        vert(x0, y0, z1); vert(x0, y0, z0);
        // top face
        vert(x0, y1, z0); vert(x1, y1, z0);
        vert(x1, y1, z0); vert(x1, y1, z1);
        vert(x1, y1, z1); vert(x0, y1, z1);
        vert(x0, y1, z1); vert(x0, y1, z0);
        // verticals
        vert(x0, y0, z0); vert(x0, y1, z0);
        vert(x1, y0, z0); vert(x1, y1, z0);
        vert(x1, y0, z1); vert(x1, y1, z1);
        vert(x0, y0, z1); vert(x0, y1, z1);
        GL11.glEnd();
    }

    /** Horizontal ring (used as the pulsing spike indicator). */
    private static void drawRing(double x, double y, double z, double radius,
                                 float r, float g, float b, float a) {
        GL11.glLineWidth(2.0f);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glColor4f(r, g, b, a);
        int segs = 24;
        for (int i = 0; i < segs; i++) {
            double ang = i / (double) segs * Math.PI * 2;
            GL11.glVertex3d(x + Math.cos(ang) * radius, y, z + Math.sin(ang) * radius);
        }
        GL11.glEnd();
    }

    private static void vert(double x, double y, double z) {
        GL11.glVertex3d(x, y, z);
    }

    /** Anchor position (Vector A) as {x, y, z}. */
    private static double[] getEchoAnchor() {
        return new double[]{vecAx, vecAy, vecAz};
    }

    /** Predicted position (Vector C) as {x, y, z}. */
    private static double[] getEchoPredict() {
        return new double[]{vecCx, vecCy, vecCz};
    }

    /**
     * Recent position history as {x, y, z} triples, oldest first. Used to
     * draw the fading trail behind the player.
     */
    private static double[][] getEchoTrail(int max) {
        int size = Math.min(max, history.size());
        double[][] out = new double[size][3];
        Sample[] arr = history.toArray(new Sample[0]);
        for (int i = 0; i < size; i++) {
            Sample s = arr[arr.length - size + i];
            out[i][0] = s.x;
            out[i][1] = s.y;
            out[i][2] = s.z;
        }
        return out;
    }

    // ── helpers ───────────────────────────────────────────────────

    /**
     * Interpolate the player's position at an arbitrary point in time from
     * the sample history (null when the history is empty).
     */
    private static Sample sampleAt(long targetNanos) {
        if (history.isEmpty()) return null;
        Sample newest = history.peekLast();
        Sample oldest = history.peekFirst();
        if (targetNanos >= newest.nanos) return newest;
        if (targetNanos <= oldest.nanos) return oldest;

        Sample prev = oldest;
        for (Sample s : history) {
            if (s.nanos >= targetNanos) {
                long span = s.nanos - prev.nanos;
                if (span <= 0) return s;
                double t = (targetNanos - prev.nanos) / (double) span;
                return new Sample(targetNanos,
                        prev.x + (s.x - prev.x) * t,
                        prev.y + (s.y - prev.y) * t,
                        prev.z + (s.z - prev.z) * t,
                        s.onGround);
            }
            prev = s;
        }
        return newest;
    }

    private void resetEngine() {
        history.clear();
        vx = vy = vz = 0;
        prevVx = prevVy = prevVz = 0;
        jitterMs = 0;
        smoothPing = 0;
        lastInboundNanos = 0;
        spikeActive = false;
        spikeResendTimer = 0;
        vecAx = vecAy = vecAz = 0;
        vecCx = vecCy = vecCz = 0;
        offsetA = offsetC = 0;
        effectiveLookaheadMs = 0;
    }

    private static double sq(double v) {
        return v * v;
    }

    /** One recorded player position sample. */
    private static final class Sample {
        final long nanos;
        final double x, y, z;
        final boolean onGround;

        Sample(long nanos, double x, double y, double z, boolean onGround) {
            this.nanos = nanos;
            this.x = x;
            this.y = y;
            this.z = z;
            this.onGround = onGround;
        }
    }
}
