package com.qynl.client189.mixin;

import com.qynl.client189.modules.NoWebModule;
import net.minecraft.block.BlockState;
import net.minecraft.block.CobwebBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NoWebMixin — the movement side of {@link NoWebModule}.
 *
 * <p>In 1.8.9 the cobweb slowdown is applied client-side when the block
 * collision handler {@code CobwebBlock.onEntityCollision} runs — it sets the
 * web flag on the entity, and the movement code then dampens velocity. For
 * the local player the handler is skipped entirely, so webs never slow the
 * player down. Nothing is sent to the server, making this silent.</p>
 */
@Mixin(CobwebBlock.class)
public abstract class NoWebMixin {

    @Inject(method = "onEntityCollision", at = @At("HEAD"), cancellable = true)
    private void qynl189$noWeb(World world, BlockPos pos, BlockState state, Entity entity, CallbackInfo ci) {
        if (!NoWebModule.isActive()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && entity == client.player) {
            ci.cancel();
        }
    }
}
