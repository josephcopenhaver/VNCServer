package com.jcope.vnc.server;

import java.util.ArrayList;
import java.util.concurrent.Semaphore;

import com.jcope.debug.LLog;
import com.jcope.vnc.shared.ByteBufferPool;
import com.jcope.vnc.shared.Msg;
import com.jcope.vnc.shared.StateMachine.SERVER_EVENT;

public class JitCompressedEvent
{
    
    private static final Semaphore poolSyncLock = new Semaphore(1, true);
    private static final ArrayList<JitCompressedEvent> objPool = new  ArrayList<JitCompressedEvent>();
    
    private final Semaphore readSyncLock;
    private final Semaphore releaseSyncLock;
    
    // Must have a local refcount
    // using BufferPool ref's refCount is NOT an option
    // this counter is for the container (which serves git compressed instances)
    private volatile int refCount;
    
    private volatile ByteBufferPool.PoolRef ref;
    private volatile SERVER_EVENT event;
    private volatile Object[] args;
    
    private final Runnable onDestroy;
    
    private JitCompressedEvent()
    {
        readSyncLock = new Semaphore(1, true);
        releaseSyncLock = new Semaphore(1, true);
        reset();
        onDestroy = new Runnable() {

            @Override
            public void run()
            {
                release();
            }
            
        };
    }
    
    private void reset()
    {
        refCount = 1;
        ref = null;
        event = null;
        args = null;
    }
    
    public static JitCompressedEvent getInstance(SERVER_EVENT event, Object[] args)
    {
        JitCompressedEvent rval = null;
        try
        {
            poolSyncLock.acquire();
        }
        catch (InterruptedException e)
        {
            LLog.e(e);
        }
        
        try
        {
            synchronized(objPool) {
                if (!objPool.isEmpty())
                {
                    rval = objPool.remove(objPool.size()-1);
                }
            }
        }
        finally {
            poolSyncLock.release();
        }
        
        if (rval == null)
        {
            rval = new JitCompressedEvent();
        }
        
        rval.event = event;
        rval.args = args;
        
        return rval;
    }
    
    public void acquire()
    {
        refCount++;
    }
    
    public void release()
    {
        try
        {
            releaseSyncLock.acquire();
        }
        catch (InterruptedException e)
        {
            LLog.e(e);
        }
        
        try
        {
            if ((--refCount) <= 0)
            {
                if (ref != null)
                {
                    ref.release();
                }
                reset();
                try
                {
                    poolSyncLock.acquire();
                }
                catch (InterruptedException e)
                {
                    LLog.e(e);
                }
                try
                {
                    synchronized(objPool) {
                        objPool.add(this);
                    }
                }
                finally {
                    poolSyncLock.release();
                }
            }
        }
        finally {
            releaseSyncLock.release();
        }
    }

    public byte[] getCompressed()
    {
        if (ref == null)
        {
            try
            {
                readSyncLock.acquire();
            }
            catch (InterruptedException e)
            {
                LLog.e(e);
            }
            
            try
            {
                if (ref == null)
                {
                    ref = Msg.getCompressed(event, args);
                }
            }
            finally {
                readSyncLock.release();
            }
        }
        
        return ref.get();
    }
    
    public SERVER_EVENT getEvent()
    {
        return event;
    }

    public Runnable getOnDestroy()
    {
        return onDestroy;
    }
}
