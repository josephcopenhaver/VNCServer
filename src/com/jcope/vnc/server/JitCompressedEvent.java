package com.jcope.vnc.server;

import static com.jcope.vnc.shared.MsgCache.bufferPool;

import java.util.LinkedList;
import java.util.concurrent.Semaphore;

import com.jcope.debug.LLog;
import com.jcope.vnc.shared.Msg;
import com.jcope.vnc.shared.StateMachine.SERVER_EVENT;

public class JitCompressedEvent
{
    
    private static final LinkedList<JitCompressedEvent> objPool = new  LinkedList<JitCompressedEvent>();
    
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
        JitCompressedEvent rval = objPool.isEmpty() ? (new JitCompressedEvent()) : objPool.pop();
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
                synchronized(objPool) {
                    objPool.add(this);
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
