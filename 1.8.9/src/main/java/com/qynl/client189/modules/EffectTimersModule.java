package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.effect.StatusEffectInstance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * EffectTimersModule for 1.8.9 — shows remaining time on active effects.
 * Mirrors the 1.21.1 EffectTimers module.
 */
public class EffectTimersModule extends Module {

    public EffectTimersModule() {
        super("EffectTimers", "Show how much time is left on your active effects.", Category.INFO);
    }

    public List<StatusEffectInstance> getEffects(MinecraftClient client) {
        if (client.player == null) {
            return new ArrayList<>();
        }
        List<StatusEffectInstance> list = new ArrayList<>(client.player.getStatusEffectInstances());
        list.sort(new Comparator<StatusEffectInstance>() {
            @Override
            public int compare(StatusEffectInstance a, StatusEffectInstance b) {
                return Integer.compare(b.getDuration(), a.getDuration());
            }
        });
        return list;
    }
}
