package ru.kdev.safeclassdefiner;

import com.sun.tools.attach.AgentInitializationException;
import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

class SafeDeencapsulator {

    public static void main(String[] args) throws IOException, AttachNotSupportedException, AgentLoadException, AgentInitializationException {
        VirtualMachine virtualMachine = VirtualMachine.attach(args[0]);
        virtualMachine.loadAgent(Paths.get("temp.jar").toAbsolutePath().toString());
        virtualMachine.detach();
    }

    public static void agentmain(String args, Instrumentation instrumentation) {
        Module javaBase = Object.class.getModule();
        Module moduleOfSafeClassDefiner = SafeClassDefiner.class.getModule();
        Set<Module> singletonSet = Set.of(moduleOfSafeClassDefiner);

        instrumentation.redefineModule(
                javaBase,
                Collections.emptySet(),
                Map.of(
                        "jdk.internal.misc", singletonSet,
                        "jdk.internal.loader", singletonSet
                ),
                Map.of(
                        "jdk.internal.misc", singletonSet,
                        "jdk.internal.loader", singletonSet,
                        "java.lang.invoke", singletonSet,
                        "java.lang", singletonSet
                ),
                Collections.emptySet(),
                Collections.emptyMap()
        );
    }
}
