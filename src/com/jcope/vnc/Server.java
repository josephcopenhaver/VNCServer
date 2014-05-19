package com.jcope.vnc;

import static com.jcope.debug.Debug.assert_;

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
import java.util.Properties;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.jcope.debug.LLog;
import com.jcope.util.CurrentProcessInfo;
import com.jcope.util.EnumPropertyInterface;
import com.jcope.util.Platform;
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
    public static enum SERVER_PROPERTIES implements EnumPropertyInterface
    {
        SERVER_BIND_ADDRESS("localhost"),
        SERVER_PORT(1987),
        SERVER_LISTEN_BACKLOG(0),
        SERVER_SECURITY_POLICY("VncSecurityPolicy.xml"),
        SUPPORT_CLIPBOARD_SYNCHRONIZATION(Boolean.FALSE)
        
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
                case SERVER_BIND_ADDRESS:
                case SERVER_SECURITY_POLICY:
                    assert_(obj instanceof String);
                    break;
                case SERVER_LISTEN_BACKLOG:
                case SERVER_PORT:
                    assert_(obj instanceof Integer);
                    break;
                case SUPPORT_CLIPBOARD_SYNCHRONIZATION:
                    assert_(obj instanceof Boolean);
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
			if (Platform.isWindows())
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
			
			VncServer vncServer = new VncServer(serverPort, listenBacklog, serverBindAddress);
			
			System.out.println("VNCServer is running!");
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
