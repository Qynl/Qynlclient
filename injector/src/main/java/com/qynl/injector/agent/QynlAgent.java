package com.qynl.injector.agent;

import java.lang.instrument.Instrumentation;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Java agent entry point for the Qyn-L injector.
 *
 * <p><b>Launch mode</b> ({@code -javaagent:qynl-injector.jar}): {@link #premain}
 * runs before the game's main class, so every hooked class is transformed on
 * first load.</p>
 *
 * <p><b>Attach mode</b> ({@code agentmain}): attach to an already-running
 * vanilla 1.8.9 game (launched from any launcher — Modrinth, the vanilla
 * launcher, …). The same transformers are registered and any already-loaded
 * hook targets are retransformed so the client boots on the next tick.</p>
 *
 * <p>Attach mode works because {@link GameHookTransformer} only modifies
 * method bodies (retransformation cannot add interfaces, fields or methods)
 * and all accessor logic lives in {@link com.qynl.client189.ReflectionAccess}.</p>
 */
public final class QynlAgent {

    private static volatile boolean initialized = false;

    private QynlAgent() {
    }

    public static void premain(String agentArgs, Instrumentation inst) {
        initialize(inst);
    }

    public static void agentmain(String agentArgs, Instrumentation inst) {
        initialize(inst);
    }

    private static synchronized void initialize(Instrumentation inst) {
        if (initialized) {
            return;
        }
        initialized = true;
        try {
            TinyMappings.load();
        } catch (Throwable t) {
            System.err.println("[Qyn-L] failed to load mappings, injector disabled: " + t);
            t.printStackTrace();
            return;
        }

        inst.addTransformer(new GameHookTransformer(), true);
        inst.addTransformer(new ClientRemapTransformer(), true);

        // Attach-to-running-game: force a retransform of already-loaded targets.
        // Iterate the loaded classes instead of Class.forName with the system
        // classloader — under LaunchWrapper the game classes live in their own
        // classloader and would never be found that way.
        if (inst.isRetransformClassesSupported()) {
            Set<String> targets = new HashSet<>(Arrays.asList(GameHookTransformer.targetClassInternalNames()));
            for (Class<?> loaded : inst.getAllLoadedClasses()) {
                if (targets.contains(loaded.getName().replace('.', '/'))) {
                    try {
                        if (inst.isModifiableClass(loaded)) {
                            inst.retransformClasses(loaded);
                        }
                    } catch (Throwable ignored) {
                        // Class not loaded yet — the normal load path will hook it.
                    }
                }
            }
        }
        System.out.println("[Qyn-L] injector agent ready");
    }
}
