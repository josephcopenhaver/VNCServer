package com.jcope.util;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Semaphore;

import com.jcope.debug.LLog;

public class TempFileFactory
{
    public static final String tmpFileStoreName = "JCOPE_VNC";
    private static volatile File subSystemTempPath = null;
    private static Semaphore sema = new Semaphore(1, true);
    
    public static File get(String prefix, String suffix) throws IOException
    {
        getSubSystemTempPath();
        File rval = File.createTempFile(prefix, suffix, subSystemTempPath);
        
        rval.deleteOnExit();
        rval.setReadable(true, true);
        rval.setWritable(true, true);
        
        return rval;
    }

    private static void getSubSystemTempPath()
    {
        if (subSystemTempPath == null)
        {
            try
            {
                sema.acquire();
            }
            catch (InterruptedException e)
            {
                LLog.e(e);
            }
            synchronized(TempFileFactory.class)
            {
                if (subSystemTempPath != null)
                {
                    return;
                }
                File f = null;
                try
                {
                    f = File.createTempFile("tmp", "tmp");
                }
                catch (IOException e)
                {
                    LLog.e(e);
                }
                subSystemTempPath = new File(f.getParentFile().getAbsolutePath(), tmpFileStoreName);
                if ((!subSystemTempPath.exists() && !subSystemTempPath.mkdir()) || (subSystemTempPath.exists() && !subSystemTempPath.isDirectory()))
                {
                    LLog.e(new IOException("Could not create temp directory: " + subSystemTempPath.getAbsolutePath()));
                }
            }
            sema.release();
        }
    }
    
}
