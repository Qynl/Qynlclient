package com.qynl.injector.agent;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Offline verification of the injector pipeline, run by the
 * {@code verifyHooks} Gradle task:
 *
 * <ol>
 *   <li>For every class the agent transforms: load the real obfuscated class
 *       bytes from the vanilla 1.8.9 jar, apply {@link GameHookTransformer}
 *       and assert the expected hook markers landed in the output. This
 *       proves every yarn→obfuscated name resolution is correct.</li>
 *   <li>For every member {@link com.qynl.client189.ReflectionAccess} resolves
 *       reflectively at runtime: assert the mapped obfuscated field/method
 *       actually exists on the real class in the vanilla jar. Reflection
 *       fails silently until first use, so this must be proven offline.</li>
 *   <li>For every compiled client class: apply {@link ClientRemapTransformer}
 *       and assert no mappable {@code net.minecraft...} reference survives.
 *       This proves the client bytecode will resolve inside the game.</li>
 * </ol>
 *
 * Exits non-zero on any failure. Run with:
 * {@code ./gradlew -p injector verifyHooks}
 */
public final class VerifyHooks {

    private static int errors = 0;

    private VerifyHooks() {
    }

    public static void main(String[] args) throws Exception {
        String mcJar = System.getProperty("verify.mcjar");
        String classDir = System.getProperty("verify.classdir");
        if (mcJar == null || classDir == null) {
            System.err.println("verify.mcjar and verify.classdir system properties are required");
            System.exit(2);
        }

        TinyMappings.load();
        System.out.println("[verify] mappings: " + TinyMappings.get().classCount() + " classes, "
                + TinyMappings.get().methodCount() + " methods, " + TinyMappings.get().fieldCount() + " fields");

        verifyHooks(mcJar);
        verifyReflectionTargets(mcJar);
        verifyRemap(classDir);

        if (errors == 0) {
            System.out.println("[verify] ALL CHECKS PASSED");
            System.exit(0);
        } else {
            System.out.println("[verify] " + errors + " CHECK(S) FAILED");
            System.exit(1);
        }
    }

    // ── part 1: hook resolution + injection ───────────────────────────────

    private static void verifyHooks(String mcJar) throws IOException {
        System.out.println("[verify] === hooks against " + mcJar + " ===");
        GameHookTransformer transformer = new GameHookTransformer();
        try (ZipFile zip = new ZipFile(mcJar)) {
            String[] targets = GameHookTransformer.targetClassInternalNames();
            for (String target : targets) {
                byte[] original = readZip(zip, target + ".class");
                if (original == null) {
                    fail("hook target class not found in jar: " + target);
                    continue;
                }
                byte[] out = transformer.transform(null, target, null, null, original);
                if (out == null) {
                    fail("transformer returned null for " + target);
                    continue;
                }
                List<String> markers = GameHookTransformer.expectedMarkers(target);
                for (String marker : markers) {
                    if (!containsAscii(out, marker)) {
                        fail("marker '" + marker + "' missing in " + target);
                    }
                }
                System.out.println("[verify]   " + target + " -> ok (" + markers.size() + " marker(s))");
            }
        }
    }

    // ── part 1b: reflection accessor resolution ───────────────────────────

    private static void verifyReflectionTargets(String mcJar) throws IOException {
        System.out.println("[verify] === reflection accessors ===");
        try (ZipFile zip = new ZipFile(mcJar)) {
            checkMethod(zip, "net/minecraft/client/MinecraftClient", "doAttack", "()V");
            checkField(zip, "net/minecraft/client/options/KeyBinding", "pressed");
            checkField(zip, "net/minecraft/client/options/KeyBinding", "code");
            checkField(zip, "net/minecraft/network/packet/c2s/play/PlayerMoveC2SPacket", "yaw");
            checkField(zip, "net/minecraft/network/packet/c2s/play/PlayerMoveC2SPacket", "pitch");
        }
    }

    private static void checkMethod(ZipFile zip, String yarnClass, String yarnMethod, String yarnDesc)
            throws IOException {
        TinyMappings mappings = TinyMappings.get();
        String obfClass = mappings.mapClass(yarnClass);
        String obfMethod = mappings.mapMethod(yarnClass, yarnMethod, yarnDesc);
        String obfDesc = mappings.mapDesc(yarnDesc);
        byte[] bytes = readZip(zip, obfClass + ".class");
        if (bytes == null) {
            fail("reflection target class not found: " + obfClass);
            return;
        }
        List<String> methods = new ArrayList<>();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                methods.add(name + desc);
                return null;
            }
        }, 0);
        if (methods.contains(obfMethod + obfDesc)) {
            System.out.println("[verify]   " + yarnClass + "." + yarnMethod + " -> " + obfClass + "." + obfMethod + obfDesc + " ok");
        } else {
            fail("reflection method '" + obfMethod + obfDesc + "' not found on " + obfClass);
        }
    }

    private static void checkField(ZipFile zip, String yarnClass, String yarnField) throws IOException {
        TinyMappings mappings = TinyMappings.get();
        String obfClass = mappings.mapClass(yarnClass);
        String obfField = mappings.mapField(yarnClass, yarnField);
        byte[] bytes = readZip(zip, obfClass + ".class");
        if (bytes == null) {
            fail("reflection target class not found: " + obfClass);
            return;
        }
        List<String> fields = new ArrayList<>();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
                fields.add(name);
                return null;
            }
        }, 0);
        if (fields.contains(obfField)) {
            System.out.println("[verify]   " + yarnClass + "." + yarnField + " -> " + obfClass + "." + obfField + " ok");
        } else {
            fail("reflection field '" + obfField + "' not found on " + obfClass);
        }
    }

    // ── part 2: client remap ──────────────────────────────────────────────

    private static void verifyRemap(String classDir) throws IOException {
        System.out.println("[verify] === client remap ===");
        ClientRemapTransformer remapper = new ClientRemapTransformer();
        File root = new File(classDir);
        if (!root.isDirectory()) {
            fail("class dir not found: " + classDir);
            return;
        }
        int checked = 0;
        for (File file : walk(root)) {
            if (!file.getName().endsWith(".class")) {
                continue;
            }
            String internal = toInternalName(root, file);
            // Only com.qynl.client189 classes are in remap scope — everything
            // else (agent infra, launcher) returns null by design.
            if (!internal.startsWith("com/qynl/client189/")) {
                continue;
            }
            byte[] original = readFile(file);
            byte[] out = remapper.transform(null, internal, null, null, original);
            if (out == null) {
                fail("remap returned null for " + internal);
                continue;
            }
            // Every remaining net/minecraft class reference must be unmapped
            // (readable) — anything mappable that survived is a bug. Only
            // genuine class references are inspected (constant-pool class
            // entries via descriptors/owners); plain string literals like the
            // yarn lookup keys in ReflectionAccess are intentionally kept.
            for (String ref : collectClassRefs(out)) {
                String mapped = TinyMappings.get().mapClass(ref);
                if (!mapped.equals(ref)) {
                    fail("unmapped reference '" + ref + "' -> should be '" + mapped + "' in " + internal);
                }
            }
            checked++;
        }
        System.out.println("[verify]   checked " + checked + " client class(es)");
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private static byte[] readZip(ZipFile zip, String name) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null) {
            return null;
        }
        try (InputStream in = zip.getInputStream(entry)) {
            return readAll(in);
        }
    }

    private static byte[] readFile(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            return readAll(in);
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) >= 0) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private static boolean containsAscii(byte[] haystack, String needle) {
        byte[] pattern = needle.getBytes(StandardCharsets.UTF_8);
        if (pattern.length == 0 || haystack.length < pattern.length) {
            return false;
        }
        outer:
        for (int i = 0; i <= haystack.length - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (haystack[i + j] != pattern[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    private static java.util.List<File> walk(File root) {
        java.util.List<File> files = new java.util.ArrayList<>();
        collect(root, files);
        return files;
    }

    private static void collect(File dir, java.util.List<File> files) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                collect(child, files);
            } else {
                files.add(child);
            }
        }
    }

    private static String toInternalName(File root, File file) {
        String rel = root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/');
        return rel.substring(0, rel.length() - ".class".length());
    }

    /**
     * Collects distinct net/minecraft class references from a class file,
     * walking the real constant-pool references (class, super, interfaces,
     * owners, descriptors, signatures, annotations, class literals). Plain
     * string literals are not class references and are deliberately ignored.
     */
    private static java.util.Set<String> collectClassRefs(byte[] bytes) {
        final java.util.Set<String> refs = new java.util.LinkedHashSet<>();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public void visit(int version, int access, String name, String signature,
                              String superName, String[] interfaces) {
                add(name);
                add(superName);
                if (interfaces != null) {
                    for (String iface : interfaces) add(iface);
                }
                addDesc(signature);
            }

            @Override
            public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
                addDesc(desc);
                addDesc(signature);
                return null;
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                addDesc(desc);
                addDesc(signature);
                if (exceptions != null) {
                    for (String ex : exceptions) add(ex);
                }
                return new MethodVisitor(Opcodes.ASM5) {
                    @Override
                    public void visitFieldInsn(int opcode, String owner, String fName, String fDesc) {
                        add(owner);
                        addDesc(fDesc);
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String mName, String mDesc, boolean itf) {
                        add(owner);
                        addDesc(mDesc);
                    }

                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        add(type);
                    }

                    @Override
                    public void visitLdcInsn(Object cst) {
                        if (cst instanceof Type) {
                            addDesc(((Type) cst).getDescriptor());
                        }
                    }

                    @Override
                    public void visitMultiANewArrayInsn(String desc, int dims) {
                        addDesc(desc);
                    }

                    @Override
                    public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
                        add(type);
                    }

                    @Override
                    public void visitLocalVariable(String vName, String desc, String signature,
                                                   Label start, Label end, int index) {
                        addDesc(desc);
                        addDesc(signature);
                    }

                    @Override
                    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                        addDesc(desc);
                        return null;
                    }

                    @Override
                    public AnnotationVisitor visitParameterAnnotation(int parameter, String desc, boolean visible) {
                        addDesc(desc);
                        return null;
                    }

                    @Override
                    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
                        addDesc(desc);
                        return null;
                    }
                };
            }

            @Override
            public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                addDesc(desc);
                return null;
            }

            @Override
            public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
                addDesc(desc);
                return null;
            }

            private void add(String cls) {
                if (cls != null && cls.startsWith("net/minecraft/")) {
                    refs.add(cls);
                }
            }

            private void addDesc(String desc) {
                if (desc == null) return;
                int i = 0;
                while ((i = desc.indexOf('L', i)) >= 0) {
                    int end = desc.indexOf(';', i);
                    if (end < 0) break;
                    add(desc.substring(i + 1, end));
                    i = end + 1;
                }
            }
        }, 0);
        return refs;
    }

    private static void fail(String message) {
        errors++;
        System.err.println("[verify] FAIL: " + message);
    }
}
