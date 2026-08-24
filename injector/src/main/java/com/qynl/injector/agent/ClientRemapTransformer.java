package com.qynl.injector.agent;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

/**
 * Remaps every client class ({@code com.qynl.client189/**}) from yarn names
 * to the obfuscated names the vanilla 1.8.9 jar ships with, at class-load
 * time. The client compiles against the same yarn mappings the Fabric mod
 * used; without this transformer every {@code net.minecraft...} reference in
 * its bytecode would point at names that don't exist in the running game.
 */
public final class ClientRemapTransformer implements ClassFileTransformer {

    private static final String CLIENT_PREFIX = "com/qynl/client189/";

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (className == null || !className.startsWith(CLIENT_PREFIX)) {
            return null;
        }
        // Never remap a class that is already defined (retransform path).
        if (classBeingRedefined != null) {
            return null;
        }
        try {
            ClassReader reader = new ClassReader(classfileBuffer);
            ClassWriter writer = new ClassWriter(0);
            ClassRemapper remapper = new ClassRemapper(writer, TinyMappings.get().remapper());
            reader.accept(remapper, 0);
            return writer.toByteArray();
        } catch (Throwable t) {
            System.err.println("[Qyn-L] remap failed for " + className + ": " + t);
            t.printStackTrace();
            return null;
        }
    }
}
