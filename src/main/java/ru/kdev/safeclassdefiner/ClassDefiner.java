package ru.kdev.safeclassdefiner;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Provides methods for define new classes.
 */
public interface ClassDefiner {

    /**
     * Creates instance of {@link SafeClassDefiner}.
     * @return new instance of {@link SafeClassDefiner}
     */
    static ClassDefiner createSafeClassDefiner() {
        return new SafeClassDefiner();
    }

    /**
     * Creates instance of {@link SafeClassDefiner} without trusted lookup.
     * Do not use deencapsulation.
     * @param lookup - lookup for use instead trusted lookup
     * @return new instance of {@link SafeClassDefiner}
     */
    static ClassDefiner createUnprivilegedSafeClassDefiner(MethodHandles.Lookup lookup) {
        return new SafeClassDefiner(lookup);
    }

    /**
     * Makes empty class bytes for tests.
     * @return bytecode of empty class
     * */
    static byte[] makeEmptyClassBytes(String packageName, String className) {
        return SafeClassDefiner.StubGenerator.emptyClassBytes(packageName, className);
    }

    /**
     * Define class in specified {@link ClassLoader}.
     * Automatically detects package name.
     * @since 9
     * @param bytes - bytecode of class
     * @param classLoader - class loader
     * @return defined class
     */
    Class<?> defineClass(byte[] bytes, ClassLoader classLoader);

    /**
     * Defines class in specified {@link ClassLoader} with specified packageName.
     * @since 9
     * @param bytes - bytecode of class
     * @param classLoader - class loader
     * @param packageName - package name of new class
     * @return defined class
     */
    Class<?> defineClass(byte[] bytes, ClassLoader classLoader, String packageName);

    /**
     * Defines hidden class in specified {@link ClassLoader}.
     * Automatically detects package name.
     * @since 15
     * @param bytes - bytecode of class
     * @param initialize - initialize class or not
     * @param classLoader - class loader
     * @param options - class options
     * @return defined hidden class
     */
    MethodHandles.Lookup defineHiddenClass(byte[] bytes, boolean initialize, ClassLoader classLoader, MethodHandles.Lookup.ClassOption... options);

    /**
     * Defines hidden class in specified {@link ClassLoader} with specified package name.
     * @since 15
     * @param bytes - bytecode of class
     * @param initialize - initialize class or not
     * @param classLoader - class loader
     * @param packageName - package name of new class
     * @param options - class options
     * @return defined hidden class
     */
    MethodHandles.Lookup defineHiddenClass(byte[] bytes, boolean initialize, ClassLoader classLoader, String packageName, MethodHandles.Lookup.ClassOption... options);
}
