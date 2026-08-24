package com.qynl.client189.access;

/**
 * Injected onto the runtime {@code KeyBinding} class by the agent so client
 * code can read/write the private {@code pressed} / {@code code} fields
 * without Mixin accessors. The injected implementations reference the
 * obfuscated field names directly.
 */
public interface IKeyBindingAccess {
    boolean qynlIsPressed();

    void qynlSetPressed(boolean pressed);

    int qynlGetCode();

    void qynlSetCode(int code);
}
