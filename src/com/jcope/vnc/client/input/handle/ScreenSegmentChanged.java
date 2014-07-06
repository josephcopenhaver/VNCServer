package com.jcope.vnc.client.input.handle;

import static com.jcope.debug.Debug.assert_;

import java.util.concurrent.Semaphore;

import com.jcope.debug.LLog;
import com.jcope.util.TaskDispatcher;
import com.jcope.vnc.client.StateMachine;
import com.jcope.vnc.shared.StateMachine.CLIENT_EVENT;
import com.jcope.vnc.shared.StateMachine.SERVER_EVENT;
import com.jcope.vnc.shared.input.Handle;

public class ScreenSegmentChanged extends Handle<StateMachine>
{
    public static final TaskDispatcher<Integer> segmentFetcher = new TaskDispatcher<Integer>("ScreenSegmentChanged.segmentFetcher");
    volatile Semaphore iconifiedSema = null;
    
    public ScreenSegmentChanged()
    {
        super(StateMachine.class);
    }
    
    @Override
    public void handle(final StateMachine stateMachine, Object[] args)
    {
        assert_(args != null);
        assert_(args.length == 1);
        assert_(args[0] instanceof Integer);
        final int segmentID = (Integer) args[0];
        assert_(segmentID >= -1);
        
        Semaphore iconifiedSema = this.iconifiedSema;
        
        if (iconifiedSema == null)
        {
            iconifiedSema = stateMachine.getIconifiedSemaphore();
            this.iconifiedSema = iconifiedSema;
        }
        
        final Semaphore f_iconifiedSema = iconifiedSema;
        
        if (!segmentFetcher.queueContains(segmentID))
        {
            segmentFetcher.dispatch(segmentID, new Runnable() {
                
                @Override
                public void run()
                {
                    try
                    {
                        f_iconifiedSema.acquire();
                    }
                    catch (InterruptedException e)
                    {
                        LLog.e(e);
                    }
                    try {
                        stateMachine.sendEvent(CLIENT_EVENT.GET_SCREEN_SEGMENT, segmentID);
                    }
                    finally {
                        f_iconifiedSema.release();
                    }
                }
            });
        }
        
        stateMachine.sendEvent(CLIENT_EVENT.ACKNOWLEDGE_NON_SERIAL_EVENT, SERVER_EVENT.SCREEN_SEGMENT_CHANGED, segmentID);
    }
}
