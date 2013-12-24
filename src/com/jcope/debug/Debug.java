package com.jcope.debug;

public class Debug
{
	public static final boolean DEBUG = Boolean.TRUE;
	
	public static void assert_(boolean condition)
	{
		if (!condition)
		{
		    throw new AssertionError();
		}
	}
	
}
