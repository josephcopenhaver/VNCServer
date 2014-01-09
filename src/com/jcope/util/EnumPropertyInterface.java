package com.jcope.util;

import java.util.Properties;

public interface EnumPropertyInterface
{
    public abstract void assertType(Object obj);
    public abstract Object getValue();
    public abstract void setValue(Object value);
    public abstract void load(Properties prop);
}
