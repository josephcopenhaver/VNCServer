package com.jcope.util;

import java.util.Properties;

public interface TypeSafeEnumPropertyPattern {
    public abstract void assertType(Object obj);

    public abstract Object getValue();

    public abstract void setValue(Object value);

    public abstract void load(Properties prop);

    /*
     * public static enum MY_CLASS_BASED_PROPERTY_SET implements
     * TypeSafeEnumPropertyPattern { MY_ENUM_PROP_1, MY_ENUM_PROP_2, ...
     * 
     * 
     * ;
     * 
     * 
     * Object value;
     * 
     * MY_CLASS_BASED_PROPERTY_SET(final Object defaultValue) { this.value =
     * defaultValue; }
     * 
     * public void assertType(Object obj) { assert(obj != null); switch (this) {
     * case MY_ENUM_PROP_1: assert(obj instanceof <CLASS_TYPE>); break; case
     * MY_ENUM_PROP_2: assert(obj instanceof <CLASS_TYPE>); break; ... } }
     * 
     * public Object getValue() { return value; }
     * 
     * public void setValue(Object value) { switch (this) { case MY_ENUM_PROP_1:
     * <TYPE_CONVERSION_RULE_1> break; case MY_ENUM_PROP_2:
     * <TYPE_CONVERSION_RULE_2> break; ... } assertType(value); this.value =
     * value; }
     * 
     * public void load(Properties prop) { Object value = prop.get(name()); if
     * (value != null) { setValue(value); } } }
     */
}
