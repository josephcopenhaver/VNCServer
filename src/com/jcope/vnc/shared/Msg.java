package com.jcope.vnc.shared;

import static com.jcope.debug.Debug.assert_;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.jcope.debug.LLog;
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
	
	public static Object decompress(Object obj)
	{
	    Object rval = null;
	    
	    assert_(obj instanceof byte[]);
	    
	    ByteArrayInputStream bis = new ByteArrayInputStream((byte[]) obj);
	    try
        {
            GZIPInputStream gzip_in = new GZIPInputStream(bis);
            ObjectInputStream ois = new ObjectInputStream(gzip_in);
            
            rval = ois.readObject();
            ois.close();
            gzip_in.close();
        }
        catch (IOException e)
        {
            LLog.e(e);
        }
        catch (ClassNotFoundException e)
        {
            LLog.e(e);
        }
	    
	    try
        {
            bis.close();
        }
        catch (IOException e)
        {
            LLog.e(e);
        }
	    
	    return rval;
	}
	
	private static Object compress(Object obj)
	{
	    Object rval = null;
	    
	    ByteArrayOutputStream bos = new ByteArrayOutputStream();
	    try
        {
	        GZIPOutputStream gzip_out = new GZIPOutputStream(bos);
            ObjectOutputStream oos = new ObjectOutputStream(gzip_out);
            oos.writeObject(obj);
            oos.flush();
            gzip_out.flush();
            oos.close();
            gzip_out.close();
            
            rval = bos.toByteArray();
        }
        catch (IOException e)
        {
            LLog.e(e);
        }
        
        try
        {
            bos.close();
        }
        catch (IOException e)
        {
            LLog.e(e);
        }
	    
	    return rval;
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
			out.writeObject(compress(event));
		}
		else
		{
			out.writeObject(compress(new Msg(event, args)));
		}
		out.flush();
		out.reset();
		// TODO: periodically flush and reset rather than always flushing
	}
}
