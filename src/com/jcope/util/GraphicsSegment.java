package com.jcope.util;

import java.util.concurrent.Semaphore;

import com.jcope.debug.LLog;

public class GraphicsSegment
{
    public static interface Synchronously {
        public Object run(int[] pixels, Integer[] solidColorPtr);
    }
    
    private Semaphore sema = new Semaphore(1, true);
    private Integer[] solidColorPtr = new Integer[]{null};
    private int[] pixels;
    
    public GraphicsSegment(int size)
    {
        this(new int[size]);
    }
    
    public GraphicsSegment(int[] pixels)
    {
        this.pixels = pixels;
    }
    
    public Object synchronously(Synchronously runnable)
    {
        try
        {
            sema.acquire();
        }
        catch (InterruptedException e)
        {
            LLog.e(e);
        }
        try
        {
            synchronized(pixels){synchronized(solidColorPtr){
                return runnable.run(pixels, solidColorPtr);
            }}
        }
        finally {
            sema.release();
        }
    }
}
