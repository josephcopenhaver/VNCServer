package com.jcope.util;


public class Platform
{
    
    private static enum DETECTABLE_PLATFORM {
        
        WINDOWS("windows"),
        MAC("mac os x")
        
        
        ;
        
        
        private Boolean isCurrent = null;
        private String osHeaderLowerCase;
        
        DETECTABLE_PLATFORM(String osHeaderLowerCase)
        {
            this.osHeaderLowerCase = osHeaderLowerCase;
        }
        
        public String osHeaderLowerCase() {
            return osHeaderLowerCase;
        }
        
        public Boolean isCurrent()
        {
            return isCurrent;
        }
        
        public void setCurrent(boolean isCurrent)
        {
            this.isCurrent = isCurrent;
        }
    };
    
    private static boolean isPlatform(DETECTABLE_PLATFORM platform)
    {
        Boolean rval = platform.isCurrent();
        
        if (null == rval)
        {
            platform.setCurrent(System.getProperty("os.name").toLowerCase().startsWith(platform.osHeaderLowerCase()));
            
            rval = platform.isCurrent();
            
            if (rval)
            {
                for(DETECTABLE_PLATFORM other : DETECTABLE_PLATFORM.values())
                {
                    if (!platform.equals(other))
                    {
                        other.setCurrent(Boolean.FALSE);
                    }
                }
            }
        }
        
        return rval;
    }
	
	public static boolean isWindows()
	{
		return isPlatform(DETECTABLE_PLATFORM.WINDOWS);
	}
	
	public static boolean isMac()
	{
	    return isPlatform(DETECTABLE_PLATFORM.MAC);
	}

}
