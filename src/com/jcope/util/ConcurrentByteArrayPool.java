package com.jcope.util;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

import com.jcope.debug.LLog;

public class ConcurrentByteArrayPool
{
    private ConcurrentHashMap<Integer, ArrayList<WeakReference<byte[]>>> poolMap;
    private Semaphore sema = new Semaphore(1, true);
    
    public ConcurrentByteArrayPool()
    {
        poolMap = new ConcurrentHashMap<Integer, ArrayList<WeakReference<byte[]>>>(1);
    }
    
    public byte[] get(int size)
    {
        byte[] rval;
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
            ArrayList<WeakReference<byte[]>> pool = poolMap.get(size);
            do
            {
                if (pool == null)
                {
                    rval = new byte[size];
                    break;
                }
                synchronized(pool)
                {
                    int p = pool.size();
                    rval = null;
                    while (p>0)
                    {
                        p--;
                        rval = pool.remove(p).get();
                        if (rval != null)
                        {
                            break;
                        }
                    }
                    if (rval != null)
                    {
                        break;
                    }
                    rval = new byte[size];
                }
            } while (false);
            return rval;
        }
        finally {
            sema.release();
        }
    }
    
    public void add(byte[] unused)
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
            ArrayList<WeakReference<byte[]>> pool = poolMap.get(unused.length);
            if (pool == null)
            {
                pool = new ArrayList<WeakReference<byte[]>>(1);
                poolMap.put(unused.length, pool);
                pool.add(new WeakReference<byte[]>(unused));
            }
            else
            {
                synchronized(pool)
                {
                    pool.add(new WeakReference<byte[]>(unused));
                }
            }
        }
        finally {
            sema.release();
        }
    }
}
