package com.jcope.util;

import static com.jcope.debug.Debug.assert_;

import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.Semaphore;

import javax.imageio.ImageIO;

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

        public ImageSelection(byte[] src) throws IOException
        {
            image = ImageIO.read(new ByteArrayInputStream(src));
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
    
    public static Iterator<DataFlavor> getSupportedFlavorsIterator()
    {
        return new Iterator<DataFlavor>() {
            
            int idx = 0;

            @Override
            public boolean hasNext()
            {
                return (idx < supportedFlavors.length);
            }

            @Override
            public DataFlavor next()
            {
                return supportedFlavors[idx++];
            }

            @Override
            public void remove()
            {
                throw new UnsupportedOperationException();
            }
            
        };
    }
    
    public static boolean isFlavorSupported(DataFlavor flavor)
    {
        boolean rval = Boolean.FALSE;
        
        for (DataFlavor sFlavor : supportedFlavors)
        {
            if (sFlavor.equals(flavor))
            {
                rval = Boolean.TRUE;
                break;
            }
        }
        
        return rval;
    }
    
    private static Clipboard clipboard = null;
    
    private static void getFlavor(ArrayList<Object> pairList, DataFlavor k) throws IOException
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
            // Make objects transferable
            
            if (k.equals(DataFlavor.imageFlavor))
            {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write((BufferedImage) v, "png", baos);
                
                v = baos.toByteArray();
            }
            
            pairList.add(k);
            pairList.add(v);
        }
    }
    
    private static void loadClipboard()
    {
        clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
    }
    
    public static Object[] get() throws IOException
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
    
    public static void set(Object[] contents) throws IOException
    {
        DataFlavor dataFlavor;
        Transferable transferable;
        
        loadClipboard();
        try
        {
            dataFlavor = (DataFlavor) contents[0];
            
            if (dataFlavor.equals(DataFlavor.imageFlavor))
            {
                transferable = new ImageSelection((byte[]) contents[1]);
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
