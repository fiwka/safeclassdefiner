package ru.kdev.safeclassdefiner;

import org.objectweb.asm.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

class SafeClassDefiner implements ClassDefiner {

    private static final MethodHandles.Lookup SAFE_CLASS_DEFINER_LOOKUP;
    private static final ClassLoaderAccess CLASS_LOADER_ACCESS;

    private final MethodHandles.Lookup lookup;
    private final StubGenerator stubGenerator;

    static {
        MethodHandles.Lookup lookup = null;

        try {
            JVMStarter.startJVM(SafeDeencapsulator.class);
            Files.delete(Paths.get("temp.jar"));

            try {
                MethodHandles.Lookup defaultLookup = MethodHandles.lookup();
                Constructor<MethodHandles.Lookup> lookupConstructor = MethodHandles.Lookup.class.getDeclaredConstructor(Class.class, Class.class, Integer.TYPE);
                lookupConstructor.setAccessible(true);
                lookup = (MethodHandles.Lookup) defaultLookup.unreflectConstructor(lookupConstructor).invoke(SafeClassDefiner.class, null, -1);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        } catch (Throwable t) {
            t.printStackTrace();
            System.out.println("Trusted lookup unsupported in this enviroment.");
        }

        SAFE_CLASS_DEFINER_LOOKUP = lookup;
        CLASS_LOADER_ACCESS = StubGenerator.getClassLoaderAccess();
    }

    SafeClassDefiner() {
        this.lookup = SAFE_CLASS_DEFINER_LOOKUP;
        this.stubGenerator = new StubGenerator();
    }

    SafeClassDefiner(MethodHandles.Lookup lookup) {
        this.lookup = lookup;
        this.stubGenerator = new StubGenerator();
    }

    @Override
    public Class<?> defineClass(byte[] bytes, ClassLoader classLoader) {
        return defineClass(bytes, classLoader, null);
    }

    @Override
    public Class<?> defineClass(byte[] bytes, ClassLoader classLoader, String unused) {
        Class<?> caller = getCallerClass(2);

        return CLASS_LOADER_ACCESS
                .defineClass1(classLoader, readClassName(bytes), bytes, 0, bytes.length, Objects.requireNonNull(caller == SafeClassDefiner.class ? getCallerClass() : caller).getProtectionDomain(), "__SafeClassDefiner__");
    }

    @Override
    public MethodHandles.Lookup defineHiddenClass(byte[] bytes, boolean initialize, ClassLoader classLoader, MethodHandles.Lookup.ClassOption... options) {
        return defineHiddenClass(bytes, initialize, classLoader, readPackageName(bytes), options);
    }

    @Override
    public MethodHandles.Lookup defineHiddenClass(byte[] bytes, boolean initialize, ClassLoader classLoader, String packageName, MethodHandles.Lookup.ClassOption... options) {
        Class<?> caller = getCallerClass(2);

        return lookup.in(CLASS_LOADER_ACCESS
                .defineClass0(
                        classLoader,
                        stubGenerator.generateStub(classLoader, packageName),
                        readClassName(bytes),
                        bytes,
                        0,
                        bytes.length,
                        Objects.requireNonNull(caller == SafeClassDefiner.class ? getCallerClass() : caller).getProtectionDomain(),
                        initialize,
                        (2 | optionsToFlag(options)),
                        null
                ));
    }

    private int optionsToFlag(MethodHandles.Lookup.ClassOption[] options) {
        int flags = 0;

        for (MethodHandles.Lookup.ClassOption option : options) {
            if (option == MethodHandles.Lookup.ClassOption.NESTMATE)
                flags |= 1;
            else
                flags |= 4;
        }

        return flags;
    }

    private static String readClassName(byte[] bytes) {
        return new ClassReader(bytes).getClassName();
    }

    private static Class<?> getCallerClass() {
        return getCallerClass(3);
    }

    private static Class<?> getCallerClass(int depth) {
        try {
            return Class.forName(Thread.currentThread().getStackTrace()[depth].getClassName());
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private String readPackageName(byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        String[] mass = reader.getClassName().split("/");
        int length = mass.length;

        if (length == 1) {
            return "";
        }

        List<String> list = Arrays.stream(mass).collect(Collectors.toCollection(ArrayList::new));

        list.remove(length - 1);

        return String.join(".", list);
    }

    class StubGenerator {

        private static final Map<StubClassData, Class<?>> DEFINED_STUB_CACHE = new ConcurrentHashMap<>();
        private static final Map<String, Map.Entry<String, byte[]>> EMPTY_CLASS_BYTES_CACHE = new ConcurrentHashMap<>();

        public Class<?> generateStub(ClassLoader classLoader, String packageName) {
            StubClassData classData = new StubClassData(packageName, classLoader);
            Class<?> cachedLookup = DEFINED_STUB_CACHE.get(classData);

            if (cachedLookup != null)
                return cachedLookup;

            Map.Entry<String, byte[]> entry = emptyClassBytes(packageName, true);
            byte[] bytes = entry.getValue();

            try {
                Class<?> lookup = CLASS_LOADER_ACCESS
                                .defineClass1(classLoader, entry.getKey(), bytes, 0, bytes.length, getCallerClass().getProtectionDomain(), "__SafeClassDefiner__");
                DEFINED_STUB_CACHE.put(classData, lookup);
                return lookup;
            } catch (Throwable e) {
                e.printStackTrace();
                return null;
            }
        }

        public static Map.Entry<String, byte[]> emptyClassBytes(String packageName, boolean generateId) {
            Map.Entry<String, byte[]> cachedBytes = EMPTY_CLASS_BYTES_CACHE.get(packageName);

            if (cachedBytes != null)
                return cachedBytes;

            String generatedName = "GeneratedStub" + (generateId ? Math.abs(ThreadLocalRandom.current().nextInt()) : "");

            byte[] bytes = emptyClassBytes(packageName, generatedName);
            Map.Entry<String, byte[]> entry = new AbstractMap.SimpleEntry<>(packageName + "." + generatedName, bytes);

            EMPTY_CLASS_BYTES_CACHE.put(packageName, entry);

            return entry;
        }

        private static ClassLoaderAccess getClassLoaderAccess() {
            ClassWriter writer = new ClassWriter(0);

            writer.visit(
                    Opcodes.V1_8,
                    Opcodes.ACC_PUBLIC,
                    "java/lang/ClassLoaderAccessImpl",
                    null,
                    "java/lang/Object",
                    new String[] { "ru/kdev/safeclassdefiner/ClassLoaderAccess" }
            );

            generateEmptyConstructor(writer);

            MethodVisitor defineClass1 = writer.visitMethod(
                    Opcodes.ACC_PUBLIC,
                    "defineClass1",
                    "(Ljava/lang/ClassLoader;Ljava/lang/String;[BIILjava/security/ProtectionDomain;Ljava/lang/String;)Ljava/lang/Class;",
                    null,
                    null
            );

            defineClass1.visitCode();

            defineClass1.visitVarInsn(Opcodes.ALOAD, 1);
            defineClass1.visitVarInsn(Opcodes.ALOAD, 2);
            defineClass1.visitVarInsn(Opcodes.ALOAD, 3);
            defineClass1.visitVarInsn(Opcodes.ALOAD, 4);
            defineClass1.visitVarInsn(Opcodes.ALOAD, 5);
            defineClass1.visitVarInsn(Opcodes.ALOAD, 6);
            defineClass1.visitVarInsn(Opcodes.ALOAD, 7);
            defineClass1.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "java/lang/ClassLoader",
                    "defineClass1",
                    "(Ljava/lang/ClassLoader;Ljava/lang/String;[BIILjava/security/ProtectionDomain;Ljava/lang/String;)Ljava/lang/Class;",
                    false
            );
            defineClass1.visitInsn(Opcodes.ARETURN);

            defineClass1.visitMaxs(7, 8);
            defineClass1.visitEnd();

            MethodVisitor defineClass0 = writer.visitMethod(
                    Opcodes.ACC_PUBLIC,
                    "defineClass0",
                    "(Ljava/lang/ClassLoader;Ljava/lang/Class;Ljava/lang/String;[BIILjava/security/ProtectionDomain;ZILjava/lang/Object;)Ljava/lang/Class;",
                    null,
                    null
            );

            defineClass0.visitCode();

            defineClass0.visitVarInsn(Opcodes.ALOAD, 1);
            defineClass0.visitVarInsn(Opcodes.ALOAD, 2);
            defineClass0.visitVarInsn(Opcodes.ALOAD, 3);
            defineClass0.visitVarInsn(Opcodes.ALOAD, 4);
            defineClass0.visitVarInsn(Opcodes.ALOAD, 5);
            defineClass0.visitVarInsn(Opcodes.ALOAD, 6);
            defineClass0.visitVarInsn(Opcodes.ALOAD, 7);
            defineClass0.visitVarInsn(Opcodes.ALOAD, 8);
            defineClass0.visitVarInsn(Opcodes.ALOAD, 9);
            defineClass0.visitVarInsn(Opcodes.ALOAD, 10);
            defineClass0.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "java/lang/ClassLoader",
                    "defineClass0",
                    "(Ljava/lang/ClassLoader;Ljava/lang/Class;Ljava/lang/String;[BIILjava/security/ProtectionDomain;ZILjava/lang/Object;)Ljava/lang/Class;",
                    false
            );
            defineClass0.visitInsn(Opcodes.ARETURN);

            defineClass0.visitMaxs(10, 11);
            defineClass0.visitEnd();

            try {
                Method defineClass1Method = ClassLoader.class.getDeclaredMethod("defineClass1", ClassLoader.class, String.class, byte[].class, int.class, int.class, ProtectionDomain.class, String.class);
                defineClass1Method.setAccessible(true);

                MethodHandle handle = SAFE_CLASS_DEFINER_LOOKUP.unreflect(defineClass1Method);

                byte[] bytes = writer.toByteArray();

                byte[] classLoaderAccessBytes = new byte[] { -54, -2, -70, -66, 0, 0, 0, 61, 0, 14, 1, 0, 42, 114, 117, 47, 107, 100, 101, 118, 47, 115, 97, 102, 101, 99, 108, 97, 115, 115, 100, 101, 102, 105, 110, 101, 114, 47, 67, 108, 97, 115, 115, 76, 111, 97, 100, 101, 114, 65, 99, 99, 101, 115, 115, 7, 0, 1, 1, 0, 16, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 79, 98, 106, 101, 99, 116, 7, 0, 3, 1, 0, 22, 67, 108, 97, 115, 115, 76, 111, 97, 100, 101, 114, 65, 99, 99, 101, 115, 115, 46, 106, 97, 118, 97, 1, 0, 12, 100, 101, 102, 105, 110, 101, 67, 108, 97, 115, 115, 49, 1, 0, 114, 40, 76, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 67, 108, 97, 115, 115, 76, 111, 97, 100, 101, 114, 59, 76, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 83, 116, 114, 105, 110, 103, 59, 91, 66, 73, 73, 76, 106, 97, 118, 97, 47, 115, 101, 99, 117, 114, 105, 116, 121, 47, 80, 114, 111, 116, 101, 99, 116, 105, 111, 110, 68, 111, 109, 97, 105, 110, 59, 76, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 83, 116, 114, 105, 110, 103, 59, 41, 76, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 67, 108, 97, 115, 115, 59, 1, 0, 117, 40, 76, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 67, 108, 97, 115, 115, 76, 111, 97, 100, 101, 114, 59, 76, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 83, 116, 114, 105, 110, 103, 59, 91, 66, 73, 73, 76, 106, 97, 118, 97, 47, 115, 101, 99, 117, 114, 105, 116, 121, 47, 80, 114, 111, 116, 101, 99, 116, 105, 111, 110, 68, 111, 109, 97, 105, 110, 59, 76, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 83, 116, 114, 105, 110, 103, 59, 41, 76, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 67, 108, 97, 115, 115, 60, 42, 62, 59, 1, 0, 12, 100, 101, 102, 105, 110, 101, 67, 108, 97, 115, 115, 48, 1, 0, -123, 40, 76, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 67, 108, 97, 115, 115, 76, 111, 97, 100, 101, 114, 59, 76, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 67, 108, 97, 115, 115, 59, 76, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 83, 116, 114, 105, 110, 103, 59, 91, 66, 73, 73, 76, 106, 97, 118, 97, 47, 115, 101, 99, 117, 114, 105, 116, 121, 47, 80, 114, 111, 116, 101, 99, 116, 105, 111, 110, 68, 111, 109, 97, 105, 110, 59, 90, 73, 76, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 79, 98, 106, 101, 99, 116, 59, 41, 76, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 67, 108, 97, 115, 115, 59, 1, 0, -117, 40, 76, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 67, 108, 97, 115, 115, 76, 111, 97, 100, 101, 114, 59, 76, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 67, 108, 97, 115, 115, 60, 42, 62, 59, 76, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 83, 116, 114, 105, 110, 103, 59, 91, 66, 73, 73, 76, 106, 97, 118, 97, 47, 115, 101, 99, 117, 114, 105, 116, 121, 47, 80, 114, 111, 116, 101, 99, 116, 105, 111, 110, 68, 111, 109, 97, 105, 110, 59, 90, 73, 76, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 79, 98, 106, 101, 99, 116, 59, 41, 76, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 67, 108, 97, 115, 115, 60, 42, 62, 59, 1, 0, 9, 83, 105, 103, 110, 97, 116, 117, 114, 101, 1, 0, 10, 83, 111, 117, 114, 99, 101, 70, 105, 108, 101, 6, 1, 0, 2, 0, 4, 0, 0, 0, 0, 0, 2, 4, 1, 0, 6, 0, 7, 0, 1, 0, 12, 0, 0, 0, 2, 0, 8, 4, 1, 0, 9, 0, 10, 0, 1, 0, 12, 0, 0, 0, 2, 0, 11, 0, 1, 0, 13, 0, 0, 0, 2, 0, 5 };

                Set<Module> classLoaderAccessModule = Collections.singleton(((Class<?>) handle.invoke(
                        null,
                        "ru.kdev.safeclassdefiner.ClassLoaderAccess",
                        classLoaderAccessBytes,
                        0,
                        classLoaderAccessBytes.length,
                        SafeClassDefiner.class.getProtectionDomain(),
                        "__SafeClassDefiner__"
                )).getModule());

                SafeDeencapsulator.instrumentation.redefineModule(
                        Object.class.getModule(),
                        classLoaderAccessModule,
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        Collections.emptySet(),
                        Collections.emptyMap()
                );

                return (ClassLoaderAccess) ((Class<?>) handle
                        .invoke(null, "java.lang.ClassLoaderAccessImpl", bytes, 0, bytes.length, ClassLoader.class.getProtectionDomain(), "__SafeClassDefiner__"))
                        .getDeclaredConstructors()[0]
                        .newInstance();
            } catch (Throwable e) {
                e.printStackTrace();
                return null;
            }
        }

        public static byte[] emptyClassBytes(String packageName, String className) {
            ClassWriter writer = new ClassWriter(0);

            writer.visit(
                    Opcodes.V1_8,
                    Opcodes.ACC_PUBLIC,
                    packageName.replace(".", "/") + "/" + className,
                    null,
                    "java/lang/Object",
                    null
            );

            generateEmptyConstructor(writer);

            return writer.toByteArray();
        }

        private static void generateEmptyConstructor(ClassVisitor visitor) {
            MethodVisitor emptyConstructor = visitor.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);

            emptyConstructor.visitCode();

            emptyConstructor.visitVarInsn(Opcodes.ALOAD, 0);
            emptyConstructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            emptyConstructor.visitInsn(Opcodes.RETURN);

            emptyConstructor.visitMaxs(1, 1);
            emptyConstructor.visitEnd();
        }
    }
    
    static class StubClassData {
        
        String packageName;
        ClassLoader classLoader;

        public StubClassData(String packageName, ClassLoader classLoader) {
            this.packageName = packageName;
            this.classLoader = classLoader;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            StubClassData that = (StubClassData) o;
            return packageName.equals(that.packageName) && classLoader.equals(that.classLoader);
        }

        @Override
        public int hashCode() {
            return Objects.hash(packageName, classLoader);
        }
    }
}
