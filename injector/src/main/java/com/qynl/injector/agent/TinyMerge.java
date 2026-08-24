package com.qynl.injector.agent;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Build-time tool (run by the {@code extractMappings} Gradle task) that merges
 * the two halves of the 1.8.9 yarn mappings into the single tiny v2 file the
 * agent bundles:
 *
 * <pre>
 *   intermediary-v2 (official → intermediary) + yarn-v2 (intermediary → named)
 *   → tiny v2 with namespaces: official, intermediary, named
 * </pre>
 *
 * Both inputs are jars containing {@code mappings/mappings.tiny}. Member lines
 * are matched by (owner-intermediary, name-intermediary) — intermediary names
 * are unique per member, so overloads stay distinct. The descriptor column is
 * taken from the official-side file (the shipped obfuscated descriptor).
 *
 * <p>Usage: {@code TinyMerge <intermediary.jar> <yarn.jar> <out.tiny>}</p>
 */
public final class TinyMerge {

    private TinyMerge() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("usage: TinyMerge <intermediary.jar> <yarn.jar> <out.tiny>");
            System.exit(2);
        }
        String interPath = args[0];
        String yarnPath = args[1];
        String outPath = args[2];

        // ── official → intermediary ──────────────────────────────────────
        Map<String, String> classOff = new HashMap<>();       // inter → off
        Map<String, String[]> memberOff = new HashMap<>();    // ownerInter+"\t"+nameInter → [descOff, nameOff]
        String currentInter = null;
        try (BufferedReader r = reader(readTiny(interPath))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty()) continue;
                if (line.charAt(0) == 'c') {
                    String[] t = line.split("\t");
                    if (t.length >= 3) {
                        currentInter = t[2];
                        classOff.put(currentInter, t[1]);
                    }
                } else if (line.charAt(0) == '\t' && currentInter != null) {
                    String[] t = line.split("\t");
                    if (t.length >= 5 && ("f".equals(t[1]) || "m".equals(t[1]))) {
                        memberOff.put(currentInter + "\t" + t[3], new String[]{t[2], t[4]});
                    }
                }
            }
        }

        // ── intermediary → named ─────────────────────────────────────────
        Map<String, String> classNamed = new HashMap<>();     // inter → named
        Map<String, String> memberNamed = new HashMap<>();    // ownerInter+"\t"+nameInter → named
        currentInter = null;
        try (BufferedReader r = reader(readTiny(yarnPath))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty()) continue;
                if (line.charAt(0) == 'c') {
                    String[] t = line.split("\t");
                    if (t.length >= 3) {
                        currentInter = t[1];
                        classNamed.put(currentInter, t[2]);
                    }
                } else if (line.charAt(0) == '\t' && currentInter != null) {
                    String[] t = line.split("\t");
                    if (t.length >= 5 && ("f".equals(t[1]) || "m".equals(t[1]))) {
                        memberNamed.put(currentInter + "\t" + t[3], t[4]);
                    }
                }
            }
        }

        // ── merge (walk the official side to keep order/descs) ───────────
        try (PrintWriter w = new PrintWriter(new OutputStreamWriter(new FileOutputStream(outPath), StandardCharsets.UTF_8))) {
            w.println("tiny\t2\t0\tofficial\tintermediary\tnamed");
            currentInter = null;
            try (BufferedReader r = reader(readTiny(interPath))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.isEmpty()) continue;
                    if (line.charAt(0) == 'c') {
                        String[] t = line.split("\t");
                        if (t.length >= 3) {
                            currentInter = t[2];
                            String named = classNamed.get(currentInter);
                            w.println("c\t" + t[1] + "\t" + t[2] + "\t" + (named != null ? named : t[2]));
                        }
                    } else if (line.charAt(0) == '\t' && currentInter != null) {
                        String[] t = line.split("\t");
                        if (t.length >= 5 && ("f".equals(t[1]) || "m".equals(t[1]))) {
                            String named = memberNamed.get(currentInter + "\t" + t[3]);
                            w.println("\t" + t[1] + "\t" + t[2] + "\t" + t[3] + "\t" + t[4]
                                    + "\t" + (named != null ? named : t[4]));
                        }
                        // property / comment lines are dropped — the remapper
                        // only needs names and descriptors.
                    }
                }
            }
        }
    }

    private static byte[] readTiny(String jarPath) throws IOException {
        try (ZipFile zip = new ZipFile(jarPath)) {
            ZipEntry entry = zip.getEntry("mappings/mappings.tiny");
            if (entry == null) {
                throw new IOException("no mappings/mappings.tiny in " + jarPath);
            }
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            try (java.io.InputStream in = zip.getInputStream(entry)) {
                while ((n = in.read(buf)) >= 0) {
                    out.write(buf, 0, n);
                }
            }
            return out.toByteArray();
        }
    }

    private static BufferedReader reader(byte[] bytes) {
        return new BufferedReader(new InputStreamReader(new java.io.ByteArrayInputStream(bytes), StandardCharsets.UTF_8));
    }
}
