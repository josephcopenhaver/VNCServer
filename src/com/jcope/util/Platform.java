package com.jcope.util;

import static com.jcope.debug.Debug.assert_;
import static com.jcope.debug.Debug.DEBUG;

import com.jcope.debug.LLog;



public class Platform
{
    public static final boolean PLATFORM_IS_WINDOWS;
    public static final boolean PLATFORM_IS_MAC;
    public static final boolean PLATFORM_IS_UNKNOWN;
    public static final boolean SUPPORT_META_KEY;
    private static Object detectedPlatform = null;
    
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
            if (isPlatform)
            {
                assert_(detectedPlatform == null);
                detectedPlatform = this;
                if (DEBUG)
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
        Boolean supportMetaKey = null;
        Object thisPlatform = detectedPlatform;
        detectedPlatform = null;
        switch ((DETECTABLE_PLATFORM) thisPlatform)
        {
            case WINDOWS:
                supportMetaKey = Boolean.FALSE;
                break;
            case MAC:
            case UNKNOWN:
                supportMetaKey = Boolean.TRUE;
                break;
        }
        SUPPORT_META_KEY = supportMetaKey;
    }
}