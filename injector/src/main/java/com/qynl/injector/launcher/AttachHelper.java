package com.qynl.injector.launcher;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Attach-mode support: lists running JVMs (via {@code com.sun.tools.attach})
 * and loads the agent jar into one of them. Uses reflection so the launcher
 * compiles and runs on both Java 8 (tools.jar) and JDK 9+ (jdk.attach module).
 */
public final class AttachHelper {

    private AttachHelper() {
    }

    public static boolean available() {
        try {
            Class.forName("com.sun.tools.attach.VirtualMachine");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Returns [pid, displayName] pairs for every running JVM. */
    public static List<String[]> listJvms() throws Exception {
        Class<?> vmClass = Class.forName("com.sun.tools.attach.VirtualMachine");
        Method list = vmClass.getMethod("list");
        List<?> vms = (List<?>) list.invoke(null);
        List<String[]> out = new ArrayList<>();
        for (Object vm : vms) {
            Method id = vm.getClass().getMethod("id");
            Method display = vm.getClass().getMethod("displayName");
            String pid = String.valueOf(id.invoke(vm));
            String name = String.valueOf(display.invoke(vm));
            out.add(new String[]{pid, name});
        }
        return out;
    }

    /** Loads the agent jar into the JVM with the given pid. */
    public static void attach(String pid, String agentJar) throws Exception {
        Class<?> vmClass = Class.forName("com.sun.tools.attach.VirtualMachine");
        Method attach = vmClass.getMethod("attach", String.class);
        Object vm = attach.invoke(null, pid);
        try {
            Method loadAgent = vm.getClass().getMethod("loadAgent", String.class);
            loadAgent.invoke(vm, agentJar);
        } finally {
            Method detach = vm.getClass().getMethod("detach");
            detach.invoke(vm);
        }
    }
}
