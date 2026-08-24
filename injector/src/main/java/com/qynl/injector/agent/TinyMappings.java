package com.qynl.injector.agent;

import org.objectweb.asm.commons.Remapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Parses the yarn {@code mappings.tiny} file bundled into the agent jar and
 * exposes yarn → official (obfuscated) name lookups, plus an ASM
 * {@link Remapper} that rewrites client bytecode from yarn names to the names
 * the vanilla 1.8.9 jar actually ships with.
 *
 * <p>The tiny format maps a hierarchy of namespaces; for 1.8.9 legacyfabric
 * yarn these are {@code official} (the shipped obfuscated names) and
 * {@code named} (yarn). Unmapped names fall back to identity, which is
 * correct for the readable classes/members 1.8.9 kept (e.g.
 * {@code net.minecraft.client.main.Main}).</p>
 */
public final class TinyMappings {

    private static final String RESOURCE = "/mappings/mappings.tiny";

    private static TinyMappings instance;

    private final Map<String, String> classMap = new HashMap<>();
    private final Map<String, String> methodMap = new HashMap<>();
    private final Map<String, String> fieldMap = new HashMap<>();

    private TinyMappings() {
    }

    public static synchronized TinyMappings get() {
        if (instance == null) {
            instance = new TinyMappings();
            instance.parse();
        }
        return instance;
    }

    public static synchronized void load() {
        get();
    }

    private void parse() {
        InputStream in = TinyMappings.class.getResourceAsStream(RESOURCE);
        if (in == null) {
            throw new IllegalStateException("mappings.tiny not found on classpath (agent jar missing bundled mappings)");
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null) {
                throw new IllegalStateException("empty mappings.tiny");
            }
            String[] parts = header.split("\t");
            if (parts.length < 4 || !"tiny".equals(parts[0])) {
                throw new IllegalStateException("unsupported tiny header: " + header);
            }
            int ns = parts.length - 3; // tiny <major> <minor> <ns1> <ns2> ...
            int namedIdx = -1;
            int officialIdx = -1;
            for (int i = 0; i < ns; i++) {
                String name = parts[3 + i];
                if ("named".equals(name)) namedIdx = i;
                if ("official".equals(name)) officialIdx = i;
            }
            if (namedIdx < 0 || officialIdx < 0) {
                throw new IllegalStateException("tiny file has no named/official namespaces: " + header);
            }

            // Layout: class lines start with 'c'; member lines are indented
            // (start with a tab) and belong to the preceding class. Member
            // lines carry ONE descriptor, written in the official namespace:
            //   \tf\t<fieldDesc>\t<name per ns>
            //   \tm\t<methodDesc>\t<name per ns>
            String currentYarnOwner = null;
            String currentObfOwner = null;

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                if (line.charAt(0) == 'c') {
                    String[] t = line.split("\t");
                    if (t.length >= 1 + ns) {
                        currentObfOwner = t[1 + officialIdx];
                        currentYarnOwner = t[1 + namedIdx];
                        if (currentObfOwner != null && !currentObfOwner.equals(currentYarnOwner)) {
                            classMap.put(currentYarnOwner, currentObfOwner);
                        }
                    }
                } else if (line.charAt(0) == '\t' && currentYarnOwner != null) {
                    // Member lines: \t[f|m]\t<officialDesc>\t<name per ns>.
                    // Property/comment lines (\t\tp / \tc) are skipped.
                    String[] t = line.split("\t");
                    String kind = t.length > 1 ? t[1] : "";
                    if (!"f".equals(kind) && !"m".equals(kind)) {
                        continue;
                    }
                    if (t.length < 3 + ns) {
                        continue;
                    }
                    boolean isField = "f".equals(kind);
                    String officialDesc = t[2];
                    String officialName = t[3 + officialIdx];
                    String namedName = t[3 + namedIdx];
                    if (isField) {
                        if (!namedName.equals(officialName)) {
                            fieldMap.put(currentYarnOwner + " " + namedName, officialName);
                        }
                    } else {
                        if (!namedName.equals(officialName)) {
                            // Key by (yarn owner, yarn name, official desc) so
                            // overloads stay distinct; lookups translate the
                            // yarn desc to the official desc first.
                            methodMap.put(currentYarnOwner + " " + namedName + " " + officialDesc, officialName);
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("could not read mappings.tiny", e);
        }
        if (classMap.isEmpty()) {
            throw new IllegalStateException("mappings.tiny parsed but empty");
        }
    }

    // ── lookups (identity fallback) ───────────────────────────────────────

    /** Maps a yarn internal class name to the shipped (obfuscated) name. */
    public String mapClass(String yarnInternalName) {
        String mapped = classMap.get(yarnInternalName);
        return mapped != null ? mapped : yarnInternalName;
    }

    /** Maps a yarn method (owner + name + yarn descriptor) to the shipped name. */
    public String mapMethod(String yarnOwner, String yarnName, String yarnDesc) {
        String mapped = methodMap.get(yarnOwner + " " + yarnName + " " + mapDesc(yarnDesc));
        return mapped != null ? mapped : yarnName;
    }

    /** Maps a yarn field (owner + name) to the shipped name. */
    public String mapField(String yarnOwner, String yarnName) {
        String mapped = fieldMap.get(yarnOwner + " " + yarnName);
        return mapped != null ? mapped : yarnName;
    }

    /** Remaps every class name inside a method/field descriptor (yarn → shipped). */
    public String mapDesc(String yarnDesc) {
        if (yarnDesc == null || yarnDesc.indexOf('L') < 0) {
            return yarnDesc;
        }
        StringBuilder sb = new StringBuilder(yarnDesc.length());
        int i = 0;
        while (i < yarnDesc.length()) {
            char c = yarnDesc.charAt(i);
            if (c == 'L') {
                int end = yarnDesc.indexOf(';', i);
                String internal = yarnDesc.substring(i + 1, end);
                sb.append('L').append(mapClass(internal)).append(';');
                i = end + 1;
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    /** ASM remapper used to rewrite client classes at load time. */
    public Remapper remapper() {
        return new Remapper() {
            @Override
            public String map(String internalName) {
                return TinyMappings.this.mapClass(internalName);
            }

            @Override
            public String mapMethodName(String owner, String name, String descriptor) {
                return TinyMappings.this.mapMethod(owner, name, descriptor);
            }

            @Override
            public String mapFieldName(String owner, String name, String descriptor) {
                return TinyMappings.this.mapField(owner, name);
            }

            @Override
            public String mapDesc(String descriptor) {
                return TinyMappings.this.mapDesc(descriptor);
            }
        };
    }

    // ── diagnostics (used by VerifyHooks) ─────────────────────────────────

    public int classCount() {
        return classMap.size();
    }

    public int methodCount() {
        return methodMap.size();
    }

    public int fieldCount() {
        return fieldMap.size();
    }
}
