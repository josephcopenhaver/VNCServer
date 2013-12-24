package com.jcope.vnc.client.input;

import static com.jcope.debug.Debug.assert_;

import java.util.HashMap;
import java.util.concurrent.Semaphore;

import com.jcope.debug.LLog;
import com.jcope.vnc.client.StateMachine;
import com.jcope.vnc.shared.StateMachine.SERVER_EVENT;
import com.jcope.vnc.shared.input.AbstractHandler;
import com.jcope.vnc.shared.input.Handle;

@SuppressWarnings("unchecked")
public class Handler extends AbstractHandler<StateMachine, SERVER_EVENT>
{
    
    private static final HashMap<SERVER_EVENT,Handle<StateMachine>> eventHandles;
    private static final Semaphore instanceSema = new Semaphore(1, true);
    private static final Handler[] selfRef = new Handler[]{null};
    
    static
    {
        String thisClassName = Handler.class.getName();
        int basenameSep = thisClassName.lastIndexOf('.');
        String thisClassDir = thisClassName.substring(0, basenameSep);
        eventHandles = new HashMap<SERVER_EVENT,Handle<StateMachine>>();
        for (SERVER_EVENT event : SERVER_EVENT.values())
        {
            try
            {
                String eventCodeName = event.name();
                String eventName = formatNameCamelCase(eventCodeName);
                String className = String.format("%s.handle.%s", thisClassDir, eventName);
                Class<?> c = Class.forName(className);
                Object handle = c.newInstance();
                assert_(handle instanceof Handle);
                assert_(((Handle<?>)handle).getType() == StateMachine.class);
                eventHandles.put(event, (Handle<StateMachine>) handle); 
            }
            catch (ClassNotFoundException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException e)
            {
                LLog.e(e);
            }
        }
    }
    
    private Handler()
    {
        // Do nothing
    }
    
    public static Handler getInstance()
    {
        Handler rval = selfRef[0];
        
        if (rval == null)
        {
            try
            {
                instanceSema.acquire();
            }
            catch (InterruptedException e)
            {
                LLog.e(e);
            }
            try
            {
                synchronized(selfRef)
                {
                    rval = selfRef[0];
                    if (rval == null)
                    {
                        rval = new Handler();
                        selfRef[0] = rval;
                    }
                }
            }
            finally {
                instanceSema.release();
            }
        }
        
        return rval;
    }

    @Override
    public void handle(StateMachine stateMachine, SERVER_EVENT event, Object... args)
    {
        Handle<StateMachine> handle = eventHandles.get(event);
        handle.handle(stateMachine, args);
    }
}
