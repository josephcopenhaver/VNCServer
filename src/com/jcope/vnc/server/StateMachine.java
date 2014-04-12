package com.jcope.vnc.server;

import java.util.ArrayList;

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
	
    public static void handleServerEvent(ArrayList<ClientHandler> clients, byte[] preCmpressed, SERVER_EVENT event)
    {
        for (ClientHandler client : clients)
        {
            client.sendPreCompressed(event, preCmpressed);
        }
    }
    
    public static void handleServerEvent(ArrayList<ClientHandler> clients, SERVER_EVENT event)
    {
        handleServerEvent(clients, event, (Object[]) null);
    }
    
    /*
    public static class JitCompressedEvent
    {
        public JitCompressedEvent()
        {
            
        }
    }
    */
    
    public static void handleServerEvent(ArrayList<ClientHandler> clients, SERVER_EVENT event, Object... args)
    {
        if (clients.size() > 1)
        {
            // TODO: compress only just before serialization and sending
            // save off the compressed reference relative to this object
            // let containers be garbage collected 
            byte[] preCmpressed = Msg.getCompressed(event, args);
            for (ClientHandler client : clients)
            {
                client.sendPreCompressed(event, preCmpressed);
            }
        }
        else
        {
            for (ClientHandler client : clients) // just in case something shift
            {
                handleServerEvent(client, event, args);
            }
        }
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
