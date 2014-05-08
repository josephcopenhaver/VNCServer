package com.jcope.util;

import java.util.BitSet;

public class FixedLengthBitSet
{
    public final int length;
    private BitSet bset;
    
    public FixedLengthBitSet(int length)
    {
        this(length, Boolean.FALSE);
    }
    
    public FixedLengthBitSet(int length, boolean initBitOn)
    {
        this.length = length;
        bset = new BitSet(length);
        
        if (initBitOn)
        {
            fill(initBitOn);
        }
    }
    
    private final void checkIdx(int idx)
    {
        if (idx < 0 || idx >= length)
        {
            throw new IllegalArgumentException();
        }
    }
    
    public void set(int idx, boolean bitOn)
    {
        checkIdx(idx);
        bset.set(idx, bitOn);
    }
    
    public boolean get(int idx)
    {
        checkIdx(idx);
        return bset.get(idx);
    }
    
    public void fill(boolean bitOn)
    {
       bset.set(0, length, bitOn);
    }
    
    public int nextSetBit(int fromIndex)
    {
        return bset.nextSetBit(fromIndex);
    }
    
}
