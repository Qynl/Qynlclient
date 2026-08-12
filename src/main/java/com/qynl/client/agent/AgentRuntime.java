package com.qynl.client.agent;

import net.minecraft.client.MinecraftClient;

/** Local gameplay runtime combining perception, state and safe actions. */
public final class AgentRuntime {
    private final MinecraftAgentAdapter worldState = new MinecraftAgentAdapter();
    private ScreenVisionAdapter vision;
    private AgentState latestState;

    public void tick(MinecraftClient client) {
        latestState = worldState.snapshot(client);
    }

    public AgentState latestState() { return latestState; }

    public AgentState snapshot(MinecraftClient client) {
        latestState = worldState.snapshot(client);
        return latestState;
    }

    public ScreenVisionAdapter.ScreenFrame captureScreen() {
        if (vision == null) vision = new ScreenVisionAdapter();
        return vision.capture();
    }

    public boolean inputAllowed(MinecraftClient client) { return AgentInput.allowed(client); }

    public boolean move(MinecraftClient client, boolean forward, boolean back, boolean left,
                        boolean right, boolean jump, boolean sneak, boolean sprint) {
        return AgentActions.move(client, forward, back, left, right, jump, sneak, sprint);
    }

    public boolean look(MinecraftClient client, double yawDelta, double pitchDelta) {
        return AgentActions.look(client, yawDelta, pitchDelta);
    }

    public boolean attack(MinecraftClient client) { return AgentActions.attackTarget(client); }

    public boolean use(MinecraftClient client) { return AgentActions.useMainHand(client); }

    public boolean stop(MinecraftClient client) { return AgentActions.stop(client); }

    public void shutdown(MinecraftClient client) { AgentInput.releaseAll(client); }
}
