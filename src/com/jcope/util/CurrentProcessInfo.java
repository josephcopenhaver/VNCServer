package com.jcope.util;

import static com.jcope.debug.Debug.assert_;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory; // Hack for windows



public class CurrentProcessInfo
{
    
	public static Long getPID() throws IOException
	{
	    Long[] untaintedSrcLong = new Long[]{null};
		String stringRep = getPIDStr(untaintedSrcLong);
		
		if (stringRep != null)
		{
		    untaintedSrcLong[0] = null;
		    try
		    {
		        untaintedSrcLong[0] = Long.parseLong(stringRep);
		    }
		    catch (NumberFormatException e)
		    {
		        // ignore
		    }
		}
		
		return untaintedSrcLong[0];
	}
	
	public static String getPIDStr() throws IOException
	{
	    return getPIDStr(null);
	}
	
	private static String getPIDStr(Long[] untaintedSrcLong) throws IOException
	{
		String rval;
		try
		{
			rval = new File("/proc/self").getCanonicalFile().getName();
			if (!rval.matches("[1-9]\\d+"))
			{
				throw new IOException("Could not identify PID");
			}
		}
		catch (IOException e)
		{
			Object pid = getWindowsPID(untaintedSrcLong == null ? String.class : Long.class);
			if (pid == null)
			{
				throw(e);
			}
			else if (untaintedSrcLong == null)
		    {
		        rval = (String) pid;
		    }
		    else
		    {
		        untaintedSrcLong[0] = (Long) pid;
                rval = null;
		    }
		}
		return rval;
	}
	
	private static Object getWindowsPID(Class<?> wantedReturnType)
	{
	    // something like '<pid>@<hostname>', at least in SUN / Oracle JVMs
	    final String jvmName = ManagementFactory.getRuntimeMXBean().getName();
	    final int index = jvmName.indexOf('@');

	    if (index < 1)
	    {
	        // part before '@' empty (index = 0) / '@' not found (index = -1)
	        return null;
	    }

	    try
	    {
	        long tmp = Long.parseLong(jvmName.substring(0, index));
	        if (wantedReturnType == Long.class)
	        {
	            return tmp;
	        }
	        else if (wantedReturnType == String.class)
	        {
	            return Long.toString(tmp);
	        }
	        else
	        {
	            assert_(false);
	        }
	    }
	    catch (NumberFormatException e)
	    {
	        // ignore
	    }
	    return null;
	}
}