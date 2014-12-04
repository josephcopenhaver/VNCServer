package com.jcope.vnc;

import static com.jcope.debug.Debug.assert_;
import static com.jcope.util.Net.IPV_4_6_REGEX_STR;
import static com.jcope.util.Net.getIPBytes;
import static com.jcope.util.Platform.PLATFORM_IS_WINDOWS;
import static com.jcope.util.Time.mustParseISO8601Duration;

import java.awt.AWTException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.UnknownHostException;
import java.nio.channels.FileLock;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.Properties;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.jcope.debug.LLog;
import com.jcope.util.CurrentProcessInfo;
import com.jcope.util.TypeSafeEnumPropertyPattern;
import com.jcope.vnc.server.VncServer;

/**
 * 
 * @author Joseph Copenhaver
 *
 * This class shall be the main execution point of a server instance.
 * 
 */

public class Server
{
    
	private static final GregorianCalendar startTime = new GregorianCalendar();
    
    public static enum SERVER_PROPERTIES implements TypeSafeEnumPropertyPattern
    {
        SERVER_BIND_ADDRESS("localhost"),
        SERVER_PORT(1987),
        SERVER_LISTEN_BACKLOG(0),
        SERVER_SECURITY_POLICY("VncSecurityPolicy.xml"),
        SUPPORT_CLIPBOARD_SYNCHRONIZATION(Boolean.FALSE),
        SERVER_BIND_ADDRESS_SPEC(null),
        SERVER_BIND_ADDRESS_MASK(null),
        MIN_MONITOR_SCANNING_PERIOD(Long.valueOf(mustParseISO8601Duration("T1S", startTime))),
        OBEY_SPEED_LIMITS(Boolean.TRUE)
        
        ;
        
        Object value;
        
        SERVER_PROPERTIES(final Object defaultValue)
        {
            this.value = defaultValue;
        }
        
        public void assertType(Object obj)
        {
            assert_(obj != null);
            switch (this)
            {
                case SERVER_BIND_ADDRESS_SPEC:
                case SERVER_BIND_ADDRESS_MASK:
                    assert_(obj == null || obj instanceof byte[]);
                    break;
                case SERVER_BIND_ADDRESS:
                case SERVER_SECURITY_POLICY:
                    assert_(obj instanceof String);
                    break;
                case SERVER_LISTEN_BACKLOG:
                case SERVER_PORT:
                    assert_(obj instanceof Integer);
                    break;
                case OBEY_SPEED_LIMITS:
                case SUPPORT_CLIPBOARD_SYNCHRONIZATION:
                    assert_(obj instanceof Boolean);
                    break;
                case MIN_MONITOR_SCANNING_PERIOD:
                    assert_(obj instanceof Long);
                    break;
            }
        }
        
        public Object getValue()
        {
            return value;
        }
        
        public void setValue(Object value)
        {
            switch (this)
            {
                case SERVER_BIND_ADDRESS_SPEC:
                case SERVER_BIND_ADDRESS_MASK:
                    String str;
                    if (value != null && value instanceof String)
                    {
                        str = (String) value;
                        if (str.matches(IPV_4_6_REGEX_STR))
                        {
                            value = getIPBytes(str);
                        }
                    }
                    break;
                case SERVER_BIND_ADDRESS:
                case SERVER_SECURITY_POLICY:
                    break;
                case SERVER_LISTEN_BACKLOG:
                case SERVER_PORT:
                    if (value instanceof String)
                    {
                        value = Integer.parseInt((String) value);
                    }
                    break;
                case OBEY_SPEED_LIMITS:
                case SUPPORT_CLIPBOARD_SYNCHRONIZATION:
                    if (value instanceof String)
                    {
                        value = Integer.parseInt((String) value);
                    }
                    if (value instanceof Integer)
                    {
                        value = Boolean.valueOf(0 != ((Integer)value));
                    }
                    break;
                case MIN_MONITOR_SCANNING_PERIOD:
                    value = Long.valueOf(mustParseISO8601Duration((String) value, startTime));
                    break;
            }
            assertType(value);
            this.value = value;
        }
        
        public void load(Properties prop)
        {
            Object value = prop.get(name());
            if (value != null)
            {
                setValue(value);
            }
        }
        
        public static void loadConfig(String properyFile) throws FileNotFoundException, IOException
        {
            if (properyFile == null)
            {
                System.out.println("No property file loaded.");
                return;
            }
            Properties prop = new Properties();
            FileInputStream in = new FileInputStream(properyFile);
            try
            {
                prop.load(in);
                for (SERVER_PROPERTIES iprop : SERVER_PROPERTIES.values())
                {
                    iprop.load(prop);
                }
            }
            finally {
                in.close();
            }
        }
    }
    
    public static void main(String[] args) throws UnknownHostException, IOException, InterruptedException, AWTException, ParserConfigurationException, SAXException
    {
        RandomAccessFile serverFileRAF = null;
        FileLock serverFileLock = null;
        RandomAccessFile pidFileRAF = null;
        FileLock pidFileLock = null;
        FileOutputStream pidFileFOS = null;
        boolean forceStop = Boolean.FALSE;
        ArrayList<File> lockFiles = new ArrayList<File>(2);
        int serverPort, listenBacklog;
        String serverBindAddress;
        
        SERVER_PROPERTIES.loadConfig(args.length > 0 ? args[0] : null);
        
        try
        {
            
            
            // begin single instance logic!
            
            
            File serverLockFile = new File("server.lock");
            serverFileRAF = new RandomAccessFile(serverLockFile, "rw");
            serverFileLock = serverFileRAF.getChannel().tryLock();
            if (serverFileLock == null)
            {
                throw new RuntimeException(String.format("Server already running:\n%s", serverLockFile.getAbsolutePath()));
            }
            File pidFile = new File("server.pid");
            pidFileRAF = new RandomAccessFile(pidFile, "rw");
            pidFileLock = pidFileRAF.getChannel().tryLock();
            if (pidFileLock == null)
            {
                throw new RuntimeException(String.format("Server already running (cannot lock pid file):\n%s", pidFile.getAbsolutePath()));
            }
            lockFiles.add(pidFile);
            lockFiles.add(serverLockFile);
            if (PLATFORM_IS_WINDOWS)
            {
                pidFileLock.release();
                pidFileLock = null;
            }
            pidFileFOS = new FileOutputStream(pidFile);
            pidFileFOS.write(CurrentProcessInfo.getPIDStr().getBytes());
            pidFileFOS.write('\n');
            pidFileFOS.flush();
            
            
            // end single instance logic
            
            serverPort = (Integer) SERVER_PROPERTIES.SERVER_PORT.getValue();
            listenBacklog = (Integer) SERVER_PROPERTIES.SERVER_LISTEN_BACKLOG.getValue();
            serverBindAddress = (String) SERVER_PROPERTIES.SERVER_BIND_ADDRESS.getValue();
            
            byte[] bNSpec = (byte[]) SERVER_PROPERTIES.SERVER_BIND_ADDRESS_SPEC.getValue();
            byte[] bNMask = (byte[]) SERVER_PROPERTIES.SERVER_BIND_ADDRESS_MASK.getValue();
            
            if (((bNSpec == null) != (bNMask == null)) || ((bNSpec != null) && (bNSpec.length != bNMask.length)))
            {
                LLog.e(new RuntimeException("SERVER_BIND_ADDRESS_SPEC property and SERVER_BIND_ADDRESS_MASK property are not configured for the same network"));
            }
            
            VncServer vncServer = new VncServer(serverPort, listenBacklog, serverBindAddress, bNSpec, bNMask);
            
            System.out.println("VNCServer is running!");
            System.out.println(String.format("MIN_SCANNING_PERIOD_MS=%d", SERVER_PROPERTIES.MIN_MONITOR_SCANNING_PERIOD.getValue()));
            System.out.println(String.format("OBEYING_SPEED_LIMITS=%s", ((Boolean)SERVER_PROPERTIES.OBEY_SPEED_LIMITS.getValue()) ? "Y" : "N"));
            vncServer.run();
            forceStop = Boolean.TRUE;
        }
        finally {
            try
            {
                if (pidFileFOS != null)     {try{pidFileFOS.close();      }catch(Exception e){}}
                if (pidFileLock != null)    {try{pidFileLock.release();   }catch(Exception e){}}
                if (pidFileRAF != null)     {try{pidFileRAF.close();      }catch(Exception e){}}
                if (serverFileLock != null) {try{serverFileLock.release();}catch(Exception e){}}
                if (serverFileRAF != null)  {try{serverFileRAF.close();   }catch(Exception e){}}
                for (File f : lockFiles)
                {
                    try{f.delete();}catch(Exception e){LLog.e(e, false);}
                }
            }
            finally {
                System.out.println("VNCServer has stopped!");
                if (forceStop)
                {
                    System.exit(0);
                }
            }
        }
    }
    
}
