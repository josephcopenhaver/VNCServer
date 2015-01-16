package com.jcope.vnc.client.input.handle;

import static com.jcope.debug.Debug.assert_;

import java.util.concurrent.Semaphore;

import com.jcope.debug.LLog;
import com.jcope.util.FixedLengthBitSet;
import com.jcope.util.TaskDispatcher;
import com.jcope.vnc.Client.CLIENT_PROPERTIES;
import com.jcope.vnc.client.StateMachine;
import com.jcope.vnc.shared.StateMachine.CLIENT_EVENT;
import com.jcope.vnc.shared.StateMachine.SERVER_EVENT;
import com.jcope.vnc.shared.input.Handle;

public class ScreenSegmentChanged extends Handle<StateMachine>
{
    public static final TaskDispatcher<Integer> segmentFetcher = new TaskDispatcher<Integer>("ScreenSegmentChanged.segmentFetcher");
    private volatile Semaphore iconifiedSema = null;
    private volatile Long lastSleepTime = null;
    
    
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
        
        HANDLED:
        do
        {
        	try
            {
        		stateMachine.changedSegmentsSema.acquire();
            }
            catch (InterruptedException e)
            {
                LLog.e(e);
            }
            try
            {
                FixedLengthBitSet flbs = stateMachine.getChangedSegments();
                if (flbs != null)
                {
                    flbs.or(newFlbs);
                    break HANDLED;
                }
                stateMachine.setChangedSegments(newFlbs);
            }
            finally {
            	stateMachine.changedSegmentsSema.release();
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
                    try
                    {
                        f_iconifiedSema.acquire();
                    }
                    catch (InterruptedException e)
                    {
                        LLog.e(e);
                    }
                    f_iconifiedSema.release();
                    
                    
                    // make client obey it's own screen throttle configuration
                    
                	Long l_lastSleepTime = lastSleepTime;
                	long now = System.currentTimeMillis();
                	
                	if (l_lastSleepTime == null)
                	{
                		lastSleepTime = now;
                	}
                	else
                	{
                		long refreshMS = (Long) CLIENT_PROPERTIES.MONITOR_SCANNING_PERIOD.getValue();
                		refreshMS -= now - l_lastSleepTime;
                		if (refreshMS > 0)
                    	{
    	            		try
    	            		{
                        		Thread.sleep(refreshMS);
                        		lastSleepTime = now + refreshMS;
    	                	} catch (InterruptedException e) {
    							LLog.e(e, false);
    						}
    					}
                		else
                		{
                			lastSleepTime = now;
                		}
                	}
                	
                	try
                    {
                        stateMachine.processingFrameSema.acquire();
                    }
                    catch (InterruptedException e)
                    {
                        LLog.e(e);
                    }
                	
                	stateMachine.sendEvent(CLIENT_EVENT.GET_SCREEN_SEGMENT);
                }
            });
        } while (false);
        
        stateMachine.sendEvent(CLIENT_EVENT.ACKNOWLEDGE_NON_SERIAL_EVENT, SERVER_EVENT.SCREEN_SEGMENT_CHANGED);
    }
}
