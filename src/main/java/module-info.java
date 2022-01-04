module safeclassdefiner.main {
    requires java.management;
    requires org.objectweb.asm;
    requires jdk.attach;
    requires java.instrument;
    exports ru.kdev.safeclassdefiner;
}