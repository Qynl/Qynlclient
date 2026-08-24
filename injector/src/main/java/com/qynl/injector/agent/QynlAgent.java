package com.qynl.injector.agent;

import java.lang.instrument.Instrumentation;

/**
 * Java agent entry point for the Qyn-L injector.
 *
 * <p><b>Launch mode</b> ({@code -javaagent:qynl-injector.jar}): {@link #premain}
 * runs before the game's main class, so every hooked class is transformed on
 * first load.</p>
 *
 * <p><b>Attach mode</b> ({@code agentmain}): the same transformers are
 * registered and any already-loaded hook targets are retransformed so the
 * client boots on the next tick.</p>
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
        if (inst.isRetransformClassesSupported()) {
            for (String internalName : GameHookTransformer.targetClassInternalNames()) {
                try {
                    Class<?> c = Class.forName(internalName.replace('/', '.'), false, ClassLoader.getSystemClassLoader());
                    if (c != null && inst.isModifiableClass(c)) {
                        inst.retransformClasses(c);
                    }
                } catch (Throwable ignored) {
                    // Class not loaded yet — the normal load path will hook it.
                }
            }
        }
        System.out.println("[Qyn-L] injector agent ready");
    }
}
