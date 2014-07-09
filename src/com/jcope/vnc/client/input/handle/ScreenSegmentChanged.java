package com.jcope.vnc.client.input.handle;

import static com.jcope.debug.Debug.assert_;

import java.util.concurrent.Semaphore;

import com.jcope.debug.LLog;
import com.jcope.util.FixedLengthBitSet;
import com.jcope.util.TaskDispatcher;
import com.jcope.vnc.client.StateMachine;
import com.jcope.vnc.shared.StateMachine.CLIENT_EVENT;
import com.jcope.vnc.shared.StateMachine.SERVER_EVENT;
import com.jcope.vnc.shared.input.Handle;

public class ScreenSegmentChanged extends Handle<StateMachine>
{
    private static volatile FixedLengthBitSet changedSegments = null;
    private static Semaphore changedSegmentsSema = new Semaphore(1, true);
    public static final TaskDispatcher<Integer> segmentFetcher = new TaskDispatcher<Integer>("ScreenSegmentChanged.segmentFetcher");
    private volatile Semaphore iconifiedSema = null;
    
    
    public ScreenSegmentChanged()
    {
        super(StateMachine.class);
    }
    
    @Override
    public void handle(final StateMachine stateMachine, Object[] args)
    {
        assert_(args != null);
        assert_(args.length == 1);
        assert_(args[0] instanceof FixedLengthBitSet);
        
        FixedLengthBitSet newFlbs = (FixedLengthBitSet) args[0];
        
        try
        {
            changedSegmentsSema.acquire();
        }
        catch (InterruptedException e)
        {
            LLog.e(e);
        }
        try
        {
            FixedLengthBitSet flbs = changedSegments;
            if (flbs != null)
            {
                flbs.or(newFlbs);
                return;
            }
            changedSegments = newFlbs;
        }
        finally {
            changedSegmentsSema.release();
        }
        
        Semaphore iconifiedSema = this.iconifiedSema;
        
        if (iconifiedSema == null)
        {
            iconifiedSema = stateMachine.getIconifiedSemaphore();
            this.iconifiedSema = iconifiedSema;
        }
        
        final Semaphore f_iconifiedSema = iconifiedSema;
        
        segmentFetcher.dispatch(1, new Runnable() {
            
            @Override
            public void run()
            {
                FixedLengthBitSet flbs;
                try
                {
                    f_iconifiedSema.acquire();
                }
                catch (InterruptedException e)
                {
                    LLog.e(e);
                }
                try {
                    try
                    {
                        changedSegmentsSema.acquire();
                    }
                    catch (InterruptedException e)
                    {
                        LLog.e(e);
                    }
                    try
                    {
                        flbs = changedSegments;
                        changedSegments = null;
                    }
                    finally {
                        changedSegmentsSema.release();
                    }
                }
                finally {
                    f_iconifiedSema.release();
                }
                stateMachine.sendEvent(CLIENT_EVENT.GET_SCREEN_SEGMENT, flbs);
            }
        });
        
        stateMachine.sendEvent(CLIENT_EVENT.ACKNOWLEDGE_NON_SERIAL_EVENT, SERVER_EVENT.SCREEN_SEGMENT_CHANGED);
    }
}
