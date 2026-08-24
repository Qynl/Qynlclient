package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import com.qynl.client189.Setting;
import com.mojang.authlib.GameProfile;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.lwjgl.input.Keyboard;

/**
 * Friends — a comma-separated list of player names that combat and render
 * modules never target (AimAssist skips them, Tracers/NameTags color them
 * green). Vape-style friend protection with zero extra UI.
 */
public class FriendsModule extends Module {
    private static FriendsModule instance;

    public FriendsModule() {
        super("Friends", "Player names that are never targeted (comma separated).", Category.OTHER);
        instance = this;
        bindKey(Keyboard.KEY_NONE);
        addSetting(Setting.text("names", "Names", ""));
    }

    /** True when the given entity display name is in the friend list. */
    public static boolean isFriend(String name) {
        if (instance == null || !instance.isEnabled() || name == null || name.isEmpty()) {
            return false;
        }
        String names = instance.getStringSetting("names");
        if (names == null || names.isEmpty()) {
            return false;
        }
        for (String part : names.split(",")) {
            if (part.trim().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    /** Display name of an entity (player username or custom name). */
    public static String entityName(Entity entity) {
        if (entity instanceof PlayerEntity) {
            GameProfile profile = ((PlayerEntity) entity).getGameProfile();
            return profile == null ? null : profile.getName();
        }
        return entity.getCustomName();
    }
}
