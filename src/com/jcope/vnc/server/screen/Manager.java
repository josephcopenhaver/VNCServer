package com.jcope.vnc.server.screen;

import static com.jcope.debug.Debug.assert_;
import static com.jcope.vnc.server.StateMachine.handleServerEvent;
import static com.jcope.vnc.shared.ScreenSelector.getScreenDevices;

import java.awt.AWTException;
import java.awt.GraphicsDevice;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.concurrent.Semaphore;

import com.jcope.debug.LLog;
import com.jcope.vnc.server.ClientHandler;
import com.jcope.vnc.server.DirectRobot;
import com.jcope.vnc.server.JitCompressedEvent;
import com.jcope.vnc.server.VncServer;
import com.jcope.vnc.shared.AccessModes.ACCESS_MODE;
import com.jcope.vnc.shared.StateMachine.SERVER_EVENT;

/**
 * 
 * @author Joseph Copenhaver
 *
 * Container/controller class for all active Screen Monitors.
 * 
 */

public class Manager extends Thread
{
	public static final int SEGMENT_WIDTH = 32;
	public static final int SEGMENT_HEIGHT = 32;
	public static final long refreshMS = 1000;
	
	private static final Manager[] selfRef = new Manager[]{null};
	private static final Semaphore instanceSema = new Semaphore(1, true);
	private HashMap<GraphicsDevice, ArrayList<ClientHandler>> clientsPerGraphicsDevice;
	private HashMap<GraphicsDevice, Monitor> monitorForGraphicsDevice;
	
	private Semaphore hasMonitorLock = new Semaphore(0, true);
    private Semaphore stageLock = new Semaphore(1, true);
    private volatile Object[] stagedArgs = null;
	
	private Manager()
	{
		super("Screen Manager");
		clientsPerGraphicsDevice = new HashMap<GraphicsDevice, ArrayList<ClientHandler>>();
		monitorForGraphicsDevice = new HashMap<GraphicsDevice, Monitor>();
	}
	
	public static Manager getInstance()
	{
		Manager rval = selfRef[0];
		
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
						rval = new Manager();
						selfRef[0] = rval;
						rval.start();
					}
				}
			}
			finally {
				instanceSema.release();
			}
		}
		
		return rval;
	}
	
	private void decreaseMonitorLock()
	{
	    try
        {
            hasMonitorLock.acquire();
        }
        catch (InterruptedException e)
        {
            LLog.e(e);
        }
	}
	
	private void increaseMonitorLock()
	{
	    hasMonitorLock.release();
	}
	
	public void run()
	{
		HashMap<GraphicsDevice,Boolean> graphicDevicesSet = new HashMap<GraphicsDevice,Boolean>();
		HashMap<GraphicsDevice,Boolean> tmpGraphicsDeviceSet = new HashMap<GraphicsDevice,Boolean>();
		boolean setChanged;
		int numMissing;
		while (true)
		{
		    decreaseMonitorLock();
		    try
		    {
    			setChanged = Boolean.FALSE;
    			GraphicsDevice[] graphicsDeviceList = getScreenDevices();
    			tmpGraphicsDeviceSet.putAll(graphicDevicesSet);
    			for (GraphicsDevice graphicsDevice : graphicsDeviceList)
    			{
    				if (tmpGraphicsDeviceSet.remove(graphicsDevice) == null)
    				{
    					setChanged = Boolean.TRUE;
    					graphicDevicesSet.put(graphicsDevice, Boolean.TRUE);
    				}
    			}
    			numMissing = tmpGraphicsDeviceSet.size();
    			if (numMissing > 0)
    			{
    				try
    				{
    					setChanged = Boolean.TRUE;
    					GraphicsDevice[] missingSet = new GraphicsDevice[numMissing];
    					missingSet = tmpGraphicsDeviceSet.keySet().toArray(missingSet);
    					for (GraphicsDevice graphicsDevice : missingSet)
    					{
    						graphicDevicesSet.remove(graphicsDevice);
    					}
    					unbindSet(missingSet);
    				}
    				finally {
    					tmpGraphicsDeviceSet.clear();
    				}
    			}
    			if (setChanged)
    			{
    				sendToAll(SERVER_EVENT.NUM_SCREENS_CHANGED, Integer.valueOf(graphicDevicesSet.size()));
    			}
    			else
    			{
    				try
    				{
    					sleep(refreshMS);
    				}
    				catch (InterruptedException e)
    				{
    					LLog.e(e);
    				}
    			}
		    }
		    finally {
		        increaseMonitorLock();
		    }
		}
	}
	
	private void createMonitorForGraphicsDevice(GraphicsDevice graphicsDevice, ArrayList<ClientHandler> registeredClients)
	{
		DirectRobot dirbot = null;
		try
		{
			dirbot = new DirectRobot(graphicsDevice);
		}
		catch (AWTException e)
		{
			LLog.e(e);
		}
		Monitor monitor = new Monitor(SEGMENT_WIDTH, SEGMENT_HEIGHT, dirbot, registeredClients);
		for (ClientHandler client : registeredClients)
		{
		    monitor.sendDisplayInitEvents(client);
		}
		monitor.start();
		monitorForGraphicsDevice.put(graphicsDevice, monitor);
	}
	
	private void killMonitorForGraphicsDevice(GraphicsDevice graphicsDevice)
	{
		Monitor monitor = monitorForGraphicsDevice.remove(graphicsDevice);
		if (monitor != null)
		{
			monitor.kill();
			//monitor.join();
		}
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
			synchronized(clientsPerGraphicsDevice){synchronized(monitorForGraphicsDevice)
			{
				r.run();
			}}
		}
		finally {
			stagedArgs = null;
			stageLock.release();
		}
	}
	
	private Runnable actionBind = new Runnable()
	{

		@Override
		public void run()
		{
			boolean newMonitor = Boolean.FALSE;
			ClientHandler client = (ClientHandler) stagedArgs[0];
			GraphicsDevice graphicsDevice = (GraphicsDevice) stagedArgs[1];
			assert_(graphicsDevice != null);
			assert_(client != null);
			
			actionUnbind.run();
			ArrayList<ClientHandler> registeredClients = clientsPerGraphicsDevice.get(graphicsDevice);
			if (registeredClients == null)
			{
				registeredClients = new ArrayList<ClientHandler>(1);
				clientsPerGraphicsDevice.put(graphicsDevice, registeredClients);
				newMonitor = Boolean.TRUE;
			}
			else if (registeredClients.contains(client))
			{
				return;
			}
			increaseMonitorLock();
			if (!newMonitor)
			{
			    Monitor monitor = monitorForGraphicsDevice.get(graphicsDevice);
                monitor.sendDisplayInitEvents(client);
			}
			registeredClients.add(client);
			if (newMonitor)
			{
				createMonitorForGraphicsDevice(graphicsDevice, registeredClients);
			}
		}
		
	};
	
	private Runnable actionUnbind = new Runnable()
	{

		@Override
		public void run()
		{
			ClientHandler client = (ClientHandler) stagedArgs[0];
			assert_(client != null);
			
			for (Entry<GraphicsDevice, ArrayList<ClientHandler>> entry : clientsPerGraphicsDevice.entrySet())
			{
				ArrayList<ClientHandler> list = entry.getValue();
				if (list.contains(client))
				{
					list.remove(client);
					if (list.isEmpty())
					{
						GraphicsDevice graphicsDevice = entry.getKey();
						clientsPerGraphicsDevice.remove(graphicsDevice);
						killMonitorForGraphicsDevice(graphicsDevice);
					}
					decreaseMonitorLock();
					break;
				}
			}
		}
		
	};
	
	private Runnable actionUnbindAllSet = new Runnable()
	{

		@Override
		public void run()
		{
			GraphicsDevice[] set = (GraphicsDevice[]) stagedArgs;
			stagedArgs = new Object[]{null};
			for (GraphicsDevice graphicsDevice : set)
			{
				stagedArgs[0] = graphicsDevice;
				actionUnbindAll.run();
			}
		}
		
	};
	
	private Runnable actionUnbindAll = new Runnable()
	{

		@Override
		public void run()
		{
			GraphicsDevice graphicsDevice = (GraphicsDevice) stagedArgs[0];
			assert_(graphicsDevice != null);
			
			ArrayList<ClientHandler> clientsToSignal = clientsPerGraphicsDevice.remove(graphicsDevice);
			if (clientsToSignal != null)
			{
				try
				{
				    SERVER_EVENT event = SERVER_EVENT.SCREEN_GONE;
				    JitCompressedEvent jce = (clientsToSignal.size() > 1) ? JitCompressedEvent.getInstance(event, (Object[]) null) : null;
				    try
				    {
    				    for (ClientHandler client : clientsToSignal)
    					{
    					    if (jce != null)
    					    {
    					        client.sendEvent(jce);
    					    }
    					    else
    					    {
    					        handleServerEvent(client, event);
    					    }
    						decreaseMonitorLock();
    					}
				    }
				    finally {
				        if (jce != null)
				        {
				            jce.release();
				        }
				    }
				}
				finally {
					clientsToSignal.clear();
				}
			}
			killMonitorForGraphicsDevice(graphicsDevice);
		}
		
	};
	
	private Runnable actionSendEventToAllExcept = new Runnable()
	{

		@Override
		public void run()
		{
		    ClientHandler notThiz = stagedArgs[0] == null ? null : ((ClientHandler) stagedArgs[0]);
			SERVER_EVENT evt = (SERVER_EVENT) stagedArgs[1];
			Object[] evtArgs = (Object[]) stagedArgs[2];
			JitCompressedEvent jce = JitCompressedEvent.getInstance(evt, evtArgs);
			try
			{
    			for (ArrayList<ClientHandler> clientList : clientsPerGraphicsDevice.values())
    			{
    			    handleServerEvent(notThiz, clientList, jce, evt);
    			}
			}
			finally {
			    jce.release();
			}
		}
		
	};
	
	public boolean bind(ClientHandler client, GraphicsDevice graphicsDevice, ACCESS_MODE accessMode, String passwordHash)
	{
	    boolean rval = false;
	    
	    if (VncServer.securityPolicy.checkAuth(graphicsDevice, accessMode, passwordHash))
	    {
	        withLock(actionBind, client, graphicsDevice);
	        rval = true;
	    }
	    
	    return rval;
	}
	
	public void unbind(ClientHandler client)
	{
		withLock(actionUnbind, client);
	}
	
	private void unbindSet(GraphicsDevice[] set)
	{
		withLock(actionUnbindAllSet, (Object[]) set);
	}
	
	public void sendToAll(SERVER_EVENT evt, Object... args)
	{
		withLock(actionSendEventToAllExcept, null, evt, args); 
	}
    
    public void sendToAllExcept(ClientHandler notThiz, SERVER_EVENT evt, Object... args)
    {
        withLock(actionSendEventToAllExcept, notThiz, evt, args); 
    }
	
	public Object getSegmentOptimized(DirectRobot dirbot, int segmentID)
	{
	    Object rval = null;
	    
	    Monitor monitor = monitorForGraphicsDevice.get(dirbot.device);
	    if (monitor != null)
	    {
	        rval = monitor.getSegmentOptimized(segmentID);
	    }
	    
	    return rval;
	}
	
	public void getOrigin(DirectRobot dirbot, int[] pos)
	{
	    assert_(pos != null);
	    assert_(pos.length > 1);
        pos[0] = 0;
        pos[1] = 0;
        
        Monitor monitor = monitorForGraphicsDevice.get(dirbot.device);
        if (monitor != null)
        {
            monitor.getOrigin(pos);
        }
	}
}
