package com.jcope.debug;

public class Debug {
    public static final boolean DEBUG = Boolean.TRUE;

    public static void assert_(boolean condition) {
        assert_(condition, null);
    }

    public static void assert_(boolean condition, String msg) {
        if (!condition) {
            AssertionError e;

            if (msg == null) {
                e = new AssertionError();
            } else {
                e = new AssertionError(msg);
            }

            throw e;
        }
    }

}
