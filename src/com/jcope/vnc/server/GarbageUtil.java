package com.jcope.vnc.server;

import java.util.ArrayList;
import java.util.concurrent.Semaphore;

import javax.swing.SwingUtilities;

import com.jcope.debug.LLog;

public class GarbageUtil
{
    private static final Semaphore sema = new Semaphore(1, Boolean.TRUE);
    private static volatile boolean running = Boolean.FALSE;
    private static volatile Semaphore stageLock;
    private static volatile ArrayList<ClientHandler> clientList;
    
    private static final Runnable cleanAllAction = new Runnable()
    {
		private Semaphore stageLock;
		private ArrayList<ClientHandler> clientList;
		private long garbageSize, garbageSizePre;
		private Runtime runtime;
        private boolean rval;
        
        private boolean isDone()
        {
            rval = Boolean.TRUE;
            try
            {
                sema.acquire();
            }
            catch (InterruptedException e)
            {
                LLog.e(e);
            }
            try
            {
				try
                {
                    stageLock.acquire();
                }
                catch (InterruptedException e)
                {
                    LLog.e(e);
                }
                try
                {
                    synchronized(clientList) {
                        rval = clientList.size() > 0;
                    }
                }
                finally {
                    stageLock.release();
                }
            }
            finally {
                sema.release();
            }
            
            return rval;
        }

        @Override
        public void run()
        {
			running = Boolean.TRUE;
			try
            {
			    this.stageLock = GarbageUtil.stageLock;
	            this.clientList = GarbageUtil.clientList;
                runtime = Runtime.getRuntime();
                garbageSize = runtime.totalMemory() - runtime.freeMemory();
                garbageSizePre = garbageSize;
                while (!isDone())
                {
                    System.gc();
                    garbageSize = runtime.totalMemory() - runtime.freeMemory();
                    if (garbageSize >= garbageSizePre)
                    {
                        break;
                    }
                    garbageSizePre = garbageSize;
                }
            }
            finally {
                running = Boolean.FALSE;
                runtime = null;
				GarbageUtil.stageLock = null;
				GarbageUtil.clientList = null;
            }
        }  
    };

    public static void cleanAllAsynchronously(Semaphore stageLock, ArrayList<ClientHandler> clientList)
    {
        try
        {
            sema.acquire();
        }
        catch (InterruptedException e)
        {
            LLog.e(e);
        }
        try
        {
            if (running)
            {
                return;
            }
            running = Boolean.TRUE;
            GarbageUtil.stageLock = stageLock;
            GarbageUtil.clientList = clientList;
            SwingUtilities.invokeLater(cleanAllAction);
        }
        finally {
            sema.release();
        }
    }
    
}
