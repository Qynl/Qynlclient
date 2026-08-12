package com.qynl.client.agent;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.Hand;

/**
 * High-level gameplay actions for the local agent.
 * Multiplayer is intentionally rejected: this is an automation interface for
 * private/single-player testing, not a multiplayer cheat interface.
 */
public final class AgentActions {
    private AgentActions() {}

    public static boolean move(MinecraftClient client, boolean forward, boolean back,
                               boolean left, boolean right, boolean jump,
                               boolean sneak, boolean sprint) {
        if (!AgentInput.allowed(client)) return false;
        AgentInput.setMovement(client, "forward", forward);
        AgentInput.setMovement(client, "back", back);
        AgentInput.setMovement(client, "left", left);
        AgentInput.setMovement(client, "right", right);
        AgentInput.setMovement(client, "jump", jump);
        AgentInput.setMovement(client, "sneak", sneak);
        AgentInput.setMovement(client, "sprint", sprint);
        return true;
    }

    public static boolean look(MinecraftClient client, double yawDelta, double pitchDelta) {
        if (!AgentInput.allowed(client)) return false;
        ClientPlayerEntity player = client.player;
        float yaw = player.getYaw() + (float) yawDelta;
        float pitch = Math.max(-90.0f, Math.min(90.0f, player.getPitch() + (float) pitchDelta));
        player.setYaw(yaw);
        player.setPitch(pitch);
        return true;
    }

    public static boolean attackTarget(MinecraftClient client) {
        if (!AgentInput.allowed(client) || client.interactionManager == null || client.targetedEntity == null) return false;
        Entity target = client.targetedEntity;
        if (!target.isAlive()) return false;
        client.interactionManager.attackEntity(client.player, target);
        client.player.swingHand(Hand.MAIN_HAND);
        return true;
    }

    public static boolean useMainHand(MinecraftClient client) {
        if (!AgentInput.allowed(client) || client.interactionManager == null) return false;
        client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
        client.player.swingHand(Hand.MAIN_HAND);
        return true;
    }

    public static boolean stop(MinecraftClient client) {
        if (!AgentInput.allowed(client)) return false;
        AgentInput.releaseAll(client);
        return true;
    }
}
