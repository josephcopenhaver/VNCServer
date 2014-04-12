package com.jcope.vnc.shared;

import java.io.BufferedOutputStream;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.WeakHashMap;
import java.util.concurrent.Semaphore;

import com.jcope.util.ReusableByteArrayOutputStream;

public class MsgCache
{
    protected static WeakHashMap<BufferedOutputStream, ReusableByteArrayOutputStream> compressionCache = new WeakHashMap<BufferedOutputStream, ReusableByteArrayOutputStream>(1);
    protected static WeakHashMap<BufferedOutputStream, HashMap<Integer,WeakReference<byte[]>>> compressionResultCache = new WeakHashMap<BufferedOutputStream, HashMap<Integer,WeakReference<byte[]>>>(1);
    protected static volatile ReusableByteArrayOutputStream precompRBOS = null;
    protected static Semaphore precompSema = new Semaphore(1, true);
}
