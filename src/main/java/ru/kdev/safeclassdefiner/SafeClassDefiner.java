package ru.kdev.safeclassdefiner;

import jdk.internal.loader.ClassLoaders;
import org.objectweb.asm.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

class SafeClassDefiner implements ClassDefiner {

    private static final MethodHandles.Lookup SAFE_CLASS_DEFINER_LOOKUP;
    private static final ClassLoader BOOT_CLASS_LOADER;

    private final MethodHandles.Lookup lookup;
    private final StubGenerator stubGenerator;

    static {
        MethodHandles.Lookup lookup = null;
        ClassLoader bootClassLoader = null;

        try {
            Deencapsulation.deencapsulate(SafeClassDefiner.class);

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

        try {
            MethodHandles.Lookup privateLookupInClassLoaders = MethodHandles.privateLookupIn(ClassLoaders.class, MethodHandles.lookup());
            Field field = privateLookupInClassLoaders.lookupClass().getDeclaredField("BOOT_LOADER");
            bootClassLoader = (ClassLoader) privateLookupInClassLoaders.unreflectVarHandle(field).get();
        } catch (Throwable t) {
            t.printStackTrace();
        }

        SAFE_CLASS_DEFINER_LOOKUP = lookup;
        BOOT_CLASS_LOADER = bootClassLoader;
    }

    SafeClassDefiner() {
        this.lookup = SAFE_CLASS_DEFINER_LOOKUP;
        this.stubGenerator = new StubGenerator(false);
    }

    SafeClassDefiner(MethodHandles.Lookup lookup) {
        this.lookup = lookup;
        this.stubGenerator = new StubGenerator(false);
    }

    @Override
    public Class<?> defineClass(byte[] bytes, ClassLoader classLoader) {
        return defineClass(bytes, classLoader, readPackageName(bytes));
    }

    @Override
    public Class<?> defineClass(byte[] bytes, ClassLoader classLoader, String packageName) {
        try {
            if (classLoader == null) {
                setBoot(true);
                Class<?> klass = Objects.requireNonNull(stubGenerator.generateStub(BOOT_CLASS_LOADER, packageName)).defineClass(bytes);
                setBoot(false);
                return klass;
            }

            return Objects.requireNonNull(stubGenerator.generateStub(classLoader, packageName)).defineClass(bytes);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public MethodHandles.Lookup defineHiddenClass(byte[] bytes, boolean initialize, ClassLoader classLoader, MethodHandles.Lookup.ClassOption... options) {
        return defineHiddenClass(bytes, initialize, classLoader, readPackageName(bytes), options);
    }

    @Override
    public MethodHandles.Lookup defineHiddenClass(byte[] bytes, boolean initialize, ClassLoader classLoader, String packageName, MethodHandles.Lookup.ClassOption... options) {
        try {
            if (classLoader == null) {
                setBoot(true);
                MethodHandles.Lookup lookup = Objects.requireNonNull(stubGenerator.generateStub(BOOT_CLASS_LOADER, packageName)).defineHiddenClass(bytes, initialize, options);
                setBoot(false);
                return lookup;
            }

            return Objects.requireNonNull(stubGenerator.generateStub(classLoader, packageName)).defineHiddenClass(bytes, initialize, options);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void setBoot(boolean isBoot) {
        stubGenerator.isBoot = isBoot;
    }

    private static String readClassName(byte[] bytes) {
        return new ClassReader(bytes).getClassName().replace("/", ".");
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

        private static final Map<StubClassData, MethodHandles.Lookup> DEFINED_STUB_CACHE = new ConcurrentHashMap<>();
        private static final Map<String, Map.Entry<String, byte[]>> EMPTY_CLASS_BYTES_CACHE = new ConcurrentHashMap<>();
        private final MethodHandle DEFINE_CLASS_HANDLE;
        private final MethodHandle DEFINE_CLASS_BOOT_HANDLE;
        private boolean isBoot;

        public StubGenerator(boolean isBoot) {
            try {
                Deencapsulation.deencapsulate(StubGenerator.class);
            } catch (Throwable ignored) {}

            this.isBoot = isBoot;

            MethodHandle defineClassMH = null;
            MethodHandle defineClassBootMH = null;

            try {
                Method method = ClassLoader.class.getDeclaredMethod("defineClass", byte[].class, int.class, int.class);
                method.setAccessible(true);

                defineClassMH = SafeClassDefiner.this.lookup.unreflect(method);

                method = ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class);
                method.setAccessible(true);

                defineClassBootMH = SafeClassDefiner.this.lookup.unreflect(method);
            } catch (Throwable t) {
                t.printStackTrace();
            }

            DEFINE_CLASS_HANDLE = defineClassMH;
            DEFINE_CLASS_BOOT_HANDLE = defineClassBootMH;
        }

        public MethodHandles.Lookup generateStub(ClassLoader classLoader, String packageName) {
            StubClassData classData = new StubClassData(packageName, classLoader);
            MethodHandles.Lookup cachedLookup = DEFINED_STUB_CACHE.get(classData);

            if (cachedLookup != null)
                return cachedLookup;

            Map.Entry<String, byte[]> entry = emptyClassBytes(packageName, isBoot);
            byte[] bytes = entry.getValue();

            try {
                if (isBoot) {
                    MethodHandles.Lookup lookup = SafeClassDefiner.this.lookup.in((Class<?>) DEFINE_CLASS_BOOT_HANDLE.invoke(classLoader, entry.getKey(), bytes, 0, bytes.length));
                    DEFINED_STUB_CACHE.put(classData, lookup);
                    return lookup;
                } else {
                    MethodHandles.Lookup lookup = SafeClassDefiner.this.lookup.in((Class<?>) DEFINE_CLASS_HANDLE.invoke(classLoader, bytes, 0, bytes.length));
                    DEFINED_STUB_CACHE.put(classData, lookup);
                    return lookup;
                }
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
