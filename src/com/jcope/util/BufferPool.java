//EXPERIMENTAL; NOT_TESTED; NOT_USED

package com.jcope.util;

import static com.jcope.debug.Debug.assert_;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;

import com.jcope.debug.LLog;

public abstract class BufferPool<T>
{
    private final ArrayList<PoolRef> poolList;
    private volatile int size;
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
                            synchronized(pool.poolList)
                            {
                                do
                                {
                                    ref.parentPool = null;
                                    ref.sema = null;
                                    ref.hardRef = null;
                                    if (ref.idx >= 0)
                                    {
                                        pool.remove(ref);
                                    }
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
    
    public class PoolRef extends WeakReference<T>
    {
        //private volatile boolean isOwned;
        private volatile int refCount;
        private Semaphore sema;
        private volatile T hardRef;
        private volatile int idx;
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
            idx = -1;
            refCount = 1;
            //isOwned = Boolean.TRUE;
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
        size = 0;
        poolList = new ArrayList<PoolRef>();
        listSema = new Semaphore(1, true);
    }
    
    private void set(final int idx, final PoolRef ref)
    {
        ref.idx = idx;
        if (idx == poolList.size())
        {
            poolList.add(ref);
        }
        else
        {
            poolList.set(idx, ref);
        }
    }
    
    private void percolateUp(int idx)
    {
        final PoolRef nRef = poolList.get(idx);
        final int order = nRef.order;
        PoolRef ref;
        int nextIdx;
        boolean somethingMoved = Boolean.FALSE;
        
        while (idx > 0)
        {
            nextIdx = (idx-1)/2;
            if ((ref = poolList.get(nextIdx)).order <= order)
            {
                break;
            }
            set(idx, ref);
            somethingMoved = Boolean.TRUE;
            idx = nextIdx;
        }
        
        if (somethingMoved)
        {
            set(idx, nRef);
        }
    }
    
    private void remove(PoolRef ref)
    {
        int idx = ref.idx;
        
        if (idx < 0)
        {
            return;
        }
        
        //assert_(!ref.isOwned);
        
        int left, right;
        boolean uleft = Boolean.FALSE;
        boolean uright = Boolean.FALSE;
        
        final int newSize = size-1;
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
                set(idx, poolList.get(left));
                idx = left;
            }
            else if (uright)
            {
                uright = Boolean.FALSE;
                set(idx, poolList.get(right));
                idx = right;
            }
            else
            {
                assert_(idx <= newSize);
                if (idx != newSize)
                {
                    set(idx, poolList.get(newSize));
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
            /*
            if (left != null)
            {
                assert_(left.idx == leftIdx);
            }
            if (right != null)
            {
                assert_(right.idx == rightIdx);
            }
            */
            if (left != null && left.order > order)
            {
                left = null;
            }
            if (right != null && right.order > order)
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
            //assert_(startRef[1] == this.size);
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
        
        //rval.isOwned = Boolean.TRUE;
        
        return rval;
    }
    
    private void release(final PoolRef ref)
    {
        int newSize;
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
            //assert_(ref.isOwned);
            assert_(ref.idx < 0);
            newSize = size;
            ref.hardRef = null;
            synchronized(poolList)
            {
                set(newSize, ref);
                percolateUp(newSize);
            }
            size = newSize+1;
            //ref.isOwned = Boolean.FALSE;
        }
        finally {
            listSema.release();
        }
    }
    
    protected abstract T getInstance(int order);
    
}
