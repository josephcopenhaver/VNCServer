package com.jcope.vnc.shared;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import com.jcope.vnc.shared.StateMachine.CLIENT_EVENT;
import com.jcope.vnc.shared.StateMachine.SERVER_EVENT;

public class Msg implements Serializable
{
	// Generated: serialVersionUID
	private static final long serialVersionUID = -1197396024588406286L;
	
	public final Object event;
	public final Object[] args;
	
	private Msg(Object event, Object[] args)
	{
		this.event = event;
		this.args = args;
	}
	
	public static void send(ObjectOutputStream out, SERVER_EVENT event, Object... args) throws IOException
	{
		_send(out, event, args);
	}
	
	public static void send(ObjectOutputStream out, CLIENT_EVENT event, Object... args) throws IOException
	{
		_send(out, event, args);
	}
	
	private static void _send(ObjectOutputStream out, Object event, Object... args) throws IOException
	{
		if (args == null)
		{
			out.writeObject(event);
		}
		else
		{
			out.writeObject(new Msg(event, args));
		}
		out.flush();
		out.reset();
		// TODO: periodically flush and reset rather than always flushing
	}
}
