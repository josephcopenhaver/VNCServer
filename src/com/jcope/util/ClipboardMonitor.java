package com.jcope.util;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.Semaphore;

import com.jcope.debug.LLog;

public class ClipboardMonitor extends Thread implements ClipboardOwner
{
    public static interface ClipboardListener
    {
        public void onChange(Clipboard clipboard);
    }
    
    private static final long delay_ms = 200L;
    private static final long mac_observer_ms = 400L;
    private static Clipboard clipboard;  
    private static ArrayList<ClipboardListener> listeners;
    private static final ClipboardMonitor[] selfRef = new ClipboardMonitor[]{null};
    private static final Semaphore instanceSema = new Semaphore(1, Boolean.TRUE);
    
    private volatile boolean enabled;
    private volatile boolean disposed;
    private Semaphore notificationSema;
    private volatile boolean changed;
    private Thread macClipboardChangeDetector;
    
    private ClipboardMonitor()
    {
        // static
        clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        listeners = new ArrayList<ClipboardListener>(1);
        
        // instance member initialization
        enabled = Boolean.TRUE;
        disposed = Boolean.FALSE;
        notificationSema = new Semaphore(0, Boolean.TRUE);
        changed = Boolean.FALSE;
        
        if (Platform.isMac())
        {
            macClipboardChangeDetector = new Thread() {
                
                private HashMap<DataFlavor, Object> cache = new HashMap<DataFlavor, Object>();
                
                private boolean isDataMatch(DataFlavor flavor) throws UnsupportedFlavorException, IOException
                {
                    Object cObj = cache.get(flavor);
                    Object obj = clipboard.getData(flavor);
                    
                    boolean rval = (null == obj && null == cObj);
                    
                    if (!rval && null != obj && null != cObj)
                    {
                        rval = obj.equals(cObj);
                    }
                    
                    return rval;
                }
                
                private void cacheSupportedData() throws UnsupportedFlavorException, IOException
                {
                    HashMap<DataFlavor, Object> cache = new HashMap<DataFlavor, Object>();
                    Iterator<DataFlavor> flavors = ClipboardInterface.getSupportedFlavorsIterator();
                    DataFlavor flavor;
                    
                    while (flavors.hasNext())
                    {
                        flavor = flavors.next();
                        if (clipboard.isDataFlavorAvailable(flavor))
                        {
                            cache.put(flavor, clipboard.getData(flavor));
                        }
                    }
                    
                    this.cache = cache;
                }
                
                @Override
                public void run()
                {
                    DataFlavor[] prevFlavors = null;
                    DataFlavor[] flavors;
                    DataFlavor flavor;
                    boolean fire;
                    
                    while (!disposed)
                    {
                        flavors = null;
                        fire = Boolean.TRUE;
                        
                        try
                        {
                            flavors = clipboard.getAvailableDataFlavors();
                            
                            something_changed:
                            do
                            {
                                if (null == prevFlavors && null != flavors)
                                {
                                    break;
                                }
                                else if (null != prevFlavors && null != flavors)
                                {
                                    if (prevFlavors.length != flavors.length)
                                    {
                                        break;
                                    }
                                    
                                    for (int i=0; i<prevFlavors.length; i++)
                                    {
                                        flavor = flavors[i];
                                        if (!prevFlavors[i].equals(flavor))
                                        {
                                            flavor = null;
                                            break something_changed;
                                        }
                                        if (ClipboardInterface.isFlavorSupported(flavor) && !isDataMatch(flavor))
                                        {
                                            flavor = null;
                                            break something_changed;
                                        }
                                    }
                                }
                                
                                // No Change
                                flavors = prevFlavors;
                                fire = Boolean.FALSE;
                                
                            } while (Boolean.FALSE);
                            
                            if (fire)
                            {
                                cacheSupportedData();
                            }
                            
                            prevFlavors = flavors;
                        }
                        catch (Exception e)
                        {
                            LLog.e(e, Boolean.FALSE);
                            fire = Boolean.FALSE;
                        }
                        
                        if (fire)
                        {
                            fireChangeNotification();
                        }
                        
                        try
                        {
                            Thread.sleep(mac_observer_ms);
                        }
                        catch (InterruptedException e)
                        {
                            LLog.e(e);
                        }
                    }
                }
                
            };
            macClipboardChangeDetector.setName("Mac Clipboard Observer");
            macClipboardChangeDetector.setDaemon(Boolean.TRUE);
            macClipboardChangeDetector.setPriority(NORM_PRIORITY);
            macClipboardChangeDetector.start();
        }
        else
        {
            macClipboardChangeDetector = null;
        }

        // instance config
        setName("Clipboard Monitor");
        setDaemon(Boolean.TRUE);
        setPriority(NORM_PRIORITY);
        start();
    }
    
    public static boolean hasInstance()
    {
        boolean rval;
        
        synchronized(selfRef)
        {
            rval = (null != selfRef[0]);
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
                instanceSema.acquire();
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
                instanceSema.release();
            }
        }
        
        return rval;
    }
    
    private void fireChangeNotification()
    {
        notificationSema.drainPermits();
        changed = Boolean.TRUE;
        notificationSema.release();
    }
    
    @Override
    public void lostOwnership(Clipboard clipboard, Transferable transferable)
    {
        if (Platform.isMac())
        {
            LLog.w("As of 5/19/2014, MAC does not broadcast clipboard ownership changes, so why is this logged?");
            return;
        }
        fireChangeNotification();
    }
    
    @Override
    public void run()
    {
        while (!disposed)
        {
            // Gain clipboard ownership so that change notifications can take place again
            // Also secure the contents for internal processing through listeners
            
            do
            {
                try
                {
                    clipboard.setContents(clipboard.getContents(null), this);
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
            } while (!disposed);
            
            if (changed)
            {
                changed = Boolean.FALSE;
                
                if (!enabled)
                {
                    continue;
                }
                
                for(ClipboardListener l : listeners)
                {
                    if (changed)
                    {
                        break;
                    }
                    
                    try
                    {
                        l.onChange(clipboard);
                    }
                    catch (Exception e)
                    {
                        LLog.e(e, Boolean.FALSE);
                    }
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
        boolean wasNotRunning;
        
        synchronized(this)
        {
            wasNotRunning = disposed;
            disposed = Boolean.TRUE;
        }
        
        if (!wasNotRunning)
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

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }
}
