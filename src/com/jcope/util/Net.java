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
        
        if (ipSpec != null && ipMask != null)
        {
            byte[] tmp = getIPBytes(addr);
            for (int i = 0; i < tmp.length; i++)
            {
                if (((byte)(tmp[i] & ipMask[i])) != ipSpec[i])
                {
                    rval = null;
                    break;
                }
            }
        }
        
        return rval;
    }
}
