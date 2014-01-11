package com.jcope.util;

import java.io.Closeable;
import java.io.Flushable;
import java.io.OutputStream;
import java.util.ArrayList;

public class ReusableByteArrayOutputStream extends OutputStream implements Closeable, Flushable, AutoCloseable
{
    
    private ArrayList<Byte> buffer;
    
    public ReusableByteArrayOutputStream()
    {
        this(0);
    }
    
    public ReusableByteArrayOutputStream(int size)
    {
        buffer = new ArrayList<Byte>(size);
    }
    
    public int size()
    {
        return buffer.size();
    }
    
    public void reset()
    {
        buffer.clear();
    }
    
    @Override
    public void flush()
    {
        // Do Nothing
    }
    
    @Override
    public void close()
    {
        // Do Nothing
    }
    
    public byte[] toByteArray()
    {
        return toByteArray(null);
    }
    
    public byte[] toByteArray(byte[] b)
    {
        int bsize = buffer.size();
        
        if (b == null || b.length < bsize)
        {
            b = new byte[bsize];
        }
        
        for (int i=0; i<b.length; i++)
        {
            b[i] = buffer.get(i);
        }
        
        return b;
    }

    @Override
    public void write(int b)
    {
        buffer.add((byte) b);
    }
    
}
