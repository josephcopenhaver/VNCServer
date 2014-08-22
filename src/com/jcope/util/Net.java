package com.jcope.util;

public class Net
{
    public static final String IPV_4_6_REGEX_STR = "^(?:1?\\d\\d?|2[0-4]\\d|25[0-5])\\.(?:1?\\d\\d?|2[0-4]\\d|25[0-5])\\.(?:1?\\d\\d?|2[0-4]\\d|25[0-5])\\.(?:1?\\d\\d?|2[0-4]\\d|25[0-5])(?:\\.(?:1?\\d\\d?|2[0-4]\\d|25[0-5])\\.(?:1?\\d\\d?|2[0-4]\\d|25[0-5]))?$";
    
    public static final byte[] getIPBytes(String str)
    {
        int numChars = 0,
            ub = str.length(),
            i;
        
        for (i = 0; i < ub; i++)
        {
            if (str.charAt(i) == '.')
            {
                numChars++;
            }
        }
        
        byte[] out = new byte[numChars + 1];
        i = 0;
        
        for (String s : str.split("\\."))
        {
            out[i++] = (byte) (Integer.parseInt(s) & 0xff);
        }
        
        return out;
    }
    
    public static final String filterAddress(String addr, byte[] ipSpec, byte[] ipMask)
    {
        String rval = addr;
        
        byte[] tmp = getIPBytes(addr);
        
        if (ipSpec == null || ipMask == null)
        {
            rval = null;
        }
        else
        {
            for (int i = 0; i < tmp.length; i++)
            {
                if (((0xff & tmp[i]) & (0xff & ipMask[i])) != (0xff & ipSpec[i]))
                {
                    rval = null;
                    break;
                }
            }
        }
        
        return rval;
    }
}
