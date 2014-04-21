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
    private final Semaphore listSema;
    
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
        private PoolRef(T hardRef, final int order)
        {
            super(hardRef, queue);
            sema = new Semaphore(1, true);
            this.order = order;
            this.hardRef = hardRef;
            idx = -1;
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
            if (newRefCount <= 0)
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
        listSema = new Semaphore(1, true);
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
                    listSema.acquire();
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
                    listSema.release();
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
        int idx, newSize, left, right;
        boolean uleft = Boolean.FALSE;
        boolean uright = Boolean.FALSE;
        
        idx = ref.idx;
        if (idx < 0)
        {
            return;
        }
        
        newSize = size-1;
        assert_(newSize >= 0);
        
        while (true)
        {
            right = (idx+1)<<1;
            left = right-1;
            
            if (left <= newSize)
            {
                if (right <= newSize)
                {
                    if (poolList.get(left).order >= poolList.get(right).order)
                    {
                        uright = Boolean.TRUE;
                    }
                    else
                    {
                        uleft = Boolean.TRUE;
                    }
                }
                else
                {
                    uleft = Boolean.TRUE;
                }
            }
            else if (right <= newSize)
            {
                uright = Boolean.TRUE;
            }
            
            if (uleft)
            {
                uleft = Boolean.FALSE;
                _set(idx, poolList.get(left));
                idx = left;
            }
            else if (uright)
            {
                uright = Boolean.FALSE;
                _set(idx, poolList.get(right));
                idx = right;
            }
            else
            {
                if (idx != newSize)
                {
                    percolateUp(idx);
                }
                poolList.set(newSize, null);
                break;
            }
        }
        size = newSize;
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
                listSema.acquire();
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
                listSema.release();
            }
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
        int size;
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
            listSema.release();
        }
    }
    
    protected abstract T getInstance(int order);
    
}
