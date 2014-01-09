package com.jcope.vnc.server;

import static com.jcope.debug.Debug.DEBUG;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.jcope.debug.LLog;
import com.jcope.vnc.Server.SERVER_PROPERTIES;

public class VncServer implements Runnable
{
	
	private static final boolean _DEBUG = Boolean.TRUE || DEBUG;
	
	public static final SecurityPolicy securityPolicy = new SecurityPolicy();
	
	private InetAddress serverBindAddress;
	private int listenBacklog, serverPort;
	private ArrayList<ClientHandler> clientList = new ArrayList<ClientHandler>();
	
	ServerSocket serverSocket;
	
	public VncServer(int serverPort, int listenBacklog, String serverBindAddress) throws ParserConfigurationException, SAXException, IOException
	{
		if (serverBindAddress == null)
		{
			this.serverBindAddress = null;
		}
		else
		{
			this.serverBindAddress = InetAddress.getByName(serverBindAddress);
		}
		this.listenBacklog = listenBacklog;
		this.serverPort = serverPort;
		securityPolicy.clear();
	    File file = new File((String) SERVER_PROPERTIES.SERVER_SECURITY_POLICY.getValue());
	    securityPolicy.readPolicy(file);
	}
	
	public void run()
	{
		while (true)
		{
			try
			{
				if (serverBindAddress == null)
				{
					serverSocket = new ServerSocket(serverPort, listenBacklog);
				}
				else
				{
					serverSocket = new ServerSocket(serverPort, listenBacklog, serverBindAddress); 
				}
				if (_DEBUG) System.out.println("Waiting for connections!");
				while (true)
				{
					Socket socket = serverSocket.accept();
					if (_DEBUG) System.out.println("Got a new connection!");
					if (socket != null)
					{
						ClientHandler newClient = null;
						try
						{
							newClient = new ClientHandler(socket);
						}
						catch(Exception e2)
						{
							LLog.e(e2, false);
							try
							{
								socket.close();
							}
							catch(Exception e3)
							{
								LLog.e(e3, false);
							}
						}
						if (newClient != null)
						{
							final ClientHandler f_newClient = newClient;
							addClient(newClient);
							newClient.addOnDestroyAction(new Runnable(){
								public void run()
								{
									removeClient(f_newClient);
								}
							});
							newClient.start();
						}
					}
				}
			}
			catch(Exception e)
			{
				LLog.e(e, false);
			}
			try
			{
				Thread.sleep(1000);
			}
			catch (InterruptedException e)
			{
				LLog.e(e, false);
			}
		}
	}
	
	private Runnable addClientAction = new Runnable()
	{

		@Override
		public void run()
		{
			ClientHandler client = (ClientHandler) stagedArgs[0];
			clientList.add(client);
		}
		
	};
	
	private void addClient(ClientHandler newClient)
	{
		withLock(addClientAction, newClient);
	}
	
	private Runnable removeClientAction = new Runnable()
	{

		@Override
		public void run()
		{
			ClientHandler client = (ClientHandler) stagedArgs[0];
			clientList.remove(client);
		}
		
	};
	
	public void removeClient(ClientHandler client)
	{
		withLock(removeClientAction, client);
	}
	
	private Semaphore stageLock = new Semaphore(1, true);
	private volatile Object[] stagedArgs;
	public void withLock(Runnable r, Object... args)
	{
		try
		{
			stageLock.acquire();
		}
		catch (InterruptedException e)
		{
			LLog.e(e);
		}
		try
		{
			stagedArgs = args;
			synchronized(clientList)
			{
				r.run();
			}
		}
		finally {
			stagedArgs = null;
			stageLock.release();
		}
	}
}
