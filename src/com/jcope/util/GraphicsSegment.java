package com.jcope.util;

import java.util.concurrent.Semaphore;

import com.jcope.debug.LLog;
import com.jcope.vnc.server.JitCompressedEvent;
import com.jcope.vnc.shared.StateMachine.SERVER_EVENT;

public class GraphicsSegment
{
    public static interface Synchronously {
        public Object run(GraphicsSegment receiver, int[] pixels, Integer[] solidColorPtr);
    }
    
    private Semaphore sema = new Semaphore(1, true);
    private Integer[] solidColorPtr = new Integer[]{null};
    private int[] pixels;
    private volatile JitCompressedEvent jce = null;
    
    public GraphicsSegment(int size)
    {
        this(new int[size]);
    }
    
    public GraphicsSegment(int[] pixels)
    {
        this.pixels = pixels;
    }
    
    public Object synchronously(Synchronously runnable)
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
            synchronized(pixels){synchronized(solidColorPtr){
                return runnable.run(this, pixels, solidColorPtr);
            }}
        }
        finally {
            sema.release();
        }
    }
    
    public void releaseJitCompressedEvent()
    {
    	JitCompressedEvent my_jce = jce;
    	if (my_jce != null)
    	{
    		my_jce.release();
    	}
    	jce = null;
    }
    
    public JitCompressedEvent acquireJitCompressedEvent(Object id, Object serialized)
    {
    	JitCompressedEvent rval = jce;
    	
    	if (rval == null)
    	{
    		rval = JitCompressedEvent.getInstance(SERVER_EVENT.SCREEN_SEGMENT_UPDATE, new Object[]{id, serialized});
    		jce = rval;
    	}
    	rval.acquire();
    	
    	return rval;
    }
}
