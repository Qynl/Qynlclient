package com.qynl.client189.mixin;

import com.qynl.client189.modules.AutoStepModule;
import com.qynl.client189.modules.AutoWalkModule;
import com.qynl.client189.modules.InvWalkModule;
import com.qynl.client189.modules.NoSlowModule;
import com.qynl.client189.modules.ToggleSneakModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.entity.player.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Input overrides for 1.8.9 — applied AFTER vanilla {@link Input#tick} has
 * read the keyboard, which is the only reliable moment to force movement
 * flags: the values written here are consumed by the player's movement later
 * in the same tick.
 *
 * <p>Handles the input side of AutoStep (jump at ledges), ToggleSneak
 * (hold sneak), AutoWalk (hold forward), InvWalk (walk while a screen is
 * open) and NoSlow (undoes the item-use slowdown, which vanilla applies to
 * the input right after {@code tick}).</p>
 */
@Mixin(Input.class)
public abstract class InputMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void qynlclient189$applyInputOverrides(CallbackInfo ci) {
        Input input = (Input) (Object) this;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            return;
        }
        if (input != client.player.input) {
            return;
        }

        // ── ToggleSneak — sneak without holding the key ──
        if (ToggleSneakModule.isActive()) {
            input.sneaking = true;
        }

        // ── AutoStep — jump when walking into a low ledge ──
        if (AutoStepModule.shouldJump(client)) {
            input.jumping = true;
        }

        // ── AutoWalk — hold forward ──
        if (AutoWalkModule.isActive()) {
            input.movementForward = 1.0F;
        }

        // ── InvWalk — keep moving while a screen is open. Key bindings do
        //    not update while a screen consumes keyboard input, so read the
        //    raw LWJGL key state instead.
        InvWalkModule.apply(client, input);

        // ── NoSlow — undo the 0.2× item-use slowdown before it is applied ──
        // Vanilla multiplies input.movementForward/Sideways by 0.2 right
        // after Input.tick() when the player is using an item (eating,
        // blocking, drawing a bow). Countering it here — after every other
        // override — keeps movement fluid without touching any packets.
        if (NoSlowModule.isActive() && client.player.isUsingItem() && !client.player.hasVehicle()) {
            input.movementForward *= 5.0F;
            input.movementSideways *= 5.0F;
        }
    }
}
