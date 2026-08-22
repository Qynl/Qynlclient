package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import com.qynl.client189.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.util.hit.BlockHitResult;
import org.lwjgl.input.Keyboard;

/**
 * AutoSword — when you attack a mob, QynlClient automatically switches to
 * the strongest sword in your hotbar. Great for players who cannot switch
 * hotbar slots quickly mid-fight: you keep swinging the attack button and
 * your best weapon is always in your hand. The switch uses the normal
 * hotbar-selection path (the same one the 1–9 keys use), so the server sees
 * a perfectly ordinary item switch.
 */
public class AutoSwordModule extends Module {
    private static AutoSwordModule instance;

    public AutoSwordModule() {
        super("AutoSword", "Automatically switches to your strongest sword when you attack.",
                Category.ASSIST);
        instance = this;
        bindKey(Keyboard.KEY_M);
        addSetting(Setting.range("minAdvantage", "Min advantage", 0.5, 0.0, 3.0, 0.5));
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return;
        }
        // Only help while the player is actually attacking a living target.
        if (!client.options.keyAttack.isPressed()) {
            return;
        }
        if (client.result == null || client.result.type != BlockHitResult.Type.ENTITY) {
            return;
        }
        if (!(client.result.entity instanceof LivingEntity)) {
            return;
        }
        LivingEntity target = (LivingEntity) client.result.entity;
        if (!target.isAlive() || target.isInvisible()) {
            return;
        }

        PlayerInventory inventory = client.player.inventory;
        int bestSlot = inventory.selectedSlot;
        float bestDamage = damage(inventory.main[bestSlot]);
        for (int i = 0; i < 9; i++) {
            float d = damage(inventory.main[i]);
            if (d > bestDamage) {
                bestDamage = d;
                bestSlot = i;
            }
        }
        double minAdv = getDoubleSetting("minAdvantage");
        if (bestSlot != inventory.selectedSlot
                && (bestDamage - damage(inventory.main[inventory.selectedSlot])) >= minAdv) {
            inventory.selectedSlot = bestSlot;
        }
    }

    /** Melee damage this stack would deal: sword damage, or bare hands (1). */
    private float damage(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return 1.0F;
        }
        if (stack.getItem() instanceof SwordItem) {
            return ((SwordItem) stack.getItem()).getAttackDamage();
        }
        return 1.0F;
    }
}
