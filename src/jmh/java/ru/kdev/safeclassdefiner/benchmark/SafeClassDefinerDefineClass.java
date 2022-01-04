package ru.kdev.safeclassdefiner.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import ru.kdev.safeclassdefiner.ClassDefiner;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3)
@Fork(value = 1, warmups = 1)
@Measurement(iterations = 100, time = 10, timeUnit = TimeUnit.MILLISECONDS)
public class SafeClassDefinerDefineClass {

    private static final ClassDefiner SAFE_CLASS_DEFINER = ClassDefiner.createSafeClassDefiner();

    {
        try {
            final sun.misc.Unsafe UNSAFE;
            {
                final Field theUnsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
                theUnsafeField.setAccessible(true);
                UNSAFE = (sun.misc.Unsafe) theUnsafeField.get(null);
            }

            MethodHandles.Lookup IMPL_LOOKUP;
            {
                final Field implLookupField = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
                IMPL_LOOKUP = (MethodHandles.Lookup) UNSAFE.getObject(UNSAFE.staticFieldBase(implLookupField), UNSAFE.staticFieldOffset(implLookupField));
            }
            {
                final MethodHandle implAddExports = IMPL_LOOKUP.findVirtual(
                        Module.class,
                        "implAddExports",
                        MethodType.methodType(void.class, String.class)
                );

                Module javaBase = Byte.class.getModule();

                implAddExports.invokeExact(javaBase, "jdk.internal.misc");
                implAddExports.invokeExact(javaBase, "jdk.internal.reflect");
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    static final jdk.internal.misc.Unsafe UNSAFE = jdk.internal.misc.Unsafe.getUnsafe();

    @Benchmark
    public void benchmarkDefineClass(Blackhole blackhole) throws Throwable {
        int benchmarkId = Math.abs(ThreadLocalRandom.current().nextInt());
        int testId = Math.abs(ThreadLocalRandom.current().nextInt());

        blackhole.consume(SAFE_CLASS_DEFINER.defineClass(
            ClassDefiner.makeEmptyClassBytes("benchmark" + benchmarkId, "Test" + testId),
            ClassLoader.getSystemClassLoader()
        ));
    }

    @Benchmark
    public void benchmarkUnsafeDefineClass(Blackhole blackhole) throws Throwable {
        int benchmarkId = Math.abs(ThreadLocalRandom.current().nextInt());
        int testId = Math.abs(ThreadLocalRandom.current().nextInt());
        String name = "benchmark" + benchmarkId + ".Test" + testId;
        byte[] bytes = ClassDefiner.makeEmptyClassBytes("benchmark" + benchmarkId, "Test" + testId);

        blackhole.consume(UNSAFE.defineClass(
                name,
                bytes,
                0,
                bytes.length,
                ClassLoader.getSystemClassLoader(),
                this.getClass().getProtectionDomain()
        ));
    }
}
