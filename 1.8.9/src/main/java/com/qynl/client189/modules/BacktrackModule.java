package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import com.qynl.client189.Setting;
import com.qynl.client189.mixin.BacktrackMixin;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;

/**
 * Backtrack — holds opponents' hitboxes a few milliseconds in the past.
 *
 * <p>Every inbound entity-movement packet ({@code onEntityUpdate},
 * {@code onEntityPosition}, {@code onEntitySetHeadYaw}) is buffered by
 * {@link BacktrackMixin} and only applied {@code delay} ms later. Because the
 * client-side position — and therefore the hitbox — of every entity trails
 * its true server position by that window, an opponent who sprints away
 * leaves their box behind for a moment: you can still hit them where they
 * just were, exactly like fighting a high-ping player. Buffered packets are
 * replayed every tick in arrival order, and the whole queue is flushed on
 * disable so nothing is ever lost.</p>
 *
 * <p>Honest note: the server validates reach against the defender's current
 * position, so the delayed hit only registers where the server's own lag
 * compensation accepts it (practice / plugin servers with client-authoritative
 * hit detection, or high-ping-compensated reach checks). On vanilla servers
 * this is primarily a client-side hitbox + visual effect.</p>
 */
public class BacktrackModule extends Module {
    private static BacktrackModule instance;

    public BacktrackModule() {
        super("Backtrack",
                "Delays opponents' movement packets so their hitboxes lag a few ms behind them — hit them where they just were.",
                Category.ASSIST);
        instance = this;
        bindKey(Keyboard.KEY_B);
        addSetting(Setting.range("delay", "Delay", 150.0, 50, 400, 10, "ms"));
    }

    public static BacktrackModule getInstance() { return instance; }
    public static boolean isActive() { return instance != null && instance.isEnabled(); }
    public static int getDelayMs() { return instance == null ? 150 : (int) instance.getDoubleSetting("delay"); }

    @Override
    public void onTick(MinecraftClient client) {
        // Replay packets whose hold window has elapsed (arrival order).
        BacktrackMixin.pump(client.getNetworkHandler(), getDelayMs());
    }

    @Override
    public void onDisable() {
        // Apply everything still buffered so entities snap to their true
        // position instead of freezing forever.
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() != null) {
            BacktrackMixin.flush(client.getNetworkHandler());
        }
    }

    /** Live HUD line: configured delay plus how many packets are buffered. */
    public List<String> getHudLines() {
        List<String> lines = new ArrayList<>();
        int buffered = BacktrackMixin.bufferedCount();
        lines.add("Backtrack \u00b7 " + getDelayMs() + "ms" + (buffered > 0 ? " \u00b7 buf " + buffered : ""));
        return lines;
    }
}
