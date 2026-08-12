package com.qynl.client.agent;

import net.minecraft.client.MinecraftClient;

/** Single entry point used by an external local agent integration. */
public final class AgentRuntime {
    private final MinecraftAgentAdapter worldState = new MinecraftAgentAdapter();
    private ScreenVisionAdapter vision;
    private AgentState latestState;

    public void tick(MinecraftClient client) {
        latestState = worldState.snapshot(client);
    }

    public AgentState latestState() {
        return latestState;
    }

    public AgentState snapshot(MinecraftClient client) {
        latestState = worldState.snapshot(client);
        return latestState;
    }

    public ScreenVisionAdapter.ScreenFrame captureScreen() {
        if (vision == null) vision = new ScreenVisionAdapter();
        return vision.capture();
    }

    public boolean inputAllowed(MinecraftClient client) {
        return AgentInput.allowed(client);
    }

    public void shutdown(MinecraftClient client) {
        AgentInput.releaseAll(client);
    }
}
