package com.jcope.util;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory; // Hack for windows



public class CurrentProcessInfo
{
    
	public static Long getPID()
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
	
	public static String getPIDStr()
	{
		String rval;
		boolean isValid;
		
		try
		{
			rval = new File("/proc/self").getCanonicalFile().getName();
			isValid = isNumRPlus(rval);
		}
		catch (IOException e)
		{
			rval = null;
			isValid = false;
		}

		if (!isValid)
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