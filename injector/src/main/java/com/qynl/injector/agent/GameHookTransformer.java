package com.qynl.injector.agent;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Rewrites the obfuscated 1.8.9 game classes to call into
 * {@code com.qynl.client189.GameHooks} at the same points the old Fabric
 * mixins hooked.
 *
 * <p>Only <em>method-body</em> call-site insertions are made: no interfaces,
 * fields or methods are added, so the same transformer works in both launch
 * mode ({@code -javaagent}, classes transformed on load) and attach mode
 * (already-loaded classes retransformed). Private/protected member access
 * that the old Mixin accessors provided is done via
 * {@link com.qynl.client189.ReflectionAccess} instead.</p>
 *
 * <p>All injected call sites use only primitives / {@link Object}
 * descriptors so the injected bytecode never has to spell out an obfuscated
 * type. The target class/method names are resolved from the bundled yarn
 * mappings at runtime.</p>
 */
public final class GameHookTransformer implements ClassFileTransformer {

    private static final String GAMEHOOKS = "com/qynl/client189/GameHooks";

    private static final String YARN_MINECRAFT = "net/minecraft/client/MinecraftClient";

    private enum Kind {
        HEAD_VOID,          // call void hook at head
        HEAD_OBJ1,          // call hook(Object arg1) at head
        HEAD_CANCEL,        // cancel void method if hook()Z returns true
        HEAD_CANCEL_OBJ1,   // cancel if hook(Object arg1)Z returns true
        HEAD_CANCEL_OBJ0_3D,// cancel if hook(Object this, double,double,double)Z returns true
        TAIL_VOID,          // call void hook before RETURN
        TAIL_F1,            // call hook(float arg1) before RETURN
        TAIL_OBJ0,          // call hook(Object this) before RETURN
        RETURN_F            // fold hook(float)float before every FRETURN
    }

    private static final class HookSpec {
        final String yarnClass;
        final String yarnNameOwner; // class whose mapping resolves the method name (may differ for interface methods)
        final String yarnMethod;
        final String yarnDesc;
        final Kind kind;
        final String hookName;
        final String hookDesc;

        HookSpec(String yarnClass, String yarnMethod, String yarnDesc, Kind kind, String hookName, String hookDesc) {
            this(yarnClass, yarnClass, yarnMethod, yarnDesc, kind, hookName, hookDesc);
        }

        HookSpec(String yarnClass, String yarnNameOwner, String yarnMethod, String yarnDesc,
                 Kind kind, String hookName, String hookDesc) {
            this.yarnClass = yarnClass;
            this.yarnNameOwner = yarnNameOwner;
            this.yarnMethod = yarnMethod;
            this.yarnDesc = yarnDesc;
            this.kind = kind;
            this.hookName = hookName;
            this.hookDesc = hookDesc;
        }

        String obfClass() {
            return TinyMappings.get().mapClass(yarnClass);
        }

        String obfMethod() {
            return TinyMappings.get().mapMethod(yarnNameOwner, yarnMethod, yarnDesc);
        }

        String obfDesc() {
            return TinyMappings.get().mapDesc(yarnDesc);
        }
    }

    private static final HookSpec[] HOOKS = {
            new HookSpec(YARN_MINECRAFT, "tick", "()V", Kind.HEAD_VOID, "onClientTick", "()V"),
            new HookSpec("net/minecraft/client/gui/hud/InGameHud", "render", "(F)V", Kind.TAIL_VOID, "renderHud", "()V"),
            new HookSpec("net/minecraft/client/network/ClientPlayNetworkHandler", "sendPacket",
                    "(Lnet/minecraft/network/Packet;)V", Kind.HEAD_CANCEL_OBJ1, "onSendPacket", "(Ljava/lang/Object;)Z"),
            // onKeepAlive/onPlaySound are interface methods (ClientPlayPacketListener)
            // in this yarn — the handler class implements them under the obfuscated
            // interface names, so resolve the names against the interface.
            new HookSpec("net/minecraft/client/network/ClientPlayNetworkHandler",
                    "net/minecraft/network/listener/ClientPlayPacketListener", "onKeepAlive",
                    "(Lnet/minecraft/network/packet/s2c/play/KeepAliveS2CPacket;)V", Kind.HEAD_VOID, "onKeepAliveReceived", "()V"),
            new HookSpec("net/minecraft/client/network/ClientPlayNetworkHandler",
                    "net/minecraft/network/listener/ClientPlayPacketListener", "onPlaySound",
                    "(Lnet/minecraft/network/packet/s2c/play/PlaySoundIdS2CPacket;)V", Kind.HEAD_OBJ1, "onPlaySound", "(Ljava/lang/Object;)V"),
            new HookSpec("net/minecraft/entity/Entity", "addVelocity", "(DDD)V", Kind.HEAD_CANCEL_OBJ0_3D,
                    "onAddVelocity", "(Ljava/lang/Object;DDD)Z"),
            new HookSpec(YARN_MINECRAFT, "doAttack", "()V", Kind.HEAD_VOID, "onAttack", "()V"),
            new HookSpec("net/minecraft/client/network/ClientPlayerInteractionManager", "getReachDistance", "()F",
                    Kind.RETURN_F, "onGetReachDistance", "(F)F"),
            new HookSpec("net/minecraft/client/render/GameRenderer", "bobViewWhenHurt", "(F)V",
                    Kind.HEAD_CANCEL, "shouldSkipHurtCam", "()Z"),
            new HookSpec("net/minecraft/client/render/GameRenderer", "bobView", "(F)V",
                    Kind.HEAD_CANCEL, "shouldSkipViewBob", "()Z"),
            new HookSpec("net/minecraft/client/render/GameRenderer", "renderWorld", "(FJ)V",
                    Kind.TAIL_F1, "renderWorld", "(F)V"),
            new HookSpec("net/minecraft/client/input/Input", "tick", "()V", Kind.TAIL_OBJ0, "onInputTick", "(Ljava/lang/Object;)V"),
    };

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (className == null || !isTarget(className)) {
            return null;
        }
        try {
            return transformClass(className, classfileBuffer);
        } catch (Throwable t) {
            System.err.println("[Qyn-L] hook transform failed for " + className + ": " + t);
            t.printStackTrace();
            return null;
        }
    }

    private boolean isTarget(String internalName) {
        for (HookSpec h : HOOKS) {
            if (h.obfClass().equals(internalName)) {
                return true;
            }
        }
        return false;
    }

    private byte[] transformClass(String obfName, byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM5, writer) {
            private String className;

            @Override
            public void visit(int version, int access, String name, String signature,
                              String superName, String[] interfaces) {
                this.className = name;
                super.visit(version, access, name, signature, superName, interfaces);
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                HookSpec hook = findHook(className, name, desc);
                if (hook != null) {
                    return new HookMethodVisitor(mv, hook);
                }
                return mv;
            }
        };
        reader.accept(visitor, 0);
        return writer.toByteArray();
    }

    private HookSpec findHook(String obfClassName, String methodName, String methodDesc) {
        for (HookSpec h : HOOKS) {
            if (h.obfClass().equals(obfClassName)
                    && h.obfMethod().equals(methodName)
                    && h.obfDesc().equals(methodDesc)) {
                return h;
            }
        }
        return null;
    }

    /** Class internal names of every class this transformer modifies. */
    public static String[] targetClassInternalNames() {
        Set<String> names = new LinkedHashSet<>();
        for (HookSpec h : HOOKS) {
            names.add(h.obfClass());
        }
        return names.toArray(new String[0]);
    }

    /** Hook markers (constant-pool strings) expected after transforming a class. */
    public static List<String> expectedMarkers(String obfClassName) {
        List<String> markers = new ArrayList<>();
        for (HookSpec h : HOOKS) {
            if (h.obfClass().equals(obfClassName)) {
                markers.add(h.hookName);
            }
        }
        return markers;
    }

    private static final class HookMethodVisitor extends MethodVisitor {

        private final HookSpec hook;

        HookMethodVisitor(MethodVisitor mv, HookSpec hook) {
            super(Opcodes.ASM5, mv);
            this.hook = hook;
        }

        @Override
        public void visitCode() {
            super.visitCode();
            switch (hook.kind) {
                case HEAD_VOID:
                    emitInvoke(hook.hookName, hook.hookDesc);
                    break;
                case HEAD_OBJ1:
                    mv.visitVarInsn(Opcodes.ALOAD, 1);
                    emitInvoke(hook.hookName, hook.hookDesc);
                    break;
                case HEAD_CANCEL:
                    emitCancelHead(false, false);
                    break;
                case HEAD_CANCEL_OBJ1:
                    emitCancelHead(true, false);
                    break;
                case HEAD_CANCEL_OBJ0_3D:
                    emitCancelHead(false, true);
                    break;
                default:
                    break;
            }
        }

        @Override
        public void visitInsn(int opcode) {
            switch (hook.kind) {
                case TAIL_VOID:
                    if (opcode == Opcodes.RETURN) {
                        emitInvoke(hook.hookName, hook.hookDesc);
                    }
                    break;
                case TAIL_F1:
                    if (opcode == Opcodes.RETURN) {
                        mv.visitVarInsn(Opcodes.FLOAD, 1);
                        emitInvoke(hook.hookName, hook.hookDesc);
                    }
                    break;
                case TAIL_OBJ0:
                    if (opcode == Opcodes.RETURN) {
                        mv.visitVarInsn(Opcodes.ALOAD, 0);
                        emitInvoke(hook.hookName, hook.hookDesc);
                    }
                    break;
                case RETURN_F:
                    if (opcode == Opcodes.FRETURN) {
                        emitInvoke(hook.hookName, hook.hookDesc);
                    }
                    break;
                default:
                    break;
            }
            super.visitInsn(opcode);
        }

        private void emitCancelHead(boolean loadArg1, boolean loadThisAnd3Doubles) {
            if (loadArg1) {
                mv.visitVarInsn(Opcodes.ALOAD, 1);
            }
            if (loadThisAnd3Doubles) {
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitVarInsn(Opcodes.DLOAD, 1);
                mv.visitVarInsn(Opcodes.DLOAD, 3);
                mv.visitVarInsn(Opcodes.DLOAD, 5);
            }
            emitInvoke(hook.hookName, hook.hookDesc);
            Label cont = new Label();
            mv.visitJumpInsn(Opcodes.IFEQ, cont);
            mv.visitInsn(Opcodes.RETURN);
            mv.visitLabel(cont);
        }

        private void emitInvoke(String name, String desc) {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, GAMEHOOKS, name, desc, false);
        }
    }
}
