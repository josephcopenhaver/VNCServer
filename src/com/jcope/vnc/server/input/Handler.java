package com.jcope.vnc.server.input;

import static com.jcope.debug.Debug.assert_;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.Semaphore;

import com.jcope.debug.LLog;
import com.jcope.vnc.server.ClientHandler;
import com.jcope.vnc.server.input.handle.AcknowledgeNonSerialEvent;
import com.jcope.vnc.server.input.handle.ClipboardChanged;
import com.jcope.vnc.server.input.handle.EnableAliasMonitor;
import com.jcope.vnc.server.input.handle.EnableConnectionMonitor;
import com.jcope.vnc.server.input.handle.GetClipboard;
import com.jcope.vnc.server.input.handle.GetScreenSegment;
import com.jcope.vnc.server.input.handle.OfferInput;
import com.jcope.vnc.server.input.handle.RequestAlias;
import com.jcope.vnc.server.input.handle.SelectScreen;
import com.jcope.vnc.server.input.handle.SendChatMsg;
import com.jcope.vnc.server.input.handle.SetClipboard;
import com.jcope.vnc.shared.StateMachine.CLIENT_EVENT;
import com.jcope.vnc.shared.input.Handle;

@SuppressWarnings("unchecked")
public class Handler extends com.jcope.vnc.shared.input.Handler<ClientHandler, CLIENT_EVENT>
{
    
    private static final HashMap<CLIENT_EVENT,Handle<ClientHandler>> eventHandles;
    private static final Semaphore instanceSema = new Semaphore(1, true);
    private static final Handler[] selfRef = new Handler[]{null};
    
    // enumerating linkage here so top down compilations will include these classes
    private static Class<?>[] bootStraps = new Class<?>[]{
        AcknowledgeNonSerialEvent.class,
        ClipboardChanged.class,
        EnableAliasMonitor.class,
        EnableConnectionMonitor.class,
        GetClipboard.class,
        GetScreenSegment.class,
        OfferInput.class,
        RequestAlias.class,
        SelectScreen.class,
        SendChatMsg.class,
        SetClipboard.class
    };
    
    static
    {
        ArrayList<Class<?>> bootStrapper;
        if (bootStraps != null)
        {
            try {
                bootStrapper = new ArrayList<Class<?>>(bootStraps.length);
                for (Class<?> clazz : bootStraps)
                {
                    bootStrapper.add(clazz);
                }
            }
            finally {
                bootStraps = null;
            }
        }
        else
        {
            bootStrapper = null;
        }
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
                if (bootStrapper != null)
                {    
                    if (bootStrapper.contains(c))
                    {
                        bootStrapper.remove(c);
                    }
                    else
                    {
                        LLog.e(new ClassNotFoundException(String.format("Class %s is not in the bootStraps set in %s", c.getSimpleName(), Handler.class.getName())), true, true);
                    }
                }
                Object handle = c.newInstance();
                assert_(handle instanceof Handle);
                assert_(((Handle<?>)handle).getType() == ClientHandler.class);
                eventHandles.put(event, (Handle<ClientHandler>) handle); 
            }
            catch (ClassNotFoundException e)
            {
                LLog.e(e);
            }
            catch (SecurityException e)
            {
                LLog.e(e);
            }
            catch (InstantiationException e)
            {
                LLog.e(e);
            }
            catch (IllegalAccessException e)
            {
                LLog.e(e);
            }
            catch (IllegalArgumentException e)
            {
                LLog.e(e);
            }
        }
        if (null != bootStrapper && !bootStrapper.isEmpty())
        {
            LLog.e(new ClassNotFoundException(String.format("Class %s in the bootStraps set was never instantiated in %s", bootStrapper.get(0).getSimpleName(), Handler.class.getName())), true, true);
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
