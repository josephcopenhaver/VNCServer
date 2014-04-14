package com.jcope.vnc.server;

import static com.jcope.vnc.shared.MsgCache.bufferPool;

import java.util.ArrayList;
import java.util.concurrent.Semaphore;

import com.jcope.debug.LLog;
import com.jcope.vnc.shared.Msg;
import com.jcope.vnc.shared.StateMachine.SERVER_EVENT;

public class JitCompressedEvent
{
    
    private static final Semaphore poolSyncLock = new Semaphore(1, true);
    private static final ArrayList<JitCompressedEvent> objPool = new  ArrayList<JitCompressedEvent>();
    
    private final Semaphore readSyncLock;
    private final Semaphore releaseSyncLock;
    
    private volatile int refCount;
    
    private volatile byte[] bytes;
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
        bytes = null;
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
                if (bytes != null)
                {
                    bufferPool.add(bytes);
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
        if (bytes == null)
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
                if (bytes == null)
                {
                    bytes = Msg.getCompressed(event, args);
                }
            }
            finally {
                readSyncLock.release();
            }
        }
        
        return bytes;
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
