package com.jcope.vnc;

import java.awt.AWTException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.UnknownHostException;
import java.nio.channels.FileLock;
import java.util.ArrayList;

import com.jcope.debug.LLog;
import com.jcope.util.CurrentProcessInfo;
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
	
	public static void main(String[] args) throws UnknownHostException, IOException, InterruptedException, AWTException
	{
		RandomAccessFile serverFileRAF = null;
		FileLock serverFileLock = null;
		RandomAccessFile pidFileRAF = null;
		FileLock pidFileLock = null;
		FileOutputStream pidFileFOS = null;
		boolean forceStop = Boolean.FALSE;
		ArrayList<File> lockFiles = new ArrayList<File>(2);
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
			
			VncServer vncServer = new VncServer(1979, 0, "localhost");
			
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
