package com.jcope.vnc.server.input;

import static com.jcope.debug.Debug.assert_;

import java.util.HashMap;
import java.util.concurrent.Semaphore;

import com.jcope.debug.LLog;
import com.jcope.vnc.server.ClientHandler;
import com.jcope.vnc.shared.StateMachine.CLIENT_EVENT;
import com.jcope.vnc.shared.input.AbstractHandler;
import com.jcope.vnc.shared.input.Handle;

@SuppressWarnings("unchecked")
public class Handler extends AbstractHandler<ClientHandler, CLIENT_EVENT>
{
    
    private static final HashMap<CLIENT_EVENT,Handle<ClientHandler>> eventHandles;
    private static final Semaphore instanceSema = new Semaphore(1, true);
    private static final Handler[] selfRef = new Handler[]{null};
    
    static
    {
        String thisClassName = Handler.class.getName();
        int basenameSep = thisClassName.lastIndexOf('.');
        String thisClassDir = thisClassName.substring(0, basenameSep);
        eventHandles = new HashMap<CLIENT_EVENT,Handle<ClientHandler>>();
        for (CLIENT_EVENT event : CLIENT_EVENT.values())
        {
            try
            {
                String eventCodeName = event.name();
                String eventName = formatNameCamelCase(eventCodeName);
                String className = String.format("%s.handle.%s", thisClassDir, eventName);
                Class<?> c = Class.forName(className);
                Object handle = c.newInstance();
                assert_(handle instanceof Handle);
                assert_(((Handle<?>)handle).getType() == ClientHandler.class);
                eventHandles.put(event, (Handle<ClientHandler>) handle); 
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
    public void handle(ClientHandler obj, CLIENT_EVENT event, Object... args)
    {
        Handle<ClientHandler> handle = eventHandles.get(event);
        handle.handle(obj, args);
    }
}
