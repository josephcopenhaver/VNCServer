package com.jcope.vnc.server;

import com.jcope.debug.LLog;
import com.jcope.vnc.server.input.Handler;
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
        obj = Msg.decompress(obj);
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
		LLog.logEvent(String.format("Client \"%s\"", client.toString()), event, args);
		Handler.getInstance().handle(client, event, args);
	}
	
	public static void handleServerEvent(ClientHandler client, SERVER_EVENT event)
    {
        client.sendEvent(event);
    }
    
	public static void handleServerEvent(ClientHandler client, SERVER_EVENT event, Object... args)
	{
		client.sendEvent(event, args);
	}
	
}
