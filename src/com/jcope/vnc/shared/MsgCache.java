package com.jcope.vnc.shared;

import java.io.BufferedOutputStream;
import java.util.WeakHashMap;
import java.util.concurrent.Semaphore;

import com.jcope.util.BufferPool;
import com.jcope.util.ReusableByteArrayOutputStream;

public class MsgCache
{
    protected static WeakHashMap<BufferedOutputStream, ReusableByteArrayOutputStream> compressionCache = new WeakHashMap<BufferedOutputStream, ReusableByteArrayOutputStream>(1);
    protected static WeakHashMap<BufferedOutputStream, BufferPool<byte[]>> compressionResultCache = new WeakHashMap<BufferedOutputStream, BufferPool<byte[]>>(1);
    protected static volatile ReusableByteArrayOutputStream precompRBOS = null;
    protected static final Semaphore bufferPoolLock = new Semaphore(1, true);
    public static volatile BufferPool<byte[]> bufferPool = null;
    protected static final Semaphore precompSema = new Semaphore(1, true);
}
