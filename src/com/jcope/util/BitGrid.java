package com.jcope.util;

import java.util.Arrays;

public class BitGrid
{
    private static final byte[] offMasks = new byte[]{
        ((byte) (~1)),
        ((byte) (~(1 << 1))),
        ((byte) (~(1 << 2))),
        ((byte) (~(1 << 3))),
        ((byte) (~(1 << 4))),
        ((byte) (~(1 << 5))),
        ((byte) (~(1 << 6))),
        ((byte) (~(1 << 7)))
    };
    
    private byte[] grid;
    public final int length;
    
    public BitGrid(int length)
    {
        this(length, Boolean.FALSE);
    }
    
    public BitGrid(int length, boolean initBitOn)
    {
        this.length = length;
        grid = new byte[(length + 7) >> 3];
        
        if (initBitOn)
        {
            fill(initBitOn);
        }
    }
    
    public void set(int idx, boolean bitOn)
    {
        if (bitOn)
        {
            grid[idx >> 3] |=  (1 << (idx & 7));
        }
        else
        {
            grid[idx >> 3] &= offMasks[idx & 7];
        }
    }
    
    public void setByte(int idx, byte b)
    {
        grid[idx] = b;
    }
    
    public boolean get(int idx)
    {
        return (grid[idx >> 3] & (1 << (idx & 7))) != 0;
    }
    
    public byte getByte(int idx)
    {
        return grid[idx];
    }
    
    public void fill(boolean bitOn)
    {
       fill((byte) (bitOn ? 0xff : 0));
    }
    
    public void fill(byte b)
    {
        Arrays.fill(grid, b);
    }
    
}
