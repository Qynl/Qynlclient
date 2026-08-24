package com.qynl.client189;

import com.qynl.client189.modules.AegisModule;
import com.qynl.client189.modules.BlinkModule;
import com.qynl.client189.modules.BlockHitModule;
import com.qynl.client189.modules.ClutchModule;
import com.qynl.client189.modules.CriticalsModule;
import com.qynl.client189.modules.DirectorModule;
import com.qynl.client189.modules.EchoModule;
import com.qynl.client189.modules.HindsightModule;
import com.qynl.client189.modules.NameTagsModule;
import com.qynl.client189.modules.NoHurtCamModule;
import com.qynl.client189.modules.NoViewBobModule;
import com.qynl.client189.modules.QynlModule;
import com.qynl.client189.modules.ReachModule;
import com.qynl.client189.modules.SearchModule;
import com.qynl.client189.modules.StorageESPModule;
import com.qynl.client189.modules.TracersModule;
import com.qynl.client189.modules.VelocityModule;
import com.qynl.client189.modules.WTapModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.c2s.play.KeepAliveC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundIdS2CPacket;

/**
 * GameHooks — the single bridge between the agent-injected bytecode in the
 * obfuscated game classes and the (runtime-remapped) client code.
 *
 * <p>Every method here is invoked from ASM-injected call sites with only
 * primitives / {@link Object} parameters, so the injected bytecode never has
 * to spell out an obfuscated type descriptor. This class is remapped like the
 * rest of the client package, so all Minecraft references below resolve to
 * their obfuscated names at runtime.</p>
 */
public final class GameHooks {

    private static boolean flushingBlink = false;

    private GameHooks() {
    }

    // ── tick ──────────────────────────────────────────────────────────────
    /** Injected at the head of {@code MinecraftClient.tick}. */
    public static void onClientTick() {
        QynlClient189.ensureInit();
        QynlClient189 instance = QynlClient189.getInstance();
        if (instance != null) {
            instance.onClientTick();
        }
    }

    // ── HUD ───────────────────────────────────────────────────────────────
    /** Injected at the tail of {@code InGameHud.render}. */
    public static void renderHud() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.world != null && client.currentScreen == null) {
            HudRenderer189.render(client);
        }
    }

    // ── packets (send) ────────────────────────────────────────────────────
    /**
     * Injected at the head of {@code ClientPlayNetworkHandler.sendPacket},
     * cancellable. Merges the old movement-hook, blink and keep-alive mixins.
     */
    public static boolean onSendPacket(Object packet) {
        if (flushingBlink) {
            return false;
        }
        if (packet instanceof KeepAliveC2SPacket) {
            PingTracker.onKeepAliveSent();
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return false;
        }

        boolean isMovePacket = packet instanceof PlayerMoveC2SPacket;

        // Qynl Quantum Collapse — hold movement during the dodge window.
        if (isMovePacket && QynlModule.shouldHoldMovement()) {
            QynlModule.buffer((Packet) packet);
            return true;
        }

        // Reach silent pack-choke — hold movement, flush together later.
        if (isMovePacket && ReachModule.shouldHoldPacket()) {
            ReachModule.buffer((Packet) packet);
            return true;
        }

        // Silent aim (one-shot) — spoof the rotation carried by the packet,
        // restore the camera to where the player was actually looking.
        if (SilentAim.isArmed() && isMovePacket) {
            PlayerMoveC2SPacket move = (PlayerMoveC2SPacket) packet;
            if (!SilentAim.hasCapturedVisual()) {
                SilentAim.captureVisual(client.player.yaw, client.player.pitch);
            }
            ReflectionAccess.playerMoveSetYaw(move, SilentAim.getSilentYaw());
            ReflectionAccess.playerMoveSetPitch(move, SilentAim.getSilentPitch());
            client.player.yaw = SilentAim.getVisualYaw();
            client.player.pitch = SilentAim.getVisualPitch();
            SilentAim.clear();
        }

        // Blink — hold the packet, replay it on flush.
        if (BlinkModule.shouldHold((Packet) packet)) {
            BlinkModule.hold((Packet) packet);
            return true;
        }
        return false;
    }

    /** Replays Blink's buffer through the real send path (replaces BlinkMixin.flush). */
    public static void flushBlink() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        if (handler == null) {
            return;
        }
        flushingBlink = true;
        try {
            Packet packet;
            while ((packet = BlinkModule.poll()) != null) {
                handler.sendPacket(packet);
            }
        } finally {
            flushingBlink = false;
        }
    }

    // ── packets (receive) ─────────────────────────────────────────────────
    /** Injected at the head of {@code ClientPlayNetworkHandler.onKeepAlive}. */
    public static void onKeepAliveReceived() {
        PingTracker.onKeepAliveReceived();
    }

    /** Injected at the head of {@code ClientPlayNetworkHandler.onPlaySound}. */
    public static void onPlaySound(Object packet) {
        if (packet instanceof PlaySoundIdS2CPacket) {
            PlaySoundIdS2CPacket sound = (PlaySoundIdS2CPacket) packet;
            EchoModule.onSound(sound.getSound(), sound.getX(), sound.getY(), sound.getZ(), sound.getVolume());
        }
    }

    // ── velocity ──────────────────────────────────────────────────────────
    /**
     * Injected at the head of {@code Entity.addVelocity}, cancellable.
     * Returns true when the knockback was handled (and the original call
     * must be skipped). Replaces the Velocity mixin.
     */
    public static boolean onAddVelocity(Object entity, double x, double y, double z) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return false;
        }
        if (entity != client.player) {
            return false;
        }
        VelocityModule module = VelocityModule.getInstance();
        if (module == null || !module.isEnabled()) {
            return false;
        }
        if (!VelocityModule.rollChance()) {
            return false;
        }

        double hx = x * module.horizontalFactor();
        double hy = y * module.verticalFactor();
        double hz = z * module.horizontalFactor();

        client.player.velocityX += hx;
        client.player.velocityY += hy;
        client.player.velocityZ += hz;

        VelocityModule.markMixinDampened();
        return true;
    }

    // ── combat ────────────────────────────────────────────────────────────
    /** Injected at the head of {@code MinecraftClient.doAttack}. */
    public static void onAttack() {
        MinecraftClient client = MinecraftClient.getInstance();
        WTapModule.onAttack(client);
        BlockHitModule.onAttack(client);
    }

    /** Injected before every return of {@code ClientPlayerInteractionManager.getReachDistance}. */
    public static float onGetReachDistance(float base) {
        if (ReachModule.isActive()) {
            return base + (float) ReachModule.currentBonus();
        }
        return base;
    }

    // ── camera ────────────────────────────────────────────────────────────
    /** Injected at the head of {@code GameRenderer.bobViewWhenHurt}. */
    public static boolean shouldSkipHurtCam() {
        return NoHurtCamModule.isActive();
    }

    /** Injected at the head of {@code GameRenderer.bobView}. */
    public static boolean shouldSkipViewBob() {
        return NoViewBobModule.isActive();
    }

    // ── world render ──────────────────────────────────────────────────────
    /** Injected at the tail of {@code GameRenderer.renderWorld}. */
    public static void renderWorld(float partialTicks) {
        SearchModule.render(partialTicks);
        StorageESPModule.render(partialTicks);
        TracersModule.render(partialTicks);
        NameTagsModule.render(partialTicks);
        QynlModule.render(partialTicks);
        HindsightModule.render(partialTicks);
        CriticalsModule.render(partialTicks);
        AegisModule.render(partialTicks);
        ClutchModule.render(partialTicks);
        EchoModule.render(partialTicks);
        DirectorModule.render(partialTicks);
    }

    // ── input ─────────────────────────────────────────────────────────────
    /**
     * Injected at the tail of {@code Input.tick}. Applies the WTap forward
     * release, the Aegis dodge and the Qynl collapse strafe exactly like the
     * old Input mixin, with Aegis taking priority.
     */
    public static void onInputTick(Object inputObj) {
        if (!(inputObj instanceof Input)) {
            return;
        }
        Input input = (Input) inputObj;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            return;
        }
        if (input != client.player.input) {
            return;
        }

        if (WTapModule.isTapping()) {
            input.movementForward = 0.0F;
        }

        float aegisDodge = AegisModule.dodgeStrafe();
        if (aegisDodge != 0.0F) {
            input.movementSideways = aegisDodge;
            if (AegisModule.wantsJump()) {
                input.jumping = true;
            }
        } else {
            float qynlDodge = QynlModule.dodgeStrafe();
            if (qynlDodge != 0.0F) {
                input.movementSideways = qynlDodge;
            }
        }
    }
}
