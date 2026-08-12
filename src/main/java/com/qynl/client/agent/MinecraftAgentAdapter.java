package com.qynl.client.agent;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Production-facing read adapter for an external agent.
 *
 * This class deliberately contains no networking and no model code. It turns
 * the live Minecraft client into a compact, deterministic state snapshot that
 * can be transported by any local agent protocol (MCP, WebSocket, stdin, etc.).
 */
public final class MinecraftAgentAdapter {
    private long tick;

    public AgentState snapshot(MinecraftClient client) {
        tick++;
        if (client == null || client.player == null || client.world == null) {
            return new AgentState(tick, false, "", null, null, List.of(), Map.of(), Map.of());
        }

        PlayerEntity player = client.player;
        Vec3d pos = player.getPos();

        List<AgentState.EntityState> nearby = new ArrayList<>();
        for (Entity entity : client.world.getEntities()) {
            if (entity == player || !entity.isAlive()) continue;
            double distance = entity.distanceTo(player);
            if (distance > 32.0) continue;

            float health = entity instanceof LivingEntity living ? living.getHealth() : -1.0f;
            String name = entity.getName().getString();
            nearby.add(new AgentState.EntityState(
                    entity.getType().toString(), name, distance,
                    entity.getX(), entity.getY(), entity.getZ(), health
            ));
        }
        nearby.sort(java.util.Comparator.comparingDouble(AgentState.EntityState::distance));
        if (nearby.size() > 32) nearby = new ArrayList<>(nearby.subList(0, 32));

        Map<String, Integer> inventory = new LinkedHashMap<>();
        for (ItemStack stack : player.getInventory().main) {
            if (stack.isEmpty()) continue;
            String id = stack.getItem().toString();
            inventory.merge(id, stack.getCount(), Integer::sum);
        }

        Map<String, Object> environment = new LinkedHashMap<>();
        environment.put("dayTime", client.world.getTimeOfDay());
        environment.put("rain", client.world.isRaining());
        environment.put("thundering", client.world.isThundering());
        environment.put("loadedEntityCount", client.world.getEntities().size());

        AgentState.TargetState target = null;
        if (client.targetedEntity != null && client.targetedEntity.isAlive()) {
            Entity entity = client.targetedEntity;
            target = new AgentState.TargetState(
                    entity.getType().toString(),
                    entity.getName().getString(),
                    entity.distanceTo(player),
                    entity.getX(), entity.getY(), entity.getZ()
            );
        }

        AgentState.PlayerState playerState = new AgentState.PlayerState(
                pos.x, pos.y, pos.z,
                player.getYaw(), player.getPitch(),
                player.getHealth(), player.getMaxHealth(),
                player.getHungerManager().getFoodLevel(),
                player.getInventory().selectedSlot,
                player.isOnGround(), player.isSprinting(), player.isSneaking()
        );

        return new AgentState(
                tick,
                true,
                client.world.getRegistryKey().getValue().toString(),
                playerState,
                target,
                nearby,
                inventory,
                environment
        );
    }
}
