package com.jcope.util;

public class Platform
{
	
	private static Boolean isWindows = null;
	public static boolean isWindows()
	{
		if (isWindows == null)
		{
			isWindows = System.getProperty("os.name").startsWith("Windows");
		}
		return isWindows;
	}

}
