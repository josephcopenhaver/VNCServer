package com.jcope.vnc.shared;

import java.io.ObjectOutputStream;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.WeakHashMap;
import java.util.concurrent.Semaphore;

import com.jcope.util.ReusableByteArrayOutputStream;

public class MsgCache
{
    protected static WeakHashMap<ObjectOutputStream, ReusableByteArrayOutputStream> compressionCache = new WeakHashMap<ObjectOutputStream, ReusableByteArrayOutputStream>(1);
    protected static WeakHashMap<ObjectOutputStream, HashMap<Integer,WeakReference<byte[]>>> compressionResultCache = new WeakHashMap<ObjectOutputStream, HashMap<Integer,WeakReference<byte[]>>>(1);
    protected static volatile ReusableByteArrayOutputStream precompRBOS = null;
    protected static Semaphore precompSema = new Semaphore(1, true);
}
