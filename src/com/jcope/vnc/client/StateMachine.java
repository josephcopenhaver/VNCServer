package com.jcope.vnc.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.Semaphore;

import javax.swing.JOptionPane;

import com.jcope.debug.LLog;
import com.jcope.util.TaskDispatcher;
import com.jcope.vnc.client.input.Handler;
import com.jcope.vnc.shared.Msg;
import com.jcope.vnc.shared.StateMachine.CLIENT_EVENT;
import com.jcope.vnc.shared.StateMachine.SERVER_EVENT;

public class StateMachine implements Runnable
{
	private MainFrame frame;
	private String serverAddress;
	private int serverPort;
	
	private Socket socket;
	private volatile ObjectOutputStream out;
	
	public StateMachine(String serverAddress, int serverPort) throws UnknownHostException, IOException
	{
		this.serverAddress = serverAddress;
		this.serverPort = serverPort;
		
		frame = new MainFrame(this);
		frame.setVisible(true);
	}

	public void run()
	{
		boolean tryConnect, wasConnected;
		Exception whyFailed = null;
		do
		{
			tryConnect = Boolean.FALSE;
			wasConnected = Boolean.FALSE;
			socket = null;
			OutputStream os = null;
			InputStream is = null;
			out = null;
			ObjectInputStream in = null;
			try
			{
				socket = new Socket(serverAddress, serverPort);
				wasConnected = Boolean.TRUE;
				os = socket.getOutputStream();
				out = new ObjectOutputStream(os);
				is = socket.getInputStream();
				in = new ObjectInputStream(is);
				Object obj;
				while ((obj = in.readObject()) != null)
				{
					handleServerEvent(obj);
				}
			}
			catch (UnknownHostException e)
			{
				whyFailed = e;
			}
			catch (IOException e)
			{
				whyFailed = e;
			}
			catch (ClassNotFoundException e)
			{
				LLog.e(e);
			}
			finally {
				if (out != null) {try{out.close();}catch(Exception e){}}
				if (os != null)     {try{os.close();    }catch(Exception e){}}
				if (in != null)     {try{in.close();    }catch(Exception e){}}
				if (is != null)     {try{is.close();    }catch(Exception e){}}
				if (socket != null) {try{socket.close();}catch(Exception e){}}
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
	
	private Semaphore sendSema = new Semaphore(1, true);
	private TaskDispatcher<Integer> dispatcher = new TaskDispatcher<Integer>("Client output dispatcher");

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
                        LLog.e(e, false); // TODO: should be able to handoff this error to "whyfail" concept
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
	
	private void disconnect()
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
		}
		socket = null;
	}
	
	public void kill()
	{
		System.exit(0);
	}
}
