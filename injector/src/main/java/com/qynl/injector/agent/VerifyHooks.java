package com.qynl.injector.agent;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
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
 *       and assert the expected hook/interface markers landed in the output.
 *       This proves every yarn→obfuscated name resolution is correct.</li>
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
            // Every remaining net/minecraft reference must be an unmapped
            // (readable) class — anything mappable that survived is a bug.
            for (String ref : collectMinecraftRefs(out)) {
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

    /** Collects distinct net/minecraft class references from a class file's raw bytes. */
    private static java.util.Set<String> collectMinecraftRefs(byte[] bytes) {
        java.util.Set<String> refs = new java.util.LinkedHashSet<>();
        String text = new String(bytes, StandardCharsets.UTF_8);
        int idx = 0;
        while ((idx = text.indexOf("net/minecraft", idx)) >= 0) {
            int start = idx;
            int end = start;
            while (end < text.length()) {
                char c = text.charAt(end);
                if (Character.isLetterOrDigit(c) || c == '/' || c == '$' || c == '_') {
                    end++;
                } else {
                    break;
                }
            }
            refs.add(text.substring(start, end));
            idx = end;
        }
        return refs;
    }

    private static void fail(String message) {
        errors++;
        System.err.println("[verify] FAIL: " + message);
    }
}
