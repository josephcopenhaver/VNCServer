package com.jcope.vnc.server.input.handle;

import static com.jcope.debug.Debug.assert_;
import static com.jcope.vnc.shared.InputEventInfo.MAX_QUEUE_SIZE;

import java.util.concurrent.Semaphore;

import com.jcope.debug.LLog;
import com.jcope.util.TaskDispatcher;
import com.jcope.vnc.server.ClientHandler;
import com.jcope.vnc.server.DirectRobot;
import com.jcope.vnc.server.input.Handle;
import com.jcope.vnc.shared.InputEvent;
import com.jcope.vnc.shared.StateMachine.SERVER_EVENT;

public class OfferInput extends Handle
{
    private static Semaphore critSema = new Semaphore(1, true);
    private static volatile TaskDispatcher<Integer> userInputRelay = null;
    private static volatile int TID = -1;
    private static volatile int queueSize = 0;
    
    @Override
    public void handle(ClientHandler client, Object[] args)
    {
        assert_(args != null);
        assert_(args.length == 2);
        assert_(args[0] instanceof Boolean);
        
        boolean isNotification = (boolean) args[0];
        
        if (isNotification)
        {
            assert_(args[1] instanceof Integer);
            
            int numEventsQueued, numCanRead;
            numEventsQueued = (int) args[1];
            assert_(numEventsQueued > 0);
            numCanRead = MAX_QUEUE_SIZE - queueSize;
            
            client.sendEvent(SERVER_EVENT.READ_INPUT_EVENTS, numCanRead);
        }
        else
        {
            assert_(args[1] instanceof InputEvent[]);
            InputEvent[] events = (InputEvent[]) args[1];
            
            DirectRobot dirbot = client.getDirbot();
            
            if (dirbot != null)
            {
                for (InputEvent e : events)
                {
                    if (!queueEvent(dirbot, e))
                    {
                        break;
                    }
                }
            }
        }
    }
    
    private boolean queueEvent(final DirectRobot dirbot, final InputEvent event)
    {
        boolean rval = false;
        TaskDispatcher<Integer> dispatcher = userInputRelay;
        
        if (dispatcher == null)
        {
            try
            {
                critSema.acquire();
            }
            catch (InterruptedException e)
            {
                LLog.e(e);
            }
            
            try
            {
                if (userInputRelay == null)
                {
                    userInputRelay = new TaskDispatcher<Integer>("Input Dispatcher");
                }
            }
            finally {
                critSema.release();
            }
            
            dispatcher = userInputRelay;
        }
        
        try
        {
            critSema.acquire();
        }
        catch (InterruptedException e)
        {
            LLog.e(e);
        }
        try
        {
            if (queueSize < MAX_QUEUE_SIZE)
            {
                int tid;
                Runnable r;
                
                tid = TID;
                tid++;
                if (tid < 0)
                {
                    tid = 0;
                }
                TID = tid;
                
                r = new Runnable() {

                    @Override
                    public void run()
                    {
                        try
                        {
                            InputEvent.perform(dirbot, event);
                        }
                        finally {
                            queueSize--;
                        }
                    }
                    
                };
                
                dispatcher.dispatch(tid, r);
                queueSize++;
                rval = true;
            }
        }
        finally {
            critSema.release();
        }
        
        return rval;
    }
    
}
