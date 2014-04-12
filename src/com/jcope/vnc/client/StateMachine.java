package com.jcope.vnc.client;

import static com.jcope.vnc.shared.InputEventInfo.MAX_QUEUE_SIZE;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import com.jcope.debug.LLog;
import com.jcope.util.TaskDispatcher;
import com.jcope.vnc.client.input.Handler;
import com.jcope.vnc.shared.AccessModes.ACCESS_MODE;
import com.jcope.vnc.shared.HashFactory;
import com.jcope.vnc.shared.InputEvent;
import com.jcope.vnc.shared.Msg;
import com.jcope.vnc.shared.Msg.CompressedObjectReader;
import com.jcope.vnc.shared.StateMachine.CLIENT_EVENT;
import com.jcope.vnc.shared.StateMachine.SERVER_EVENT;

public class StateMachine implements Runnable
{
	private MainFrame frame;
	private String serverAddress, password;
	private int serverPort;
	private Integer selectedScreenNum;
	
	private Socket socket;
	private volatile BufferedOutputStream out;
	private volatile Exception whyFailed = null;
	
	private Semaphore setWhyFailedLock = new Semaphore(1, true);
	
	private Semaphore inputHandlingSema = new Semaphore(1, true);
	
	private Semaphore sendSema = new Semaphore(1, true);
    private TaskDispatcher<Integer> dispatcher = new TaskDispatcher<Integer>("Client output dispatcher");
    
    private Semaphore queueAccessSema = new Semaphore(1, true);
    private volatile ArrayList<InputEvent> outQueue = null;
    private ACCESS_MODE accessMode = null;
	
	public StateMachine(String serverAddress, int serverPort, Integer selectedScreenNum, String password) throws UnknownHostException, IOException
	{
		this.serverAddress = serverAddress;
		this.serverPort = serverPort;
		this.selectedScreenNum = selectedScreenNum;
		this.password = password;
		
		frame = new MainFrame(this);
		frame.setVisible(true);
	}
	
	public Integer getSelectedScreen()
	{
	    Integer rval = selectedScreenNum;
	    
	    selectedScreenNum = null;
	    
	    if (rval == null)
	    {
	        String tmp = (String) JOptionPane.showInputDialog(frame, "Select screen", "0");
	        if (tmp != null)
	        {
	            try
	            {
	                rval = Integer.parseInt(tmp);
	            }
	            catch (NumberFormatException e)
	            {
	                // Do Nothing
	            }
	        }
	    }
	    
	    return rval;
	}
	
	public String getPasswordHash()
	{
	    String rval = password;
	    
	    password = null;
	    
	    if (rval == null)
	    {
	        rval = (String) JOptionPane.showInputDialog(frame, "Password?", "");
	        if (rval != null)
	        {
	            if (rval.equals(""))
	            {
	                rval = null;
	            }
	            else
	            {
	                rval = HashFactory.hash(rval);
	            }
	        }
	    }
	    
	    return rval;
	}
	
	private void setWhyFailed(Exception exception)
	{
	    if (whyFailed == null)
	    {
	        try
	        {
	            setWhyFailedLock.acquire();
	        }
            catch (InterruptedException e)
            {
                LLog.e(e);
            }
	        try
	        {
	            if (whyFailed == null)
	            {
	                whyFailed = exception;
	            }
	        }
	        finally {
	            setWhyFailedLock.release();
	        }
	    }
	}
	
	public void handleUserAction(Runnable r)
    {
        try
        {
            inputHandlingSema.acquire();
        }
        catch (InterruptedException e)
        {
            LLog.e(e);
        }
        try
        {
            r.run();
        }
        finally {
            inputHandlingSema.release();
        }
    }
    
    public void run()
	{
		boolean tryConnect, wasConnected;
		do
		{
		    tryConnect = Boolean.FALSE;
			wasConnected = Boolean.FALSE;
			socket = null;
			OutputStream os = null;
			InputStream is = null;
			out = null;
			BufferedInputStream in = null;
			try
			{
			    ACCESS_MODE defaultAccessMode = ACCESS_MODE.VIEW_ONLY;
			    ACCESS_MODE tmpAccessMode;
			    Integer tmpSelectedScreen;
			    String tmp;
			    
			    tmp = (String) JOptionPane.showInputDialog(frame, "Enter server address", "Server Address", JOptionPane.QUESTION_MESSAGE, null, null, serverAddress);
			    if (tmp != null && !tmp.equals(""))
			    {
			        serverAddress = tmp;
			    }
			    tmp = (String) JOptionPane.showInputDialog(frame, "Enter server port", "Server Port", JOptionPane.QUESTION_MESSAGE, null, null, (new Integer(serverPort)).toString());
			    if (tmp != null && !tmp.equals(""))
                {
			        try
			        {
			            serverPort = Integer.parseInt(tmp);
			        }
			        catch (NumberFormatException e)
			        {
			            // Do Nothing
			        }
                }
                tmpAccessMode = (ACCESS_MODE) JOptionPane.showInputDialog(frame, "Select access mode", "Select Access Mode", JOptionPane.QUESTION_MESSAGE, null, ACCESS_MODE.selectable(), defaultAccessMode);
			    if (tmpAccessMode == null)
			    {
			        tmpAccessMode = defaultAccessMode;
			    }
			    tmpSelectedScreen = getSelectedScreen();
			    if (tmpSelectedScreen == null)
			    {
			        tmpSelectedScreen = 0;
			    }
			    
			    final int selectedScreen = tmpSelectedScreen;
			    final String password = getPasswordHash();
			    final ACCESS_MODE accessMode = tmpAccessMode;
			    
				socket = new Socket(serverAddress, serverPort);
				wasConnected = Boolean.TRUE;
				os = socket.getOutputStream();
				out = new BufferedOutputStream(os);
				is = socket.getInputStream();
				in = new BufferedInputStream(is);
				
				this.accessMode = accessMode;
				
				SwingUtilities.invokeLater(new Runnable() {

                    @Override
                    public void run()
                    {
                        sendEvent(CLIENT_EVENT.SELECT_SCREEN, selectedScreen, accessMode, password);
                    }
				    
				});
				
				CompressedObjectReader reader = Msg.newCompressedObjectReader();
				Object obj;
                
				while ((obj = reader.readObject(in)) != null)
				{
				    try
			        {
				        inputHandlingSema.acquire();
			        }
			        catch (InterruptedException e)
			        {
			            LLog.e(e);
			        }
			        try
			        {
			            handleServerEvent(obj);
			        }
			        finally {
			            obj = null;
			            inputHandlingSema.release();
			        }
				}
			}
			catch (UnknownHostException e)
			{
			    setWhyFailed(e);
			}
			catch (IOException e)
			{
			    setWhyFailed(e);
			}
			finally {
				if (out != null) {try{out.close();}catch(Exception e){}}
				if (os  != null) {try{ os.close();}catch(Exception e){}}
				if (in  != null) {try{ in.close();}catch(Exception e){}}
				if (is  != null) {try{ is.close();}catch(Exception e){}}
				disconnect();
			}
			if (whyFailed != null)
			{
				try
				{
					String msg = wasConnected ? "Connection lost, reconnect?" : "Failed to connect, retry?";
					int result = JOptionPane.showConfirmDialog(frame, msg, msg, JOptionPane.ERROR_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
					if (JOptionPane.OK_OPTION == result)
					{
						tryConnect = Boolean.TRUE;
					}
					else
					{
						LLog.e(whyFailed, false);
					}
				}
				finally {
					whyFailed = null;
				}
			}
		} while (tryConnect);
		frame.dispose();
	}
	
	private void handleServerEvent(Object obj)
	{
	    if (obj instanceof SERVER_EVENT)
		{
			_handleServerEvent((SERVER_EVENT) obj, null);
		}
		else
		{
			Msg msg = (Msg) obj;
			_handleServerEvent((SERVER_EVENT) msg.event, msg.args);
		}
	}

	private void _handleServerEvent(SERVER_EVENT event, Object[] args)
	{
        LLog.logEvent("Server", event, args);
	    Handler.getInstance().handle(this, event, args);
	}
	
	public void sendEvent(CLIENT_EVENT event)
	{
	    sendEvent(event, (Object[]) null);
	}
	
	public void sendEvent(final CLIENT_EVENT event, final Object... args)
	{
	    Runnable r = new Runnable() {
            
            @Override
            public void run()
            {
                if (out == null)
                {
                    return; // TODO: fix the fact that this is an event black hole
                }
                try
                {
                    sendSema.acquire();
                }
                catch (InterruptedException e)
                {
                    LLog.e(e);
                }
                boolean killConnection = true;
                try
                {
                    try
                    {
                        Msg.send(out, event, args);
                        killConnection = false;
                    }
                    catch (IOException e)
                    {
                        setWhyFailed(e);
                    }
                }
                finally {
                    sendSema.release();
                    if (killConnection)
                    {
                        disconnect();
                    }
                }
            }
        };
        dispatcher.dispatch(event == CLIENT_EVENT.GET_SCREEN_SEGMENT ? -(((Integer)args[0]) + 2) : event.ordinal(), r);
	}
	
	public void disconnect()
	{
	    try
	    {
    		if (socket != null)
    		{
    		    try
    		    {
    		        socket.close();
    		    }
    		    catch (IOException e)
    		    {
    		        // Do Nothing
    		    }
    		    finally {
    		        socket = null;
    		    }
    		}
	    }
	    finally {
	        dispatcher.clear();
	    }
	}
	
	public MainFrame getFrame()
	{
	    return frame;
	}
	
	public void kill()
	{
		System.exit(0);
	}
	
	public InputEvent[] popEvents(int avail)
	{
	    InputEvent[] rval = null;
	    ArrayList<InputEvent> list;
	    int size;
	    
	    try
        {
            queueAccessSema.acquire();
        }
        catch (InterruptedException e)
        {
            LLog.e(e);
        }
        try
        {
            list = outQueue;
            if (list != null && (size = list.size()) > 0)
            {
                rval = new InputEvent[Math.min(avail, size)];
                for (int i=0; i<rval.length; i++)
                {
                    rval[i] = list.remove(0);
                }
                list.clear();
            }
        }
        finally {
            queueAccessSema.release();
        }
        
        return rval;
	}
    
    public void nts_acquireInputqueue()
    {
        try
        {
            queueAccessSema.acquire();
        }
        catch (InterruptedException e)
        {
            LLog.e(e);
        }
    }
    
    public void nts_releaseInputqueue()
    {
        queueAccessSema.release();
    }
    
    public void nts_addInput(InputEvent event)
    {
        ArrayList<InputEvent> list = outQueue;
        if (list == null)
        {
            list = new ArrayList<InputEvent>(MAX_QUEUE_SIZE);
            list.add(event);
            outQueue = list;
            sendEvent(CLIENT_EVENT.OFFER_INPUT, Boolean.TRUE, 1);
        }
        else
        {
            int size = list.size();
            if (size >= MAX_QUEUE_SIZE)
            {
                // Do Nothing
            }
            else if (size > 0)
            {
                InputEvent prev = list.get(size - 1);
                
                if (!prev.merge(event, true))
                {
                    // Could not merge
                    list.add(event);
                    sendEvent(CLIENT_EVENT.OFFER_INPUT, Boolean.TRUE, size + 1);
                }
                else if (size > 1 && list.get(size - 2).merge(prev, false))
                {
                    // A merge occurred, the event 'prev' may have become something else
                    // e.g. a pressed event...
                    // turns out the 'new' event was merge'able with it's previous event
                    list.remove(size - 1);
                }
            }
            else
            {
                list.add(event);
                sendEvent(CLIENT_EVENT.OFFER_INPUT, Boolean.TRUE, 1);
            }
        }
    }

    public void addInput(InputEvent event)
    {
        nts_acquireInputqueue();
        try
        {
            nts_addInput(event);
        }
        finally {
            nts_releaseInputqueue();
        }
    }

    public ACCESS_MODE getAccessMode()
    {
        return accessMode;
    }
}
