package com.jcope.vnc.shared;

import static com.jcope.vnc.shared.MsgCache.compressionCache;
import static com.jcope.vnc.shared.MsgCache.compressionResultCache;
import static com.jcope.vnc.shared.MsgCache.precompRBOS;
import static com.jcope.vnc.shared.MsgCache.precompSema;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.jcope.debug.LLog;
import com.jcope.util.ReusableByteArrayOutputStream;
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
	
	private static Object decompress(byte[] bArray, int length)
	{
	    Object rval = null;
	    
	     ByteArrayInputStream bis = new ByteArrayInputStream(bArray, 0, length);
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
	
	public static byte[] getCompressed(SERVER_EVENT event, Object... args)
	{
	    try
	    {
	        precompSema.acquire();
	    }
	    catch (InterruptedException e)
	    {
	        LLog.e(e);
	    }
	    try
	    {
	        return (byte[]) compress(null, (args == null) ? event : new Msg(event, args));
	    }
	    finally {
	        precompSema.release();
	    }
	}
	
	private static byte[] compress(BufferedOutputStream out, Object obj)
	{
	    byte[] rval = null;
	    ReusableByteArrayOutputStream rbos;
	    int resultSize;
	    
	    if (out == null)
	    {
	        if (precompRBOS == null)
	        {
	            precompRBOS = new ReusableByteArrayOutputStream();
	        }
	        else
	        {
	            precompRBOS.reset();
	        }
	        rbos = precompRBOS;
	    }
	    else
	    {
    	    rbos = compressionCache.get(out);
    	    if (rbos == null)
    	    {
    	        rbos = new ReusableByteArrayOutputStream();
    	        compressionCache.put(out, rbos);
    	    }
    	    else
    	    {
    	        rbos.reset();
    	    }
	    }
	    
	    try
        {
	        GZIPOutputStream gzip_out = new GZIPOutputStream(rbos);
            ObjectOutputStream oos = new ObjectOutputStream(gzip_out);
            oos.writeObject(obj);
            oos.flush();
            gzip_out.flush();
            oos.close();
            gzip_out.close();
            
            resultSize = rbos.size();
            if (out == null)
            {
                rval = rbos.toByteArray();
            }
            else
            {
                HashMap<Integer,WeakReference<byte[]>> resultCache = compressionResultCache.get(out);
                if (resultCache == null)
                {
                    resultCache = new HashMap<Integer,WeakReference<byte[]>>(1);
                    compressionResultCache.put(out, resultCache);
                    rval = new byte[resultSize];
                    resultCache.put(resultSize, new WeakReference<byte[]>(rval));
                }
                else
                {
                    WeakReference<byte[]> ref = resultCache.get(resultSize);
                    rval = (ref == null) ? null : ref.get();
                    if (rval == null)
                    {
                        rval = new byte[resultSize];
                        resultCache.put(resultSize, new WeakReference<byte[]>(rval));
                    }
                }
                rbos.toByteArray(rval);
            }
        }
        catch (IOException e)
        {
            LLog.e(e);
        }
	    
	    return rval;
	}
	
	public static void send(BufferedOutputStream out, byte[] preCompressed, SERVER_EVENT event, Object... args) throws IOException
	{
		_send(out, preCompressed, event, args);
	}
	
	public static void send(BufferedOutputStream out, CLIENT_EVENT event, Object... args) throws IOException
	{
		_send(out, null, event, args);
	}
	
	private static void _send(BufferedOutputStream out, byte[] preCompressed, Object event, Object... args) throws IOException
	{
	    if (preCompressed == null)
	    {
    	    if (args == null)
    		{
    	        preCompressed = compress(out, event);
    		}
    		else
    		{
    		    preCompressed = compress(out, new Msg(event, args));
    		}
    	}
	    
	    if (preCompressed.length > 0)
	    {
	        byte b = preCompressed[0];
	        
	        preCompressed[0] = (byte)(preCompressed.length & 0xff);
    	    out.write(preCompressed, 0, 1);
    	    
    	    preCompressed[0] = (byte)((preCompressed.length >> 8) & 0xff);
	        out.write(preCompressed, 0, 1);
	        
	        preCompressed[0] = (byte)((preCompressed.length >> 16) & 0xff);
	        out.write(preCompressed, 0, 1);
	        
	        preCompressed[0] = (byte)((preCompressed.length >> 24) & 0xff);
	        out.write(preCompressed, 0, 1);
	        
	        preCompressed[0] = b;
    	    out.write(preCompressed);
    	    
    		out.flush();
    		// TODO: periodically flush and reset rather than always flushing
	    }
	}
	
	public static class CompressedObjectReader
	{
	    private BufferedInputStream in;
	    private byte[] buffer;
	    private int pos,
            dp,
            size;
        
        public CompressedObjectReader()
        {
            buffer = new byte[4];
        }
        
        private void fillBuffer() throws IOException
        {
            do
            {
                
                dp = in.read(buffer, pos, size-pos);
                if (dp < 0)
                {
                    break;
                }
                pos += dp;
                
            } while (pos < size);
        }
        
        public Object readObject(BufferedInputStream in) throws IOException
        {
            Object rval = null;
            
            this.in = in;
            
            try
            {
                
                do
                {
                    pos = 0;
                    size = 4;
                    
                    fillBuffer();
                    
                    if (dp < 1)
                    {
                        // why would it ever be zero and allowed to continue?
                        break;
                    }
                    
                    pos = 0;
                    size = (0xff & buffer[0])
                        | ((0xff & buffer[1]) << 8)
                        | ((0xff & buffer[2]) << 16)
                        | ((0xff & buffer[3]) << 24);
                    
                    if (buffer.length < size)
                    {
                        buffer = new byte[size];
                    }
                    
                    fillBuffer();
                    
                    if (dp < 1)
                    {
                        // why would it ever be zero and allowed to continue?
                        break;
                    }
                    
                    rval = decompress(buffer, size);
                    
                } while (Boolean.FALSE);
                
            }
            finally {
                
                this.in = null;
                
            }
            
            return rval;
        }
	}
}
