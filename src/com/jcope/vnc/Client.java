package com.jcope.vnc;

import java.io.IOException;
import java.net.UnknownHostException;

import static com.jcope.debug.Debug.DEBUG;
import com.jcope.vnc.client.StateMachine;

/**
 * 
 * @author Joseph Copenhaver
 *
 * 
 * This class shall be the main execution point of a client instance.
 * 
 */

public class Client
{
	public static void main(String[] args) throws UnknownHostException, IOException, InterruptedException
	{
		boolean forceStop = !DEBUG;
		String targetHost = "localhost";
		int targetPort = 1979;
		
		try
		{
			StateMachine myClient = new StateMachine(targetHost, targetPort);
			System.out.println("Client is running!");
			myClient.run();
			forceStop = Boolean.TRUE;
		}
		finally {
			System.out.println("Client closed");
			if (forceStop)
			{
				System.exit(0);
			}
		}
	}
}
