package com.jcope.vnc.server;

import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;

import com.jcope.debug.LLog;
import com.jcope.util.TaskDispatcher;
import com.jcope.vnc.server.screen.Manager;
import com.jcope.vnc.server.screen.ScreenListener;
import com.jcope.vnc.shared.Msg;
import com.jcope.vnc.shared.StateMachine.SERVER_EVENT;

public class ClientHandler extends Thread
{
	
	private GraphicsDevice graphicsDevice = null;
	private Socket socket;
	private ObjectInputStream in = null;
	private ObjectOutputStream out = null;
	private ArrayList<Runnable> onDestroyActions = new ArrayList<Runnable>(1);
	private volatile boolean dying = Boolean.FALSE;
	private volatile boolean alive = Boolean.TRUE;
	
	private ClientState clientState = null;
	private DirectRobot dirbot = null;
	
	private TaskDispatcher<Integer> unserializedDispatcher;
    private TaskDispatcher<Integer> serializedDispatcher;
    private boolean isNewFlag = Boolean.TRUE;
	
	public ClientHandler(Socket socket) throws IOException
	{
	    super(toString(socket));
	    this.socket = socket;
		out = new ObjectOutputStream(socket.getOutputStream());
		in = new ObjectInputStream(socket.getInputStream());
		String strID = toString();
		unserializedDispatcher = new TaskDispatcher<Integer>(String.format("Non-serial dispatcher: %s", strID));
        serializedDispatcher = new TaskDispatcher<Integer>(String.format("Serial dispatcher: %s", strID));
	}
	
	public String toString()
	{
	    String rval = toString(socket);
	    
	    return rval;
	}
	
	private static String toString(Socket socket)
	{
	    String rval = String.format("ClientHandler: %s", socketToString(socket));
	    
	    return rval;
	}
	
	private static String socketToString(Socket socket)
	{
		InetAddress addr = socket.getInetAddress();
		return String.format("%s - %s - %d - %d", addr.getHostName(), addr.getHostAddress(), socket.getLocalPort(), socket.getPort());
	}
	
	private static Runnable getUnbindAliasAction(final ClientHandler thiz)
	{
	    Runnable rval = new Runnable()
	    {
	        
	        @Override
	        public void run()
	        {
	            if (AliasRegistry.hasInstance())
	            {
	                AliasRegistry.getInstance().unbind(thiz);
	            }
	        }
	        
	    };
	    
	    return rval;
	}
	
	public boolean getIsNewFlag()
	{
	    return isNewFlag;
	}
	
	public void setIsNewFlag(boolean x)
	{
	    isNewFlag = x;
	}
	
	private Runnable killIOAction = new Runnable()
	{
		public void run()
		{
			try
			{
				in.close();
			}
			catch (IOException e)
			{
				LLog.e(e);
			}
			finally {
				try
				{
					out.close();
				}
				catch (IOException e)
				{
					LLog.e(e);
				}
				finally {
					try
					{
						socket.close();
					}
					catch (IOException e)
					{
						LLog.e(e);
					}
				}
			}
		}
	};
	
	public void run()
	{
		try
		{
			addOnDestroyAction(killIOAction);
			addOnDestroyAction(getUnbindAliasAction(this));
			graphicsDevice = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
			Manager.getInstance().bind(this, graphicsDevice);
			while (!dying)
			{
				Object obj = null;
				try
				{
					obj = in.readObject();
				}
				catch (IOException e)
				{
					LLog.e(e);
				}
				catch (ClassNotFoundException e)
				{
					LLog.e(e);
				}
				handle(obj);
			}
		}
		catch (Exception e)
		{
			LLog.e(e, false);
		}
		finally
		{
			kill();
		}
	}

	public void addOnDestroyAction(Runnable r)
	{
		onDestroyActions.add(r);
	}
	
	public void kill()
	{
		if (dying)
		{
			return;
		}
		dying = true;
		
		try
		{
		    Manager.getInstance().unbind(this);
		}
		catch(Exception e)
		{
		    LLog.e(e,false);
		}
		
		try
		{
			for (Runnable r : onDestroyActions)
			{
				r.run();
			}
		}
		finally {
			onDestroyActions.clear();
			onDestroyActions = null;
			try
			{
			    serializedDispatcher.dispose();
			    unserializedDispatcher.dispose();
			    serializedDispatcher.join();
			    unserializedDispatcher.join();
			}
            catch (InterruptedException e)
            {
                LLog.e(e);
            }
		}
		
		alive = false;
	}
	
	public boolean isRunning()
	{
		return !dying;
	}
	
	public boolean isDead()
	{
		return !alive;
	}
	
	private void handle(Object obj)
	{
		StateMachine.handleClientInput(this, obj);
	}
	
	public ClientState getClientState()
	{
		return clientState;
	}
	
	public void initClientState()
	{
		clientState = new ClientState();
	}

	private ScreenListener[] screenListenerRef = new ScreenListener[]{null};
	public ScreenListener getScreenListener(final DirectRobot dirbot)
	{
		ScreenListener l = screenListenerRef[0];
		if (l == null || dirbot != this.dirbot)
		{
			this.dirbot = dirbot;
			l = new ScreenListener() {
			    
				@Override
				public void onScreenChange(int segmentID)
				{
					sendEvent(SERVER_EVENT.SCREEN_SEGMENT_CHANGED, Integer.valueOf(segmentID));
				}
			};
			screenListenerRef[0] = l;
		}
		return l;
		
	}
	
	private Semaphore sendSema = new Semaphore(1, true);
	private Semaphore serialSema = new Semaphore(1, true);
    volatile int tid = -1;

	public void sendEvent(final SERVER_EVENT event, final Object... args)
	{
	    int tidTmp;
	    TaskDispatcher<Integer> dispatcher;
	    boolean dispatch;
	    
		if (event.isSerial())
        {
		    try
		    {
		        serialSema.acquire();
		    }
            catch (InterruptedException e)
            {
                LLog.e(e);
            }
		    try
		    {
                tid++;
                if (tid<0)
                {
                    tid=0;
                }
                tidTmp = tid;
            }
    		finally {
    		    serialSema.release();
    		}
            dispatcher = serializedDispatcher;
            dispatch = true;
        }
		else
		{
		    if (event == SERVER_EVENT.SCREEN_SEGMENT_CHANGED || event == SERVER_EVENT.SCREEN_SEGMENT_UPDATE)
		    {
		        tidTmp = ((Integer)args[0]) + 2;
		        if (event == SERVER_EVENT.SCREEN_SEGMENT_UPDATE)
		        {
		            tidTmp += SERVER_EVENT.getMaxOrdinal();
		        }
		        else
		        {
		            tidTmp = -tidTmp;
		        }
		    }
		    else
		    {
		        tidTmp = event.ordinal();
		    }
		    dispatcher = unserializedDispatcher;
		    // TODO: only dispatch if we know for sure that the arguments have changed
		    dispatch = (event.hasMutableArgs() || !unserializedDispatcher.queueContains(tidTmp));
		        
		}
		if (dispatch)
		{
		    Runnable r = new Runnable() {
	            
	            @Override
	            public void run()
	            {
	                boolean killSelf = true;
	                try
	                {
	                    try
	                    {
	                        sendSema.acquire();
	                    }
	                    catch (InterruptedException e)
	                    {
	                        LLog.e(e);
	                    }
	                    try
	                    {
	                        Msg.send(out, event, args);
	                    }
	                    catch (IOException e)
	                    {
	                        LLog.e(e);
	                    }
	                    finally {
	                        sendSema.release();
	                    }
	                    killSelf = false;
	                }
	                finally {
	                    if (killSelf)
	                    {
	                        kill();
	                    }
	                }
	            }
	        };
	        dispatcher.dispatch(tidTmp, r);
		}
	}
	
	public int[] getSegment(int segmentID)
	{
	    int[] rval = Manager.getInstance().getSegment(dirbot, segmentID);
	    
	    return rval;
	}
}
