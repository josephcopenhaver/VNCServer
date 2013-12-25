package com.jcope.vnc.shared.input;

public abstract class Handler<R,T>
{
    
    public abstract void handle(R obj, T event, Object... args);
    
    public static String formatNameCamelCase(String name)
    {
        StringBuffer sb = new StringBuffer();
        boolean isFirst = true;
        boolean doCap = true;
        
        for (char c : name.toCharArray())
        {
            String tmpU = String.format("%c", c).toUpperCase();
            String tmpL = tmpU.toLowerCase();
            if (!tmpU.equals(tmpL))
            {
                if (isFirst)
                {
                    isFirst = false;
                    doCap = false;
                    sb.append(tmpU.charAt(0));
                }
                else if (doCap)
                {
                    doCap = false;
                    sb.append(tmpU.charAt(0));
                }
                else
                {
                    sb.append(tmpL.charAt(0));
                }
            }
            else if (c == '_')
            {
                doCap = true;
            }
        }
        
        return sb.toString();
    }
}
