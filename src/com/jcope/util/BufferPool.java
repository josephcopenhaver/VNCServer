package com.jcope.util;

import static com.jcope.debug.Debug.assert_;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.Semaphore;

import com.jcope.debug.LLog;

public abstract class BufferPool<T>
{
    private final HashMap<Integer, Object> poolMap;
    private final Semaphore listSema;
    
    @SuppressWarnings("rawtypes")
    private static final ReferenceQueue queue = new ReferenceQueue();
    private static final Thread cleanupThread;
    
    static
    {
        cleanupThread = new Thread() {
            @SuppressWarnings({
                    "rawtypes", "unchecked"
            })
            @Override
            public void run() {
                BufferPool pool;
                BufferPool.PoolRef ref = null;
                while (true)
                {
                    try
                    {
                        if (ref == null)
                        {
                            ref = (BufferPool.PoolRef) queue.remove();
                        }
                        pool = ref.parentPool;
                        assert_(pool != null);
                        try
                        {
                            pool.listSema.acquire();
                        }
                        catch (InterruptedException e)
                        {
                            LLog.e(e);
                        }
                        try
                        {
                            synchronized(pool.poolMap)
                            {
                                do
                                {
                                    ref.parentPool = null;
                                    ref.sema = null;
                                    ref.hardRef = null;
                                    pool.remove(ref);
                                } while ((ref = (BufferPool.PoolRef) queue.poll()) != null && pool == ref.parentPool);
                            }
                        }
                        finally {
                            pool.listSema.release();
                        }
                    }
                    catch (InterruptedException e)
                    {
                        LLog.e(e, Boolean.FALSE);
                    }
                }
            }
        };
        
        cleanupThread.setName("BufferPool-cleanup-thread");
        cleanupThread.setPriority(Thread.MAX_PRIORITY);
        cleanupThread.setDaemon(true);
        cleanupThread.start();
    }
    
    public class PoolRef extends SoftReference<T>
    {
        private volatile int refCount;
        private Semaphore sema;
        private volatile T hardRef;
        private final int order;
        BufferPool<T> parentPool;
        
        @SuppressWarnings("unchecked")
        private PoolRef(T hardRef, final int order)
        {
            super(hardRef, queue);
            parentPool = BufferPool.this;
            sema = new Semaphore(1, true);
            this.order = order;
            this.hardRef = hardRef;
            refCount = 1;
        }
        
        private void stageGet()
        {
            hardRef = super.get();
        }
        
        public T get()
        {
            return hardRef;
        }
        
        public void release()
        {
            int newRefCount;
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
                newRefCount = refCount;
                assert_(newRefCount > 0);
                newRefCount--;
                refCount = newRefCount;
            }
            finally {
                sema.release();
            }
            if (newRefCount <= 0)
            {
                assert_(newRefCount == 0);
                BufferPool.this.release(this);
            }
        }
        
        public void acquire()
        {
            int newRefCount;
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
                newRefCount = refCount;
                assert_(newRefCount > 0);
                newRefCount++;
                refCount = newRefCount;
            }
            finally {
                sema.release();
            }
        }
    }
    
    public BufferPool()
    {
        poolMap = new HashMap<Integer, Object>();
        listSema = new Semaphore(1, true);
    }
    
    @SuppressWarnings("unchecked")
    private void reclaim(final PoolRef ref)
    {
        Object poolSetObj = poolMap.get(ref.order);
        if (poolSetObj == null)
        {
            poolMap.put(ref.order, ref);
            return;
        }
        LinkedList<PoolRef> poolSet;
        if (poolSetObj instanceof LinkedList) {
            poolSet = (LinkedList<PoolRef>) poolSetObj;
            synchronized(poolSet) {
                poolSet.add(ref);
            }
            return;
        }
        poolSet = new LinkedList<PoolRef>();
        poolSet.add((BufferPool<T>.PoolRef) poolSetObj);
        poolSet.add(ref);
        poolMap.put(ref.order, poolSet);
    }
    
    private void remove(PoolRef ref)
    {
        Object poolSetObj = poolMap.get(ref.order);
        if (poolSetObj == null) {
        	// Do Nothing
        	return;
        }
        else if (poolSetObj instanceof LinkedList) {
            @SuppressWarnings("unchecked")
            LinkedList<PoolRef> poolSet = (LinkedList<PoolRef>) poolSetObj;
            synchronized(poolSet) {
                poolSet.remove(ref);
                if (poolSet.isEmpty()) {
                    poolMap.remove(ref.order);
                }
            }
        }
        else if (poolSetObj == ref) {
            poolMap.remove(ref.order);
        }
    }
    
    @SuppressWarnings("unchecked")
    public PoolRef acquire(final int order)
    {
        PoolRef rval = null;
        PoolRef tmp;
        
        try
        {
            listSema.acquire();
        }
        catch (InterruptedException e)
        {
            LLog.e(e);
        }
        try
        {
            synchronized(poolMap)
            {
            	Object poolSetObj = poolMap.get(order);
                if (poolSetObj != null) {
                    LinkedList<PoolRef> poolSet;
                    if (poolSetObj instanceof HashMap) {
                    	boolean poolIsEmpty;
                        poolSet = (LinkedList<BufferPool<T>.PoolRef>) poolSetObj;
                        synchronized(poolSet){
                            while (!(poolIsEmpty = poolSet.isEmpty()))
                            {
                                tmp = poolSet.remove();
                                tmp.stageGet();
                                if (tmp.hardRef != null) {
                                    rval = tmp;
                                    break;
                                }
                            }
                        }
                        if (poolIsEmpty) {
                        	poolMap.remove(order);
                        }
                    }
                    else {
                        poolMap.remove(order);
                        tmp = (BufferPool<T>.PoolRef) poolSetObj;
                        tmp.stageGet();
                        if (tmp.hardRef != null) {
                            rval = tmp;
                        }
                    }
                }
            }
        }
        finally {
            listSema.release();
        }
        
        if (rval == null)
        {
            T hardRef = getInstance(order);
            rval = new PoolRef(hardRef, order);
        }
        else
        {
            rval.refCount = 1;
        }
        
        return rval;
    }
    
    private void release(final PoolRef ref)
    {
        try
        {
            listSema.acquire();
        }
        catch (InterruptedException e)
        {
            LLog.e(e);
        }
        try
        {
            ref.hardRef = null;
            synchronized(poolMap)
            {
                reclaim(ref);
            }
        }
        finally {
            listSema.release();
        }
    }
    
    protected abstract T getInstance(int order);
    
}
