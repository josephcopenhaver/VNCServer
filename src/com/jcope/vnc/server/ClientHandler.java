package com.jcope.vnc.server;

import static com.jcope.debug.Debug.assert_;
import static com.jcope.vnc.shared.ScreenSelector.getScreenDevicesOrdered;

import java.awt.GraphicsDevice;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.Semaphore;

import javax.swing.SwingUtilities;

import com.jcope.debug.LLog;
import com.jcope.util.FixedLengthBitSet;
import com.jcope.util.TaskDispatcher;
import com.jcope.vnc.server.screen.Manager;
import com.jcope.vnc.server.screen.ScreenListener;
import com.jcope.vnc.shared.AccessModes.ACCESS_MODE;
import com.jcope.vnc.shared.IOERunnable;
import com.jcope.vnc.shared.Msg;
import com.jcope.vnc.shared.Msg.CompressedObjectReader;
import com.jcope.vnc.shared.StateMachine.SERVER_EVENT;

public class ClientHandler extends Thread
{
    private Socket socket;
	private BufferedInputStream in = null;
	private BufferedOutputStream out = null;
	private ArrayList<Runnable> onDestroyActions = new ArrayList<Runnable>(1);
	private volatile boolean dying = Boolean.FALSE;
	private volatile boolean alive = Boolean.TRUE;
	
	private ClientState clientState = null;
	private DirectRobot dirbot = null;
	
	private TaskDispatcher<Integer> unserializedDispatcher;
    private TaskDispatcher<Integer> serializedDispatcher;
    private boolean isNewFlag = Boolean.TRUE;
    
    private ScreenListener[] screenListenerRef = new ScreenListener[]{null};
    
    private Semaphore sendSema = new Semaphore(1, true);
    private Semaphore serialSema = new Semaphore(1, true);
    volatile int tid = -1;
    
    private Semaphore handleIOSema = new Semaphore(1, true);
    private Semaphore queueSema = new Semaphore(1, true);
    private HashMap<Integer, SERVER_EVENT> nonSerialEventOutboundQueue = new HashMap<Integer, SERVER_EVENT>();
    private HashMap<Integer, Object[]> nonSerialEventQueue = new HashMap<Integer, Object[]>();
    private LinkedList<Integer> nonSerialOrderedEventQueue = new LinkedList<Integer>();
    
    private Semaphore changedSegmentsSema = new Semaphore(1, true);
    private volatile FixedLengthBitSet changedSegments = null;
	
	public ClientHandler(Socket socket) throws IOException
	{
	    super(toString(socket));
	    this.socket = socket;
		out = new BufferedOutputStream(socket.getOutputStream());
		in = new BufferedInputStream(socket.getInputStream());
		String strID = toString();
		unserializedDispatcher = new TaskDispatcher<Integer>(String.format("Non-serial dispatcher: %s", strID));
        serializedDispatcher = new TaskDispatcher<Integer>(String.format("Serial dispatcher: %s", strID));
        
        assert_(!SERVER_EVENT.SCREEN_SEGMENT_UPDATE.isImmediate());
        for (SERVER_EVENT event : SERVER_EVENT.values())
        {
        	if (event != SERVER_EVENT.SCREEN_SEGMENT_UPDATE)
        	{
        		unserializedDispatcher.setImmediate(event.isImmediate(), getNonSerialTID(event, null, 0));
        	}
        }
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
	    @Override
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
	
	private Runnable releaseIOResources = new Runnable()
	{
        @Override
        public void run()
        {
            try
            {
                queueSema.acquire();
            }
            catch (InterruptedException e)
            {
                LLog.e(e);
            }
            try
            {
                synchronized(nonSerialEventQueue) {
                    for (Object[] sargs : nonSerialEventQueue.values())
                    {
                        if (sargs == null || sargs[0] == null)
                        {
                            continue;
                        }
                        ((JitCompressedEvent)sargs[0]).release();
                    }
                }
            }
            finally {
                queueSema.release();
            }
        }
	};
	
	public void run()
	{
		try
		{
		    // Destroy actions are now LIFO
            addOnDestroyAction(getUnbindAliasAction(this));
            addOnDestroyAction(releaseIOResources);
			addOnDestroyAction(killIOAction);
			
			CompressedObjectReader reader = new CompressedObjectReader();
			Object obj = null;
			
			while (!dying)
			{
				try
				{
					obj = reader.readObject(in);
					if (obj == null)
	                {
	                    throw new IOException("Connection reset by peer");
	                }
				}
				catch (IOException e)
				{
					LLog.e(e);
				}
				StateMachine.handleClientInput(this, obj);
				obj = null;
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

	public boolean selectGraphicsDevice(int graphicsDeviceID, ACCESS_MODE accessMode, String password)
	{
	    boolean rval;
	    
	    GraphicsDevice[] devices = getScreenDevicesOrdered();
	    GraphicsDevice graphicsDevice = devices[graphicsDeviceID];
        
	    rval = Manager.getInstance().bind(this, graphicsDevice, accessMode, password);
	    
	    return rval;
	}
	
	public void addOnDestroyAction(Runnable r)
	{
		onDestroyActions.add(r);
	}
	
	public void kill()
	{
	    synchronized(this)
	    {
    		if (dying)
    		{
    			return;
    		}
    		dying = true;
	    }
		
	    Exception topE = null;
	    Runnable r;
        
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
            try
            {
                try
                {
                    serializedDispatcher.dispose();
                }
                finally {
                    unserializedDispatcher.dispose();
                }
            }
            finally {
                SwingUtilities.invokeLater(new Runnable() {
                    
                    @Override
                    public void run()
                    {
                        try
                        {
                            try
                            {
                                serializedDispatcher.join();
                            }
                            finally {
                                unserializedDispatcher.join();
                            }
                        }
                        catch (InterruptedException e)
                        {
                            LLog.e(e, Boolean.FALSE);
                        }
                    }
                    
                });
            }
        }
        finally {
    		try
    		{
    			for (int idx = onDestroyActions.size(); idx > 0;)
    			{
    			    r = onDestroyActions.get(--idx);
    				try
    				{
    				    r.run();
    				}
    				catch(Exception e)
    				{
    				    if (topE == null)
    				    {
    				        topE = e;
    				    }
    				    else
    				    {
    				        System.err.println(e.getMessage());
    				        e.printStackTrace(System.err);
    				    }
    				}
    			}
    			if (topE != null)
                {
                    throw new RuntimeException(topE);
                }
    		}
    		finally {
    		    try
    		    {
    		    onDestroyActions.clear();
    			onDestroyActions = null;
    		    }
    		    finally {
    		        alive = false;
    		    }
    		}
        }
	}
	
	public boolean isRunning()
	{
		return !dying;
	}
	
	public boolean isDead()
	{
		return !alive;
	}
	
	public ClientState getClientState()
	{
		return clientState;
	}
	
	public void initClientState()
	{
		clientState = new ClientState();
	}

	public ScreenListener getScreenListener(final DirectRobot dirbot)
	{
		ScreenListener l = screenListenerRef[0];
		if (l == null || dirbot != this.dirbot)
		{
			this.dirbot = dirbot;
			l = new ScreenListener() {
			    
				@Override
				public void onScreenChange(FixedLengthBitSet changedSegments)
				{
					sendEvent(SERVER_EVENT.SCREEN_SEGMENT_CHANGED, changedSegments);
				}
			};
			screenListenerRef[0] = l;
		}
		return l;
		
	}
	
	public void sendEvent(JitCompressedEvent jce)
    {
        _sendEvent(jce.getEvent(), jce, (Object[]) null);
    }
	
	public void sendEvent(SERVER_EVENT event)
    {
        sendEvent(event, (Object[]) null);
    }
	
	public void sendEvent(final SERVER_EVENT event, final Object... args)
	{
	    _sendEvent(event, null, args);
	}

	public void _sendEvent(final SERVER_EVENT event, final JitCompressedEvent jce, final Object... args)
	{
		if (event == SERVER_EVENT.SCREEN_SEGMENT_CHANGED)
        {
            assert_(jce == null);
            assert_(args.length == 1);
            assert_(args[0] instanceof FixedLengthBitSet);
            try
            {
                changedSegmentsSema.acquire();
            }
            catch (InterruptedException e)
            {
                LLog.e(e);
            }
            try
            {
                FixedLengthBitSet flbs = changedSegments;
                if (flbs != null)
                {
                    flbs.or(((FixedLengthBitSet) args[0]));
                    return;
                }
                flbs = ((FixedLengthBitSet) args[0]).clone();
                args[0] = flbs;
                changedSegments = flbs;
            }
            finally {
                changedSegmentsSema.release();
            }
        }
	    try
        {
            handleIOSema.acquire();
        }
        catch (InterruptedException e)
        {
            LLog.e(e);
        }
	    try
        {
	        nts_sendEvent(event, jce, args);
        }
        finally {
            handleIOSema.release();
        }
	}
	
	private void nts_sendEvent(final SERVER_EVENT event, final JitCompressedEvent jce, final Object... args)
	{
	    int tidTmp;
	    TaskDispatcher<Integer> dispatcher;
	    boolean dispatch;
	    boolean isMutable;
	    
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
		        tidTmp = tid;
		        tidTmp++;
                if (tidTmp < 0)
                {
                    tidTmp = 0;
                }
                tid = tidTmp;
            }
    		finally {
    		    serialSema.release();
    		}
            dispatcher = serializedDispatcher;
            dispatch = Boolean.TRUE;
        }
		else
		{
		    tidTmp = getNonSerialTID(event, args, 0);
		    dispatcher = unserializedDispatcher;
		    // TODO: only dispatch if we know for sure that the arguments have changed
		    if ((isMutable = event.hasMutableArgs()) || !unserializedDispatcher.queueContains(tidTmp))
		    {
		        if (event == SERVER_EVENT.SCREEN_SEGMENT_UPDATE)
                {
                    dispatch = Boolean.TRUE;
                }
		        else
		        {
    		        try
    	            {
    	                queueSema.acquire();
    	            }
    	            catch (InterruptedException e)
    	            {
    	                LLog.e(e);
    	            }
    	            try
    	            {
    	                synchronized(nonSerialEventQueue) {synchronized(nonSerialEventOutboundQueue) {synchronized(nonSerialOrderedEventQueue) {
    	                    if (nonSerialEventOutboundQueue.get(tidTmp) == null)
                            {
                                dispatch = Boolean.TRUE;
                                nonSerialEventOutboundQueue.put(tidTmp, event);
                                if (event.isImmediate())
                                {
                                	nonSerialOrderedEventQueue.addFirst(tidTmp);
                                }
                                else
                                {
                                	nonSerialOrderedEventQueue.addLast(tidTmp);
                                }
                            }
                            else
                            {
                                Object[] sargs = null;
                                dispatch = Boolean.FALSE;
                                if (isMutable || (sargs = nonSerialEventQueue.get(tidTmp)) == null)
                                {
                                    JitCompressedEvent jce2;
                                    if (isMutable)
                                    {
                                        sargs = nonSerialEventQueue.get(tidTmp);
                                    }
                                    if (sargs == null)
                                    {
                                        sargs = new Object[]{ jce, args };
                                        nonSerialEventQueue.put(tidTmp, sargs);
                                    }
                                    else
                                    {
                                        jce2 = (JitCompressedEvent) sargs[0];
                                        if (jce2 != null)
                                        {
                                            jce2.release();
                                        }
                                        sargs[0] = jce;
                                        sargs[1] = args;
                                    }
                                    if (jce != null)
                                    {
                                        jce.acquire();
                                    }
                                }
                            }
                        }}}
    	            }
    	            finally {
    	                queueSema.release();
    	            }
		        }
		    }
		    else
		    {
		        dispatch = Boolean.FALSE;
		    }
		        
		}
		if (dispatch)
		{
		    final IOERunnable msgAction;
		    if (event == SERVER_EVENT.SCREEN_SEGMENT_CHANGED)
		    {
		        msgAction = new IOERunnable() {
		            
                    @Override
                    public void run() throws IOException
                    {
                        try
                        {
                            changedSegmentsSema.acquire();
                        }
                        catch (InterruptedException e)
                        {
                            LLog.e(e);
                        }
                        try
                        {
                            ClientHandler.this.changedSegments = null;
                        }
                        finally {
                            changedSegmentsSema.release();
                        }
                        Msg.send(out, jce, event, args);
                    }
                    
                };
		    }
		    else
		    {
		        msgAction = new IOERunnable() {

                    @Override
                    public void run() throws IOException
                    {
                        Msg.send(out, jce, event, args);
                    }
		            
		        };
		    }
		    Runnable r = new Runnable() {
	            
	            @Override
	            public void run()
	            {
	                boolean killSelf = true;
	                boolean flushed = false;
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
	                        msgAction.run();
	                        if (serializedDispatcher.isEmpty() && unserializedDispatcher.isEmpty())
	                        {
	                            flushed = true;
	                            out.flush();
	                        }
	                        switch(event)
	                        {
                                case AUTHORIZATION_UPDATE:
                                    if (!flushed)
                                    {
                                        flushed = true;
                                        out.flush();
                                    }
                                    if (!((Boolean) args[0]))
                                    {
                                        SwingUtilities.invokeLater(new Runnable() {

                                            @Override
                                            public void run()
                                            {
                                                kill();
                                            }
                                            
                                        });
                                    }
                                    break;
                                case ALIAS_CHANGED:
                                case ALIAS_DISCONNECTED:
                                case ALIAS_REGISTERED:
                                case ALIAS_UNREGISTERED:
                                case CHAT_MSG_TO_ALL:
                                case CHAT_MSG_TO_USER:
                                case CLIENT_ALIAS_UPDATE:
                                case CONNECTION_CLOSED:
                                case CONNECTION_ESTABLISHED:
                                case CURSOR_GONE:
                                case CURSOR_MOVE:
                                case FAILED_AUTHORIZATION:
                                case NUM_SCREENS_CHANGED:
                                case SCREEN_GONE:
                                case SCREEN_RESIZED:
                                case SCREEN_SEGMENT_CHANGED:
                                case SCREEN_SEGMENT_SIZE_UPDATE:
                                case SCREEN_SEGMENT_UPDATE:
                                case READ_INPUT_EVENTS:
                                case CLIPBOARD_CHANGED:
                                case GET_CLIPBOARD:
                                case SET_CLIPBOARD:
                                    break;
	                        }
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
	        
	        Runnable rOnDestroy;
	        
	        if (jce == null)
	        {
	            rOnDestroy = null;
	        }
	        else
	        {
	            jce.acquire();
	            rOnDestroy = jce.getOnDestroy();
	        } 
	        
	        dispatcher.dispatch(tidTmp, r, rOnDestroy);
		}
	}
	
	private int getNonSerialTID(SERVER_EVENT event, Object[] refStack, int idxSegmentID)
    {
	    int rval;
	    if (event == SERVER_EVENT.SCREEN_SEGMENT_UPDATE)
        {
	        rval = ((Integer)refStack[idxSegmentID]) + 2;
            rval += SERVER_EVENT.getMaxOrdinal();
        }
        else
        {
            rval = event.ordinal();
        }
	    
	    return rval;
    }

    public Object getSegmentOptimized(int segmentID)
	{
	    Object rval = Manager.getInstance().getSegmentOptimized(dirbot, segmentID);
	    
	    return rval;
	}
	
	public DirectRobot getDirbot()
	{
	    return dirbot;
	}
	
	public void handleEventAck(SERVER_EVENT ackForEvent, Object[] refStack, int idxSegmentID)
	{
	    ArrayList<SERVER_EVENT> evtlist = new ArrayList<SERVER_EVENT>();
	    ArrayList<Object[]> plist = new ArrayList<Object[]>();
	    int tTid = getNonSerialTID(ackForEvent, refStack, idxSegmentID);
	    int idx = 0;
        Exception firstE = null;
	    int tid;
	    SERVER_EVENT hiddenAckEvt;
        Object[] sargs;
	    JitCompressedEvent jce;
	    
	    try
        {
            handleIOSema.acquire();
        }
        catch (InterruptedException e)
        {
            LLog.e(e);
        }
	    try
	    {
    	    try
            {
                queueSema.acquire();
            }
            catch (InterruptedException e)
            {
                LLog.e(e);
            }
    	    try
            {
                synchronized(nonSerialEventQueue) {synchronized(nonSerialEventOutboundQueue) {synchronized(nonSerialOrderedEventQueue) {
                    do
                    {
                        tid = nonSerialOrderedEventQueue.removeFirst();
                        hiddenAckEvt = nonSerialEventOutboundQueue.remove(tid);
                        sargs = nonSerialEventQueue.remove(tid);
                        if (sargs != null)
                        {
                            evtlist.add(hiddenAckEvt);
                            plist.add(sargs);
                        }
                    } while (tid != tTid);
                }}}
            }
            finally {
                queueSema.release();
            }
            for (Object[] targs : plist)
            {
                hiddenAckEvt = evtlist.get(idx++);
                jce = (JitCompressedEvent) targs[0];
                Object[] args = (Object[]) targs[1];
                
                // erase targs to unbind objects
                targs[0] = null;
                targs[1] = null;
                
                // flush deferred queue contents
                if (firstE == null)
                {
	                try
	                {
	                	//LLog.i(String.format("Sending pending event %s with %d # of args", hiddenAckEvt.name(), args == null ? 0 : args.length));
	                    nts_sendEvent(hiddenAckEvt, jce, args);
	                }
	                catch (Exception e)
	                {
	                    firstE = e;
	                }
	                finally {
	                    if (jce != null)
	                    {
	                        jce.release();
	                    }
	                }
                }
                else if (jce != null)
                {
                	jce.release();
                }
            }
            
            if (firstE != null)
            {
                throw new RuntimeException(firstE);
            }
            
            assert_(plist.size() == idx);
	    }
	    finally {
	        handleIOSema.release();
	    }
	}
}
