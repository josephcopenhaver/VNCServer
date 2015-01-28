package com.jcope.vnc.server;

import static com.jcope.debug.Debug.DEBUG;
import static com.jcope.debug.Debug.assert_;
import static com.jcope.util.Net.IPV_4_6_REGEX_STR;
import static com.jcope.util.Net.filterAddress;

import java.awt.datatransfer.Clipboard;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import sun.net.spi.nameservice.dns.DNSNameService;

import com.jcope.debug.LLog;
import com.jcope.util.ClipboardMonitor;
import com.jcope.util.ClipboardMonitor.ClipboardListener;
import com.jcope.vnc.Server;
import com.jcope.vnc.Server.SERVER_PROPERTIES;
import com.jcope.vnc.server.screen.Manager;
import com.jcope.vnc.shared.StateMachine.SERVER_EVENT;

public class VncServer implements Runnable
{
    public static final long DNS_QUERY_FAIL_DELAY_MS = 3000L;
	private static final boolean _DEBUG = Boolean.TRUE || DEBUG;
	
	public static final SecurityPolicy securityPolicy = new SecurityPolicy();
	
	private InetAddress serverBindAddress;
	private int listenBacklog, serverPort;
	private ArrayList<ClientHandler> clientList = new ArrayList<ClientHandler>();
	
	ServerSocket serverSocket;
	
	private Semaphore stageLock = new Semaphore(1, true);
    private volatile Object[] stagedArgs;
    
    public VncServer(int serverPort, int listenBacklog, String serverBindAddress, byte[] ipSpec, byte[] ipMask) throws ParserConfigurationException, SAXException, IOException
	{
		if (serverBindAddress == null)
		{
			this.serverBindAddress = null;
		}
		else
		{
		    this.serverBindAddress = null;
		    if (serverBindAddress.matches("^\\s+") || serverBindAddress.matches("\\s+$"))
		    {
		        serverBindAddress = serverBindAddress.trim();
		    }
			
			if (serverBindAddress.matches(IPV_4_6_REGEX_STR))
			{
			    this.serverBindAddress = InetAddress.getByName(serverBindAddress);
			}
			else
			{
			    if (serverBindAddress.equalsIgnoreCase("localhost"))
			    {
			        serverBindAddress = "127.0.0.1";
			    }
			    else
			    {
			        boolean isLoopbackPossible = false;
			        String oldServerBindAddress = serverBindAddress;
			        serverBindAddress = null;
			        do
			        {
    			        for (InetAddress addr : InetAddress.getAllByName(oldServerBindAddress))
    			        {
    			            String saddr = addr.getHostAddress();
    			            if (saddr == null)
    			            {
    			                continue;
    			            }
    			            if (saddr.equals("localhost") || saddr.equals("127.0.0.1"))
    			            {
    			                isLoopbackPossible = true;
    			            }
    			            else if (saddr.matches(IPV_4_6_REGEX_STR))
    			            {
    			                serverBindAddress = filterAddress(saddr, ipSpec, ipMask);
    			                if (serverBindAddress != null)
    			                {
    			                    break;
    			                }
    			            }
    			            else
    			            {
    			                LLog.w(String.format("Interface name format \"%s\" for address \"%s\" was not recognized as a usable IP", saddr, oldServerBindAddress));
    			            }
    			        }
    			        if (serverBindAddress != null) {
                            break;
                        }
                        DNSNameService dns;
                        try
                        {
                            dns = new DNSNameService();
                        }
                        catch (Exception e)
                        {
                            LLog.w("Failed to contact DNS server!");
                            LLog.w(e);
                            break;
                        }
                        InetAddress[] addrs = dns.lookupAllHostAddr(oldServerBindAddress);
                        if (addrs.length == 0)
                        {
                            LLog.w(String.format("Host \"%s\" is unknown by the DNS server!\nTrying DNS server again in 3 seconds...", oldServerBindAddress));
                            try
                            {
                                Thread.sleep(DNS_QUERY_FAIL_DELAY_MS);
                            }
                            catch (InterruptedException e)
                            {
                                LLog.e(e);
                            }
                            addrs = dns.lookupAllHostAddr(oldServerBindAddress);
                            if (addrs.length == 0)
                            {
                                LLog.w(String.format("Host \"%s\" is unknown by the DNS server!\nGiving up!", oldServerBindAddress));
                            }
                        }
                        
                        for (InetAddress addr : addrs)
                        {
                            String saddr = addr.getHostAddress();
                            if (saddr.equals("localhost") || saddr.equals("127.0.0.1"))
                            {
                                assert_(false); // how does this even happen?
                            }
                            else if (saddr.matches(IPV_4_6_REGEX_STR))
                            {
                                serverBindAddress = filterAddress(saddr, ipSpec, ipMask);
                                if (serverBindAddress != null)
                                {
                                    break;
                                }
                            }
                            else
                            {
                                LLog.w(String.format("Interface name format \"%s\" for address \"%s\" from DNS was not recognized as a usable IP", saddr, oldServerBindAddress));
                            }
                        }
			        } while (false);
			        
			        if (serverBindAddress == null)
			        {
			            
    			            if (isLoopbackPossible)
    			            {
    			                serverBindAddress = "127.0.0.1";
    			                LLog.w(String.format("Using loopback for bind address :\"%s\"", oldServerBindAddress));
    			            }
    			            else
    			            {
    			                LLog.e(new RuntimeException(String.format("Failed to get IP for :\"%s\"", oldServerBindAddress)), true, true);
    			            }
			        }
			    }
			    this.serverBindAddress = InetAddress.getByName(serverBindAddress);
			}
		}
		LLog.i(String.format("Listening to interface: %s", serverBindAddress));
		this.listenBacklog = listenBacklog;
		this.serverPort = serverPort;
		securityPolicy.clear();
	    File file = new File((String) SERVER_PROPERTIES.SERVER_SECURITY_POLICY.getValue());
	    securityPolicy.readPolicy(file);
	}
	
    private void runCleanupActions()
    {
        JitCompressedEvent.clearPool();
        GarbageUtil.cleanAllAsynchronously(stageLock, clientList);
    }
    
    public void run()
	{
        if (((Boolean)Server.SERVER_PROPERTIES.SUPPORT_CLIPBOARD_SYNCHRONIZATION.getValue()))
        {
            ClipboardMonitor.getInstance().addListener(new ClipboardListener(){
    
                @Override
                public void onChange(Clipboard clipboard)
                {
                    Manager.getInstance().sendToAll(SERVER_EVENT.CLIPBOARD_CHANGED, (Object[]) null);
                }
                
            });
        }
        
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
				ClientHandler newClient;
				while (true)
				{
					newClient = null;
					Socket socket = serverSocket.accept();
					if (_DEBUG) System.out.println("Got a new connection!");
					if (socket != null)
					{
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
			if (clientList.size() == 0)
			{
			    runCleanupActions();
			}
		}
		
	};
	
	public void removeClient(ClientHandler client)
	{
		withLock(removeClientAction, client);
	}
	
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
