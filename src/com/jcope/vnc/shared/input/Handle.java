package com.jcope.vnc.shared.input;

public abstract class Handle<T>
{
    
    private final Class<T> type;
    
    public Handle(Class<T> type)
    {
        this.type = type;
    }
    
    public abstract void handle(T obj, Object[] args);

    public final Class<T> getType()
    {
        return type;
    }
}
