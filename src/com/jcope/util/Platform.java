package com.jcope.util;


public class Platform
{
    private static String osNameLower = System.getProperty("os.name").toLowerCase();
    
    private static enum DETECTABLE_PLATFORM {
        
        WINDOWS("windows"),
        MAC("mac os x"),
        UNKNOWN(null) // sentinel
        
        
        ;
        
        
        private final boolean isPlatform;
        
        DETECTABLE_PLATFORM(String osHeaderLowerCase)
        {
            if (osHeaderLowerCase == null)
            {
                isPlatform = (osNameLower != null);
            }
            else
            {
                if (osNameLower == null)
                {
                    isPlatform = Boolean.FALSE;
                }
                else
                {
                    isPlatform = osNameLower.startsWith(osHeaderLowerCase);
                    if (isPlatform)
                    {
                        osHeaderLowerCase = null;
                    }
                }
            }
        }
    };
    
    public static boolean isWindows()
	{
		return DETECTABLE_PLATFORM.WINDOWS.isPlatform;
	}
	
	public static boolean isMac()
	{
	    return DETECTABLE_PLATFORM.MAC.isPlatform;
	}
	
	public static boolean isUnknown()
	{
	    return DETECTABLE_PLATFORM.UNKNOWN.isPlatform;
	}

}
