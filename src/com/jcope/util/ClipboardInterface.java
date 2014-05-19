package com.jcope.util;

import static com.jcope.debug.Debug.assert_;

import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;

import com.jcope.debug.LLog;

public class ClipboardInterface
{
    private static final Semaphore lockSema = new Semaphore(1, Boolean.TRUE);
    
    public static class ImageSelection implements Transferable
    {
        private static final DataFlavor[] transferDataFlavors = new DataFlavor[]{
            DataFlavor.imageFlavor
        };
        
        private final Image image;

        public ImageSelection(Image image)
        {
            this.image = image;
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException
        {
            if (!DataFlavor.imageFlavor.equals(flavor))
            {
                throw new UnsupportedFlavorException(flavor);
            }
            
            return image;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors()
        {
            return transferDataFlavors;
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor)
        {
            return DataFlavor.imageFlavor.equals(flavor);
        }
    }
    
    private static final DataFlavor[] supportedFlavors = new DataFlavor[] {
        DataFlavor.stringFlavor,
        DataFlavor.imageFlavor
    };
    
    private static Clipboard clipboard = null;
    
    private static void getFlavor(ArrayList<Object> pairList, DataFlavor k)
    {
        Object v = null;
        
        try
        {
            v = clipboard.getData(k);
        }
        catch (UnsupportedFlavorException e)
        {
            // Do Nothing
        }
        catch (IOException e)
        {
            LLog.e(e, Boolean.FALSE);
        }
        
        if (null != v)
        {
            pairList.add(k);
            pairList.add(v);
        }
    }
    
    private static void loadClipboard()
    {
        clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
    }
    
    public static Object[] get()
    {
        loadClipboard();
        try
        {
            ArrayList<Object> pairList = new ArrayList<Object>();
            
            for (DataFlavor dataFlavor : supportedFlavors)
            {
                getFlavor(pairList, dataFlavor);
            }
            
            assert_(3 > pairList.size());
            
            return pairList.isEmpty() ? null : pairList.toArray();
        }
        finally {
            clipboard = null;
        }
    }
    
    public static void set(Object[] contents)
    {
        DataFlavor dataFlavor;
        Transferable transferable;
        
        loadClipboard();
        try
        {
            dataFlavor = (DataFlavor) contents[0];
            
            if (dataFlavor.equals(DataFlavor.imageFlavor))
            {
                transferable = new ImageSelection((Image) contents[1]);
            }
            else
            {
                transferable = new StringSelection((String) contents[1]);
            }
            
            clipboard.setContents(transferable, ClipboardMonitor.getInstance());
        }
        finally {
            clipboard = null;
        }
    }

    public static void lock()
    {
        try
        {
            lockSema.acquire();
        }
        catch (InterruptedException e)
        {
            LLog.e(e);
        }
    }

    public static void unlock()
    {
        lockSema.release();
    }
}
