package com.jcope.util;

import static com.jcope.debug.Debug.DEBUG;

import com.jcope.debug.LLog;



public class Platform
{
    public static final boolean PLATFORM_IS_WINDOWS;
    public static final boolean PLATFORM_IS_MAC;
    public static final boolean PLATFORM_IS_UNKNOWN;
    
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
                        osNameLower = null;
                    }
                }
            }
            if (DEBUG)
            {
                if (isPlatform)
                {
                    LLog.w("PLATFORM IS: " + this.name());
                }
            }
        }
    };
    
    static
    {
        PLATFORM_IS_WINDOWS = DETECTABLE_PLATFORM.WINDOWS.isPlatform;
        PLATFORM_IS_MAC = DETECTABLE_PLATFORM.MAC.isPlatform;
        PLATFORM_IS_UNKNOWN = DETECTABLE_PLATFORM.UNKNOWN.isPlatform;
    }
}