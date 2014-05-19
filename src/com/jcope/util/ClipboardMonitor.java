package com.jcope.util;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;

import com.jcope.debug.LLog;

public class ClipboardMonitor extends Thread implements ClipboardOwner
{
    public static interface ClipboardListener
    {
        public void onChange(Clipboard clipboard);
    }
    
    private static final long delay_ms = 200L;
    private static Clipboard clipboard;  
    private static ArrayList<ClipboardListener> listeners;
    private static final ClipboardMonitor[] selfRef = new ClipboardMonitor[]{null};
    private static final Semaphore InstanceSema = new Semaphore(1, Boolean.TRUE);
    
    private volatile boolean disposed;
    private Semaphore notificationSema;
    private volatile boolean changed;
    private Semaphore syncSema;
    
    private ClipboardMonitor()
    {
        // static
        clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        listeners = new ArrayList<ClipboardListener>(1);
        
        // instance member initialization
        disposed = Boolean.FALSE;
        notificationSema = new Semaphore(0, Boolean.TRUE);
        changed = Boolean.FALSE;
        syncSema = new Semaphore(1, Boolean.TRUE);
        
        // instance config
        setName("Clipboard Monitor");
        setDaemon(Boolean.TRUE);
        setPriority(NORM_PRIORITY);
        start();
    }
    
    public static boolean hasInstance()
    {
        boolean rval;
        
        try
        {
            InstanceSema.acquire();
        }
        catch (InterruptedException e)
        {
            LLog.e(e);
        }
        try
        {
            synchronized(selfRef)
            {
                rval = (null != selfRef[0]);
            }
        }
        finally {
            InstanceSema.release();
        }
        
        return rval;
    }
    
    public static ClipboardMonitor getInstance()
    {
        ClipboardMonitor rval = selfRef[0];
        
        if (null == rval)
        {
            try
            {
                InstanceSema.acquire();
            }
            catch (InterruptedException e)
            {
                LLog.e(e);
            }
            try
            {
                synchronized(selfRef)
                {
                    rval = selfRef[0];
                    if (null == rval)
                    {
                        rval = new ClipboardMonitor();
                        selfRef[0] = rval;
                    }
                }
            }
            finally {
                InstanceSema.release();
            }
        }
        
        return rval;
    }
    
    @Override
    public void lostOwnership(Clipboard arg0, Transferable arg1)
    {
        notificationSema.drainPermits();
        try
        {
            syncSema.acquire();
        }
        catch (InterruptedException e)
        {
            LLog.e(e);
        }
        try
        {
            changed = Boolean.TRUE;
        }
        finally {
            syncSema.release();
        }
        notificationSema.release();
    }
    
    @Override
    public void run()
    {
        boolean changed = Boolean.FALSE;
        ArrayList<ClipboardListener> deadList = new ArrayList<ClipboardListener>();
        
        while (!disposed)
        {
            // Gain clipboard ownership so that change notifications can take place again
            // Also secure the contents for internal processing through listeners
            
            while (!disposed)
            {
                try
                {
                    clipboard.setContents(clipboard.getContents(this), this);
                    break;
                }
                catch (Exception e)
                {
                    LLog.e(e, Boolean.FALSE);
                }
                
                try
                {
                    Thread.sleep(delay_ms);
                }
                catch (InterruptedException e)
                {
                    LLog.e(e);
                }
            }
            
            if (this.changed)
            {
                try
                {
                    syncSema.acquire();
                }
                catch (InterruptedException e)
                {
                    LLog.e(e);
                }
                try
                {
                    changed = this.changed;
                    this.changed = Boolean.FALSE;
                }
                finally {
                    syncSema.release();
                }
            }
            
            if (changed)
            {
                changed = Boolean.FALSE;
                
                for(ClipboardListener l : listeners)
                {
                    try
                    {
                        l.onChange(clipboard);
                    }
                    catch (Exception e)
                    {
                        deadList.add(l);
                        LLog.e(e, Boolean.FALSE);
                    }
                }
                
                if (!deadList.isEmpty())
                {
                    for (ClipboardListener l : deadList)
                    {
                        removeListener(l);
                    }
                    deadList.clear();
                }
            }
            
            try
            {
                notificationSema.acquire();
            }
            catch (InterruptedException e)
            {
                LLog.e(e);
            }
        }
    }
    
    public void dispose()
    {
        boolean wasToggled;
        
        synchronized(this)
        {
            wasToggled = (Boolean.FALSE == disposed);
            disposed = Boolean.TRUE;
        }
        
        if (wasToggled)
        {
            try
            {
                join();
            }
            catch (InterruptedException e)
            {
                LLog.e(e);
            }
        }
    }
    
    public void addListener(ClipboardListener l)
    {
        listeners.add(l);
    }
    
    public boolean removeListener(ClipboardListener l)
    {
        return listeners.remove(l);
    }
}
