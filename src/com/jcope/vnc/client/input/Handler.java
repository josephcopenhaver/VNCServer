package com.jcope.vnc.client.input;

import static com.jcope.debug.Debug.assert_;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.Semaphore;

import com.jcope.debug.LLog;
import com.jcope.vnc.client.StateMachine;
import com.jcope.vnc.client.input.handle.AliasChanged;
import com.jcope.vnc.client.input.handle.AliasDisconnected;
import com.jcope.vnc.client.input.handle.AliasRegistered;
import com.jcope.vnc.client.input.handle.AliasUnregistered;
import com.jcope.vnc.client.input.handle.AuthorizationUpdate;
import com.jcope.vnc.client.input.handle.ChatMsgToAll;
import com.jcope.vnc.client.input.handle.ChatMsgToUser;
import com.jcope.vnc.client.input.handle.ClientAliasUpdate;
import com.jcope.vnc.client.input.handle.ClipboardChanged;
import com.jcope.vnc.client.input.handle.ConnectionClosed;
import com.jcope.vnc.client.input.handle.ConnectionEstablished;
import com.jcope.vnc.client.input.handle.CursorGone;
import com.jcope.vnc.client.input.handle.CursorMove;
import com.jcope.vnc.client.input.handle.FailedAuthorization;
import com.jcope.vnc.client.input.handle.GetClipboard;
import com.jcope.vnc.client.input.handle.NumScreensChanged;
import com.jcope.vnc.client.input.handle.ReadInputEvents;
import com.jcope.vnc.client.input.handle.ScreenGone;
import com.jcope.vnc.client.input.handle.ScreenResized;
import com.jcope.vnc.client.input.handle.ScreenSegmentChanged;
import com.jcope.vnc.client.input.handle.ScreenSegmentSizeUpdate;
import com.jcope.vnc.client.input.handle.ScreenSegmentUpdate;
import com.jcope.vnc.client.input.handle.SetClipboard;
import com.jcope.vnc.shared.StateMachine.CLIENT_EVENT;
import com.jcope.vnc.shared.StateMachine.SERVER_EVENT;
import com.jcope.vnc.shared.input.Handle;

@SuppressWarnings("unchecked")
public class Handler extends com.jcope.vnc.shared.input.Handler<StateMachine, SERVER_EVENT>
{
    
    private static final HashMap<SERVER_EVENT,Handle<StateMachine>> eventHandles;
    private static final Semaphore instanceSema = new Semaphore(1, true);
    private static final Handler[] selfRef = new Handler[]{null};
    
    // enumerating linkage here so top down compilations will include these classes
    private static Class<?>[] bootStraps = new Class<?>[]{
        AliasChanged.class,
        AliasDisconnected.class,
        AliasRegistered.class,
        AliasUnregistered.class,
        AuthorizationUpdate.class,
        ChatMsgToAll.class,
        ChatMsgToUser.class,
        ClientAliasUpdate.class,
        ClipboardChanged.class,
        ConnectionClosed.class,
        ConnectionEstablished.class,
        CursorGone.class,
        CursorMove.class,
        FailedAuthorization.class,
        GetClipboard.class,
        NumScreensChanged.class,
        ReadInputEvents.class,
        ScreenGone.class,
        ScreenResized.class,
        ScreenSegmentChanged.class,
        ScreenSegmentSizeUpdate.class,
        ScreenSegmentUpdate.class,
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
        eventHandles = new HashMap<SERVER_EVENT,Handle<StateMachine>>();
        for (SERVER_EVENT event : SERVER_EVENT.values())
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
                assert_(((Handle<?>)handle).getType() == StateMachine.class);
                eventHandles.put(event, (Handle<StateMachine>) handle); 
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
    public void handle(StateMachine stateMachine, SERVER_EVENT event, Object... args)
    {
        Handle<StateMachine> handle = eventHandles.get(event);
        if (!event.isSerial()
            && event != SERVER_EVENT.SCREEN_SEGMENT_UPDATE
            && event != SERVER_EVENT.SCREEN_SEGMENT_CHANGED // handle for this event performs send of NS response
            )
        {
            stateMachine.sendEvent(CLIENT_EVENT.ACKNOWLEDGE_NON_SERIAL_EVENT, event);
        }
        handle.handle(stateMachine, args);
    }
}
