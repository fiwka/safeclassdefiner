package ru.kdev.safeclassdefiner;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static java.util.jar.Attributes.Name.MANIFEST_VERSION;

public final class JVMStarter {

    public static void startJVM(Class<?> mainClass) {
        try {
            ProcessHandle.Info info = ProcessHandle.current().info();
            List<String> commandLine = new LinkedList<>();
            Path tempJar = createJar(mainClass.getName());

            commandLine.add(info.command().orElse("java"));
            commandLine.add("-Dfile.encoding=UTF-8");
            commandLine.add("-cp");
            commandLine.add(tempJar.toAbsolutePath().toString());
            commandLine.add(mainClass.getName());
            commandLine.add(Long.toString(ManagementFactory.getRuntimeMXBean().getPid()));

            new ProcessBuilder(commandLine)
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                    .start()
                    .waitFor();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private static Manifest createManifest(String mainClassName) {
        Manifest manifest = new Manifest();

        Attributes attributes = manifest.getMainAttributes();

        attributes.put(MANIFEST_VERSION, "1.0");
        attributes.putValue("Agent-Class", mainClassName);
        attributes.putValue("Can-Redefine-Classes", "true");
        attributes.putValue("Can-Retransform-Classes", "true");

        return manifest;
    }

    private static Path createJar(String mainClassName) throws IOException {
        Path temp = Paths.get("temp.jar");

        if (!Files.exists(temp))
            Files.createFile(temp);

        JarOutputStream jos = new JarOutputStream(Files.newOutputStream(temp), createManifest(mainClassName));
        String entryName = mainClassName.replace(".", "/") + ".class";
        JarEntry entry = new JarEntry(entryName);
        byte[] bytes = Objects.requireNonNull(JVMStarter.class.getResourceAsStream("/" + entryName)).readAllBytes();
        jos.putNextEntry(entry);
        jos.write(bytes);
        jos.closeEntry();
        jos.close();
        return temp;
    }
}
