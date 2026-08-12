package com.qynl.client.agent;

import java.util.List;
import java.util.Map;

/** Immutable, model-friendly snapshot of the Minecraft client state. */
public record AgentState(
        long tick,
        boolean inGame,
        String dimension,
        PlayerState player,
        TargetState target,
        List<EntityState> nearbyEntities,
        Map<String, Integer> inventory,
        Map<String, Object> environment
) {
    public record PlayerState(
            double x, double y, double z,
            float yaw, float pitch,
            float health, float maxHealth,
            int food,
            int selectedSlot,
            boolean onGround,
            boolean sprinting,
            boolean sneaking
    ) {}

    public record TargetState(
            String type,
            String name,
            double distance,
            double x, double y, double z
    ) {}

    public record EntityState(
            String type,
            String name,
            double distance,
            double x, double y, double z,
            float health
    ) {}
}
