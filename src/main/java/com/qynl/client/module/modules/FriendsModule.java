package com.qynl.client.module.modules;	import com.qynl.client.Friends;
	import com.qynl.client.module.Category;
	import com.qynl.client.module.Module;
	import com.qynl.client.module.Setting;
	import net.minecraft.client.Minecraft;
	import net.minecraft.world.entity.Entity;
	import net.minecraft.world.entity.player.Player;
	import org.lwjgl.glfw.GLFW;

/**
 * FriendsModule — stores a comma-separated list of friend names.
 * When enabled, AimAssist skips them and render modules color them green.
 */
public class FriendsModule extends Module {
    private static FriendsModule instance;	public FriendsModule() {
		super("Friends", "Comma-separated names that are never targeted and render green.",
				Category.OTHER);
        instance = this;
        bindKey(GLFW.GLFW_KEY_UNKNOWN);
        addSetting(Setting.text("names", "Names", ""));
    }

    public static FriendsModule getInstance() { return instance; }

    @Override
    public void onTick(Minecraft client) {
        // Sync the names setting to the Friends registry
        String names = getStringSetting("names");
        Friends.load(names);
    }	@Override
	public void onEnable() {
		String names = getStringSetting("names");
		Friends.load(names);
	}

	/** True when the given entity display name is in the friend list. */
	public static boolean isFriend(String name) {
		return Friends.isFriend(name);
	}

	/** Display name of an entity (player username or custom name). */
	public static String entityName(Entity entity) {
		if (entity instanceof Player player) {
			com.mojang.authlib.GameProfile profile = player.getGameProfile();
			return profile == null ? null : profile.getName();
		}
		if (entity.getCustomName() != null) {
			return entity.getCustomName().getString();
		}
		return null;
	}
}