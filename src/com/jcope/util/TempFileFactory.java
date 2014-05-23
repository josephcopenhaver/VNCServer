package com.jcope.util;

import java.io.File;
import java.io.IOException;

public class TempFileFactory
{
    public static final String tmpFileStoreName = "JCOPE_VNC";
    
    public static File get(String prefix, String suffix) throws IOException
    {
        File rval = File.createTempFile(prefix, suffix, new File(tmpFileStoreName));
        
        rval.deleteOnExit();
        rval.setReadable(true, true);
        rval.setWritable(true, true);
        
        return rval;
    }
    
}
