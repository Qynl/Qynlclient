package com.qynl.client.util;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

import java.util.UUID;

/**
 * BedWars-style teammate detection. BedWars servers register one scoreboard
 * team per team (each with a distinct color) and color the tab-list names,
 * so teammates are reliably identified without any configuration.
 */
public final class TeamHelper {

    private TeamHelper() {
    }

    /** True when both players are on the same team (or no info exists). */
    public static boolean sameTeam(Minecraft mc, Player a, Player b) {
        if (a == null || b == null || mc.level == null) return false;

        // Primary: scoreboard teams — BedWars gives every team its own
        // PlayerTeam instance with a unique color.
        try {
            Scoreboard sb = mc.level.getScoreboard();
            if (sb != null) {
                Team ta = sb.getPlayerTeam(a.getScoreboardName());
                Team tb = sb.getPlayerTeam(b.getScoreboardName());
                if (ta != null && tb != null) {
                    if (ta.equals(tb)) return true;
                    ChatFormatting ca = ta.getColor();
                    ChatFormatting cb = tb.getColor();
                    if (ca != null && ca == cb) return true;
                }
            }
        } catch (Throwable ignored) {
        }

        // Fallback: identical team color in the tab-list display name.
        ChatFormatting ca = tabColor(mc, a.getUUID());
        ChatFormatting cb = tabColor(mc, b.getUUID());
        return ca != null && ca == cb;
    }

    private static ChatFormatting tabColor(Minecraft mc, UUID uuid) {
        try {
            if (mc.getConnection() != null) {
                PlayerInfo info = mc.getConnection().getPlayerInfo(uuid);
                if (info != null) {
                    return firstColor(info.getTabListDisplayName());
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** The first explicit color formatting anywhere in the component tree. */
    private static ChatFormatting firstColor(Component c) {
        if (c == null) return null;
        try {
            Style style = c.getStyle();
            if (style != null) {
                TextColor tc = style.getColor();
                if (tc != null) {
                    for (ChatFormatting cf : ChatFormatting.values()) {
                        Integer col = cf.getColor();
                        if (col != null && col.intValue() == tc.getValue()) {
                            return cf;
                        }
                    }
                }
            }
            for (Component sibling : c.getSiblings()) {
                ChatFormatting f = firstColor(sibling);
                if (f != null) return f;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
