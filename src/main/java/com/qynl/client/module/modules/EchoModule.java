package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import com.qynl.client.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Echo — Soundscape radar.
 *
 * <p>Listens to the server's own sound packets (read-only) and records
 * fading 3D markers: red for player activity, magenta for ender-pearl
 * landings, gold for mobs, green for utility, grey for ambient.
 * Zero packets sent — unflagable by construction.</p>
 */
public class EchoModule extends Module {
    private static EchoModule instance;

    private final List<EchoMarker> markers = new ArrayList<>();
    private int cleanupTimer = 0;

    public EchoModule() {
        super("Echo", "Soundscape radar — listens to sound packets and shows fading 3D markers.",
                Category.RENDER);
        instance = this;
        bindKey(GLFW.GLFW_KEY_UNKNOWN);
        addSetting(Setting.range("fadeMs", "Fade time", 3000.0, 1000, 8000, 500, "ms"));
        addSetting(Setting.range("maxMarkers", "Max markers", 32.0, 8, 64, 4));
    }

    public static EchoModule getInstance() { return instance; }

    /** Called from packet mixin when a sound packet arrives. */
    public static void onSoundPacket(ClientboundSoundPacket packet) {
        if (instance == null || !instance.isEnabled()) return;
        if (packet.getSound() == null) return;

        Vec3 pos = new Vec3(packet.getX(), packet.getY(), packet.getZ());

        int color;
        var sound = packet.getSound().value();

        if (sound == SoundEvents.PLAYER_ATTACK_SWEEP || sound == SoundEvents.PLAYER_ATTACK_KNOCKBACK
                || sound == SoundEvents.PLAYER_HURT || sound == SoundEvents.PLAYER_DEATH
                || sound == SoundEvents.ARROW_SHOOT || sound == SoundEvents.CROSSBOW_SHOOT) {
            color = 0xFFFF5555; // Player activity — red
        } else if (sound == SoundEvents.ENDER_PEARL_THROW || sound == SoundEvents.CHORUS_FRUIT_TELEPORT
                || sound == SoundEvents.ENDERMAN_TELEPORT) {
            color = 0xFFFF00FF; // Pearl/teleport — magenta
        } else if (sound == SoundEvents.ZOMBIE_AMBIENT || sound == SoundEvents.SKELETON_AMBIENT
                || sound == SoundEvents.CREEPER_PRIMED || sound == SoundEvents.SPIDER_AMBIENT
                || sound == SoundEvents.WITHER_SHOOT || sound == SoundEvents.GHAST_SHOOT
                || sound == SoundEvents.BLAZE_SHOOT) {
            color = 0xFFFFAA00; // Mobs — gold
        } else if (sound == SoundEvents.CHEST_OPEN || sound == SoundEvents.CHEST_CLOSE
                || sound == SoundEvents.WOODEN_DOOR_OPEN || sound == SoundEvents.WOODEN_DOOR_CLOSE
                || sound == SoundEvents.IRON_DOOR_OPEN || sound == SoundEvents.IRON_DOOR_CLOSE
                || sound == SoundEvents.FENCE_GATE_OPEN || sound == SoundEvents.FENCE_GATE_CLOSE
                || sound == SoundEvents.WOODEN_TRAPDOOR_OPEN || sound == SoundEvents.WOODEN_TRAPDOOR_CLOSE
                || sound == SoundEvents.IRON_TRAPDOOR_OPEN || sound == SoundEvents.IRON_TRAPDOOR_CLOSE
                || sound == SoundEvents.LEVER_CLICK || sound == SoundEvents.STONE_BUTTON_CLICK_ON
                || sound == SoundEvents.WOODEN_BUTTON_CLICK_ON) {
            color = 0xFF55FF55; // Utility — green
        } else {
            color = 0xFF808080; // Ambient — grey, but only if interesting
            // Filter out common ambient sounds to avoid spam
            if (sound == SoundEvents.AMBIENT_CAVE.value()) return;
            if (packet.getVolume() < 0.5f) return;
        }

        instance.markers.add(new EchoMarker(pos, color, System.currentTimeMillis()));
    }

    public List<EchoMarker> getMarkers() {
        return List.copyOf(markers);
    }

    @Override
    public void onTick(Minecraft client) {
        if (!isEnabled()) return;
        cleanupTimer++;
        if (cleanupTimer < 10) return;
        cleanupTimer = 0;

        long now = System.currentTimeMillis();
        long fade = (long) getDoubleSetting("fadeMs");
        int maxMarkers = (int) getDoubleSetting("maxMarkers");

        markers.removeIf(m -> now - m.timestamp() > fade);
        while (markers.size() > maxMarkers) {
            markers.remove(0);
        }
    }

    @Override
    public void onDisable() {
        markers.clear();
    }

    public record EchoMarker(Vec3 pos, int color, long timestamp) {}
}