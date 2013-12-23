package com.jcope.vnc.server;

import static com.jcope.debug.Debug.assert_;

import java.io.Serializable;
import java.util.concurrent.Semaphore;

import com.jcope.debug.LLog;
import com.jcope.util.TaskDispatcher;
import com.jcope.vnc.shared.Msg;
import com.jcope.vnc.shared.StateMachine.CLIENT_EVENT;
import com.jcope.vnc.shared.StateMachine.SERVER_EVENT;

/**
 * 
 * @author Joseph Copenhaver
 *
 * Handle input from the client and server
 * 
 */


public class StateMachine
{

    public static void handleClientInput(ClientHandler client, Object obj)
	{
		if (obj instanceof CLIENT_EVENT)
		{
			_handleClientInput(client, (CLIENT_EVENT) obj, null);
		}
		else
		{
			Msg msg = (Msg) obj;
			_handleClientInput(client, (CLIENT_EVENT) msg.event, msg.args);
		}
	}
    
    private static void _handleClientInput(ClientHandler client, CLIENT_EVENT event, Object[] args)
	{
		// TODO: 
		LLog.logEvent(String.format("Client \"%s\"", client.toString()), event, args);
		switch (event)
        {
            case GET_SCREEN_SEGMENT :
                assert_(args != null);
                assert_(args.length == 1);
                assert_(args[0] instanceof Integer);
                client.sendEvent(SERVER_EVENT.SCREEN_SEGMENT_UPDATE, args[0], client.getSegment((Integer) args[0]));
                break;
            default:
                break;
        }
	}
	
	// TODO: make thread safe!
	public static void handleServerEvent(ClientHandler client, SERVER_EVENT event, Object... args)
	{
		client.sendEvent(event, args);
	}
	
}
