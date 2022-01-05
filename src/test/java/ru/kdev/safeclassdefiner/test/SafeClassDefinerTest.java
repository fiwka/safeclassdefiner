package ru.kdev.safeclassdefiner.test;

import org.junit.jupiter.api.Test;
import ru.kdev.safeclassdefiner.ClassDefiner;

import static org.junit.jupiter.api.Assertions.*;

public class SafeClassDefinerTest {

    @Test
    public void safeClassDefinerDefineClassTestPlatformClassLoader() {
        ClassDefiner safeClassDefiner = ClassDefiner.createSafeClassDefiner();
        Class<?> test = safeClassDefiner.defineClass(
                ClassDefiner.makeEmptyClassBytes("test.package", "Test1"),
                ClassLoader.getPlatformClassLoader(),
                "test.package"
        );
        assertEquals("test.package", test.getPackageName());
        assertEquals(ClassLoader.getPlatformClassLoader(), test.getClassLoader());
    }

    @Test
    public void safeClassDefinerDefineClassTestMyClassLoader() {
        ClassDefiner safeClassDefiner = ClassDefiner.createSafeClassDefiner();
        ClassLoader classLoader = new MySuperMegaPrivateClassLoader();
        Class<?> test = safeClassDefiner.defineClass(
                ClassDefiner.makeEmptyClassBytes("test.package", "Test2"),
                classLoader,
                "test.package"
        );
        assertEquals("test.package", test.getPackageName());
        assertEquals(classLoader, test.getClassLoader());
    }

    @Test
    public void safeClassDefinerDefineClassTestPlatformClassLoaderAutoPackageReading() {
        ClassDefiner safeClassDefiner = ClassDefiner.createSafeClassDefiner();
        Class<?> test = safeClassDefiner.defineClass(
                ClassDefiner.makeEmptyClassBytes("test.package", "Test3"),
                ClassLoader.getPlatformClassLoader()
        );
        assertEquals("test.package", test.getPackageName());
        assertEquals(ClassLoader.getPlatformClassLoader(), test.getClassLoader());
    }

    @Test
    public void safeClassDefinerDefineClassTestMyClassLoaderAutoPackageReading() {
        ClassDefiner safeClassDefiner = ClassDefiner.createSafeClassDefiner();
        ClassLoader classLoader = new MySuperMegaPrivateClassLoader();
        Class<?> test = safeClassDefiner.defineClass(
                ClassDefiner.makeEmptyClassBytes("test.package", "Test4"),
                classLoader
        );
        assertEquals("test.package", test.getPackageName());
        assertEquals(classLoader, test.getClassLoader());
    }

    @Test
    public void safeClassDefinerDefineHiddenClassTestPlatformClassLoader() {
        ClassDefiner safeClassDefiner = ClassDefiner.createSafeClassDefiner();
        Class<?> test = safeClassDefiner.defineHiddenClass(
                ClassDefiner.makeEmptyClassBytes("test.package", "Test5"),
                true,
                ClassLoader.getPlatformClassLoader(),
                "test.package"
        ).lookupClass();
        assertEquals("test.package", test.getPackageName());
        assertEquals(ClassLoader.getPlatformClassLoader(), test.getClassLoader());
    }

    @Test
    public void safeClassDefinerDefineHiddenClassTestMyClassLoader() {
        ClassDefiner safeClassDefiner = ClassDefiner.createSafeClassDefiner();
        ClassLoader classLoader = new MySuperMegaPrivateClassLoader();
        Class<?> test = safeClassDefiner.defineHiddenClass(
                ClassDefiner.makeEmptyClassBytes("test.package", "Test6"),
                true,
                classLoader,
                "test.package"
        ).lookupClass();
        assertEquals("test.package", test.getPackageName());
        assertEquals(classLoader, test.getClassLoader());
    }

    @Test
    public void safeClassDefinerDefineHiddenClassTestPlatformClassLoaderAutoPackageReading() {
        ClassDefiner safeClassDefiner = ClassDefiner.createSafeClassDefiner();
        Class<?> test = safeClassDefiner.defineHiddenClass(
                ClassDefiner.makeEmptyClassBytes("test.package", "Test7"),
                true,
                ClassLoader.getPlatformClassLoader()
        ).lookupClass();
        assertEquals("test.package", test.getPackageName());
        assertEquals(ClassLoader.getPlatformClassLoader(), test.getClassLoader());
    }

    @Test
    public void safeClassDefinerDefineHiddenClassTestMyClassLoaderAutoPackageReading() {
        ClassDefiner safeClassDefiner = ClassDefiner.createSafeClassDefiner();
        ClassLoader classLoader = new MySuperMegaPrivateClassLoader();
        Class<?> test = safeClassDefiner.defineHiddenClass(
                ClassDefiner.makeEmptyClassBytes("test.package", "Test8"),
                true,
                classLoader
        ).lookupClass();
        assertEquals("test.package", test.getPackageName());
        assertEquals(classLoader, test.getClassLoader());
    }

    @Test
    public void safeClassDefinerDefineClassTestBootClassLoaderAutoPackageReading() {
        ClassDefiner safeClassDefiner = ClassDefiner.createSafeClassDefiner();
        Class<?> test = safeClassDefiner.defineClass(
                ClassDefiner.makeEmptyClassBytes("test.package", "Test9"),
                null
        );
        assertEquals("test.package", test.getPackageName());
        String classLoaderName = test.getClassLoader().toString().split("@")[0];
        assertEquals("jdk.internal.loader.ClassLoaders$BootClassLoader", classLoaderName);
    }

    private static class MySuperMegaPrivateClassLoader extends ClassLoader {}
}
