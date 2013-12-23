package com.jcope.util;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory; // Hack for windows



public class CurrentProcessInfo
{
    
	public static int getPID() throws IOException
	{
		return Integer.parseInt(getPIDStr());
	}
	
	public static String getPIDStr() throws IOException
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
			rval = getWindowsPID();
			if (rval == null)
			{
				throw(e);
			}
		}
		return rval;
	}
	private static String getWindowsPID()
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
	        return Long.toString(Long.parseLong(jvmName.substring(0, index)));
	    }
	    catch (NumberFormatException e)
	    {
	        // ignore
	    }
	    return null;
	}
}