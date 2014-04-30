package com.jcope.util;

import static com.jcope.debug.Debug.assert_;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory; // Hack for windows



public class CurrentProcessInfo
{
    
	public static Long getPID() throws IOException
	{
	    Long rval = null;
		String stringRep = getPIDStr();
		
		if (stringRep != null)
		{
		    try
		    {
		        rval = Long.parseLong(stringRep);
		    }
		    catch (NumberFormatException e)
		    {
		        // ignore
		    }
		}
		
		return rval;
	}
	
	private static boolean isNumRPlus(String str)
	{
		boolean rval = str.matches("[1-9]\\d+");
		
		return rval;
	}
	
	public static String getPIDStr() throws IOException
	{
		String rval;
		
		try
		{
			rval = new File("/proc/self").getCanonicalFile().getName();
			if (!isNumRPlus(rval))
			{
				throw new IOException("Could not identify PID");
			}
		}
		catch (IOException e)
		{
			rval = getWindowsPID();
		}
		
		return rval;
	}
	
	private static String getWindowsPID()
	{
		String rval = null;
		String tmpStr;

	    // something like '<pid>@<hostname>', at least in SUN / Oracle JVMs
	    final String jvmName = ManagementFactory.getRuntimeMXBean().getName();
	    final int index = jvmName.indexOf('@');

		do
		{
			if (index < 1)
			{
				// part before '@' empty (index = 0) / '@' not found (index = -1)
				break;
			}

			tmpStr = jvmName.substring(0, index);
			
			if (!isNumRPlus(tmpStr))
			{
				break;
			}
			
			rval = tmpStr;
		} while (Boolean.FALSE);
		
	    return rval;
	}
}