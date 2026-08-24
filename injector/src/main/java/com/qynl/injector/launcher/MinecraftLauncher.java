package com.qynl.injector.launcher;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Launches a vanilla 1.8.9 installation with the Qyn-L agent attached via
 * {@code -javaagent}. Reads the version JSON the official launcher wrote,
 * assembles the library classpath, extracts the LWJGL natives and spawns
 * {@code net.minecraft.client.main.Main}.
 */
public final class MinecraftLauncher {

    private MinecraftLauncher() {
    }

    public static Process launch(File gameDir, String version, String username, String javaBin,
                                 String memory, File agentJar, Consumer<String> log) throws Exception {
        File versionDir = new File(gameDir, "versions" + File.separator + version);
        File versionJson = new File(versionDir, version + ".json");
        if (!versionJson.isFile()) {
            throw new IOException("Version '" + version + "' is not installed at " + versionDir
                    + ".\nLaunch 1.8.9 once with the official Minecraft launcher, then retry.");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> json = (Map<String, Object>) Json.parse(new String(Files.readAllBytes(versionJson.toPath()), StandardCharsets.UTF_8));
        String mainClass = str(json.get("mainClass"), "net.minecraft.client.main.Main");
        String assetIndex = str(json.get("assetIndex"), "1.8");
        String osName = osName();
        String osArch = osArch();

        List<String> classpath = new ArrayList<>();
        classpath.add(new File(versionDir, version + ".jar").getAbsolutePath());

        File nativesDir = new File(System.getProperty("java.io.tmpdir"), "qynl-natives-" + Math.abs(System.nanoTime()));
        if (!nativesDir.mkdirs()) {
            throw new IOException("could not create natives dir " + nativesDir);
        }

        List<Object> libraries = list(json.get("libraries"));
        int nativeCount = 0;
        for (Object libObj : libraries) {
            if (!(libObj instanceof Map)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> lib = (Map<String, Object>) libObj;
            if (!rulesAllow(lib.get("rules"), osName, osArch)) {
                continue;
            }
            Map<String, Object> downloads = map(lib.get("downloads"));
            Map<String, Object> artifact = map(downloads.get("artifact"));
            String artifactPath = str(artifact.get("path"), null);
            if (artifactPath == null) {
                artifactPath = coordsToPath(str(lib.get("name"), ""));
            }
            File artifactFile = new File(gameDir, "libraries" + File.separator + artifactPath);
            if (artifactFile.isFile()) {
                classpath.add(artifactFile.getAbsolutePath());
            }

            Map<String, Object> classifiers = map(downloads.get("classifiers"));
            Map<String, Object> nativeArtifact = map(classifiers.get("natives-" + osName));
            String nativePath = str(nativeArtifact.get("path"), null);
            if (nativePath != null) {
                File nativeJar = new File(gameDir, "libraries" + File.separator + nativePath);
                if (nativeJar.isFile()) {
                    extract(nativeJar, nativesDir, log);
                    nativeCount++;
                }
            }
        }

        File assetsIndex = new File(gameDir, "assets" + File.separator + "indexes" + File.separator + assetIndex + ".json");
        if (!assetsIndex.isFile()) {
            log.accept("[!] assets index " + assetIndex + " not found at " + assetsIndex
                    + " — game assets may be missing.");
        }

        String classpathStr = join(classpath, File.pathSeparator);
        List<String> cmd = new ArrayList<>();
        cmd.add(javaBin);
        cmd.add("-javaagent:" + agentJar.getAbsolutePath());
        cmd.add("-Xmx" + memory);
        cmd.add("-Djava.library.path=" + nativesDir.getAbsolutePath());
        cmd.add("-cp");
        cmd.add(classpathStr);
        cmd.add(mainClass);
        cmd.add("--username");
        cmd.add(username);
        cmd.add("--version");
        cmd.add(version);
        cmd.add("--gameDir");
        cmd.add(gameDir.getAbsolutePath());
        cmd.add("--assetsDir");
        cmd.add(new File(gameDir, "assets").getAbsolutePath());
        cmd.add("--assetIndex");
        cmd.add(assetIndex);
        cmd.add("--uuid");
        cmd.add(UUID.randomUUID().toString());
        cmd.add("--accessToken");
        cmd.add("0");
        cmd.add("--userType");
        cmd.add("legacy");

        log.accept("[launch] java " + javaBin + " -javaagent:" + agentJar.getAbsolutePath()
                + " -Xmx" + memory + " (natives: " + nativeCount + ")");
        log.accept("[launch] classpath entries: " + classpath.size());

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        Thread reader = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    log.accept(line);
                }
            } catch (IOException ignored) {
            }
        });
        reader.setDaemon(true);
        reader.start();
        return process;
    }

    // ── version JSON helpers ──────────────────────────────────────────────

    private static String osName() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) return "windows";
        if (os.contains("mac") || os.contains("darwin")) return "osx";
        return "linux";
    }

    private static String osArch() {
        String arch = System.getProperty("os.arch", "").toLowerCase();
        return arch.contains("64") ? "x86_64" : "x86";
    }

    /** Evaluates the rules list of a library entry (empty rules = allowed). */
    private static boolean rulesAllow(Object rulesObj, String osName, String osArch) {
        if (!(rulesObj instanceof List)) {
            return true;
        }
        List<?> rules = (List<?>) rulesObj;
        boolean allowed = false;
        for (Object ruleObj : rules) {
            if (!(ruleObj instanceof Map)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> rule = (Map<String, Object>) ruleObj;
            String action = str(rule.get("action"), "");
            Object os = rule.get("os");
            boolean match = osMatches(os, osName, osArch);
            if (match && "disallow".equals(action)) {
                return false;
            }
            if (match && "allow".equals(action)) {
                allowed = true;
            }
        }
        return allowed;
    }

    private static boolean osMatches(Object osObj, String osName, String osArch) {
        if (!(osObj instanceof Map)) {
            return true;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> os = (Map<String, Object>) osObj;
        String name = str(os.get("name"), null);
        if (name != null && !name.equals(osName)) {
            return false;
        }
        String arch = str(os.get("arch"), null);
        if (arch != null && !arch.equals(osArch)) {
            return false;
        }
        String version = str(os.get("version"), null);
        if (version != null && !System.getProperty("os.version", "").matches(version)) {
            return false;
        }
        return true;
    }

    /** Maven coordinates → libraries/ path fallback. */
    private static String coordsToPath(String coords) {
        if (coords == null || coords.isEmpty()) {
            return "";
        }
        String[] parts = coords.split(":");
        if (parts.length < 3) {
            return "";
        }
        String group = parts[0].replace('.', '/');
        String artifact = parts[1];
        String version = parts[2];
        String classifier = parts.length > 3 ? "-" + parts[3] : "";
        return group + "/" + artifact + "/" + version + "/" + artifact + "-" + version + classifier + ".jar";
    }

    private static void extract(File jar, File dest, Consumer<String> log) throws IOException {
        try (ZipFile zip = new ZipFile(jar)) {
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || entry.getName().startsWith("META-INF")) {
                    continue;
                }
                File out = new File(dest, entry.getName());
                out.getParentFile().mkdirs();
                Files.copy(zip.getInputStream(entry), out.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : new java.util.LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object o) {
        return o instanceof List ? (List<Object>) o : new ArrayList<>();
    }

    private static String str(Object o, String fallback) {
        return o != null ? String.valueOf(o) : fallback;
    }

    private static String join(List<String> items, String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sb.append(sep);
            }
            sb.append(items.get(i));
        }
        return sb.toString();
    }
}
