package com.qynl.client189.mixin;

import net.minecraft.client.options.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(KeyBinding.class)
public interface KeyBindingAccessor {
    @Accessor
    boolean getPressed();

    @Accessor
    void setPressed(boolean pressed);

    @Accessor
    int getCode();

    @Accessor
    void setCode(int code);
}
