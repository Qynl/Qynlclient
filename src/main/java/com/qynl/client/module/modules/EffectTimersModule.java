package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class EffectTimersModule extends Module {
	public EffectTimersModule() {
		super("EffectTimers", "Show how much time is left on your active effects.", Category.INFO);
	}

	public List<MobEffectInstance> getEffects(Minecraft client) {
		if (client.player == null) {
			return List.of();
		}
		List<MobEffectInstance> list = new ArrayList<>(client.player.getActiveEffects());
		list.sort(Comparator.comparingInt(MobEffectInstance::getDuration).reversed());
		return list;
	}
}
