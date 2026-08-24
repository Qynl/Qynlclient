package com.qynl.client189.access;

/**
 * Injected onto the runtime {@code MinecraftClient} class by the agent so
 * client code can trigger the private {@code doAttack()} path (full vanilla
 * attack: raycast, swing, particles, crits) — the same entry point the
 * AutoClicker / Qynl / Criticals modules used through the Mixin invoker.
 */
public interface IMinecraftAccess {
    void qynlDoAttack();
}
