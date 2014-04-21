//EXPERIMENTAL; NOT_TESTED; NOT_USED

package com.jcope.util;

import static com.jcope.debug.Debug.assert_;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;

import com.jcope.debug.LLog;

public abstract class BufferPool<T> extends Thread
{
    private final ArrayList<PoolRef> poolList;
    private volatile int size;
    private final Semaphore sema;
    
    @SuppressWarnings("rawtypes")
    private final ReferenceQueue queue;
    
    private class PoolRef extends WeakReference<T>
    {
        private volatile int refCount;
        private Semaphore sema;
        private volatile T hardRef;
        private volatile int idx;
        private final int order;
        
        @SuppressWarnings("unchecked")
        private PoolRef(T thingToRef, final int order)
        {
            super(thingToRef, queue);
            this.order = order;
            hardRef = thingToRef;
            idx = -1;
            refCount = 1;
            sema = new Semaphore(1, true);
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
            int newRefCount = refCount;
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
                assert_(newRefCount > 0);
                newRefCount--;
                refCount = newRefCount;
            }
            finally {
                sema.release();
            }
            if (newRefCount == 0)
            {
                BufferPool.this.release(this);
            }
        }
        
        public void acquire()
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
                assert_(refCount > 0);
                refCount++;
            }
            finally {
                sema.release();
            }
        }
    }
    
    @SuppressWarnings("rawtypes")
    public BufferPool(String name)
    {
        size = 0;
        poolList = new ArrayList<PoolRef>();
        queue = new ReferenceQueue();
        sema = new Semaphore(1, true);
        if (name != null)
        {
            setName(name);
        }
        setPriority(Thread.MAX_PRIORITY);
        setDaemon(true);
        start();
    }
    
    @SuppressWarnings("unchecked")
    public void run()
    {
        while (true)
        {
            try
            {
                PoolRef ref = (PoolRef) queue.remove();
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
                    synchronized(poolList)
                    {
                        do
                        {
                            ref.sema = null;
                            ref.hardRef = null;
                            if (ref.idx >= 0)
                            {
                                remove(ref);
                            }
                        } while ((ref = (PoolRef) queue.poll()) != null);
                    }
                }
                finally {
                    sema.release();
                }
            }
            catch (InterruptedException e)
            {
                LLog.e(e);
            }
        }
    }
    
    private void _set(final int idx, final PoolRef ref)
    {
        ref.idx = idx;
        poolList.set(idx, ref);
    }
    
    private void percolateUp(int idx)
    {
        PoolRef ref;
        PoolRef nRef = poolList.get(idx);
        int nextIdx;
        int order = nRef.order;
        boolean somethingMoved = Boolean.FALSE;
        
        while (idx > 0)
        {
            nextIdx = (idx-1)/2;
            if ((ref = poolList.get(nextIdx)).order <= order)
            {
                break;
            }
            _set(idx, ref);
            somethingMoved = Boolean.TRUE;
            idx = nextIdx;
        }
        
        if (somethingMoved)
        {
            _set(idx, nRef);
        }
    }
    
    private void remove(PoolRef ref)
    {
        int idx, newSize, left, right, size;
        
        size = this.size;
        idx = ref.idx;
        if (idx < 0)
        {
            return;
        }
        newSize = size-1;
        assert_(newSize >= 0);
        while (true)
        {
            // TODO: something does not look right here
            right = (idx+1)<<1;
            left = idx-1;
            if (left >= size || right >= size)
            {
                if (idx != newSize && newSize > 0)
                {
                    _set(idx, poolList.get(newSize));
                    poolList.set(newSize, null);
                    percolateUp(idx);
                }
                else
                {
                    poolList.set(newSize, null);
                }
                break;
            }
            if (poolList.get(left).order >= poolList.get(right).order)
            {
                _set(idx, poolList.get(right));
                idx = right;
            }
            else
            {
                _set(idx, poolList.get(left));
                idx = left;
            }
        }
        this.size = newSize;
    }
    
    private PoolRef get(final int order, final int[] startRef)
    {
        PoolRef rval = null;
        PoolRef left;
        PoolRef right;
        int idx = startRef[0];
        int leftIdx, rightIdx;
        Integer useParent = null;
        
        final int size = startRef[1];
        
        if (size == 0 || size <= idx)
        {
            return null;
        }
        
        if (idx < 0)
        {
            idx = 0;
        }
        
        while ((rval = poolList.get(idx)).order != order)
        {
            rightIdx = (idx + 1)<<1;
            leftIdx = rightIdx - 1;
            left = leftIdx < size ? poolList.get(leftIdx) : null;
            right = rightIdx < size ? poolList.get(rightIdx) : null;
            if (left != null && left.order > order)
            {
                left = null;
            }
            if(right != null && right.order > order)
            {
                right = null;
            }
            if (left == null && right == null)
            {
                rval = null;
                break;
            }
            else if (left != null)
            {
                if (right != null)
                {
                    rval = (left.order < right.order) ? right : left;
                    useParent = (left.order == right.order) ? idx : null;
                }
                else
                {
                    rval = left;
                    useParent = null;
                }
            }
            else
            {
                rval = right;
                useParent = null;
            }
            idx = rval.idx;
        }
            
        if (rval != null)
        {
            rval.stageGet();
            startRef[1]--;
            remove(rval);
            rval.idx = -1;
            startRef[0] = (useParent == null) ? idx : useParent;
        }
        
        return rval;
    }
    
    public PoolRef acquire(final int order)
    {
        PoolRef rval = null;
        int[] startRef = new int[]{-1, 0};
        
        if (size > 0)
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
                if ((startRef[1] = size) > 0)
                {
                    synchronized(poolList)
                    {
                        do
                        {
                            rval = get(order, startRef);
                        } while (rval != null && rval.hardRef == null);
                    }
                }
            }
            finally {
                sema.release();
            }
        }
        
        if (rval == null)
        {
            rval = new PoolRef(getInstance(order), order);
        }
        
        return rval;
    }
    
    private void release(final PoolRef ref)
    {
        int size;
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
            size = this.size;
            ref.hardRef = null;
            synchronized(poolList)
            {
                _set(size, ref);
                percolateUp(size);
            }
            this.size = size+1;
        }
        finally {
            sema.release();
        }
    }
    
    protected abstract T getInstance(int order);
    
}
