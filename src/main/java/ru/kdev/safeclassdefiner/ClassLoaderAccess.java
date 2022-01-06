package ru.kdev.safeclassdefiner;

import java.security.ProtectionDomain;

public interface ClassLoaderAccess {

    Class<?> defineClass1(ClassLoader loader, String name, byte[] b, int off, int len,
                          ProtectionDomain pd, String source);

    Class<?> defineClass0(ClassLoader loader,
                          Class<?> lookup,
                          String name,
                          byte[] b, int off, int len,
                          ProtectionDomain pd,
                          boolean initialize,
                          int flags,
                          Object classData);
}
