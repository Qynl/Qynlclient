package com.qynl.legacy;

import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Tiny key-value config saved next to the Minecraft folder.
 * Persists which modules are enabled so they survive restarts.
 */
public class LegacyConfig {
	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("qynlclient-legacy.txt");
	}

	public static void save(com.qynl.legacy.module.ModuleManager modules) {
		try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(path()))) {
			for (com.qynl.legacy.module.Module m : modules.getModules()) {
				w.println(m.getName() + "=" + m.isEnabled());
			}
		} catch (IOException ignored) {}
	}

	public static void load(com.qynl.legacy.module.ModuleManager modules) {
		try {
			Map<String,Boolean> states = new HashMap<>();
			for (String line : Files.readAllLines(path())) {
				String[] parts = line.split("=", 2);
				if (parts.length == 2) states.put(parts[0], "true".equalsIgnoreCase(parts[1]));
			}
			for (com.qynl.legacy.module.Module m : modules.getModules()) {
				Boolean v = states.get(m.getName());
				if (v != null && v) m.setEnabled(true);
			}
		} catch (IOException ignored) {}
	}
}
