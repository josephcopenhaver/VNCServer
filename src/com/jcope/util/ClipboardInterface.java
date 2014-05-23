package com.jcope.util;

import static com.jcope.debug.Debug.assert_;
import static com.jcope.util.Platform.PLATFORM_IS_MAC;

import java.awt.HeadlessException;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.Semaphore;

import javax.imageio.ImageIO;

import com.jcope.debug.LLog;

public class ClipboardInterface
{
    public static final class ClipboardBusyException extends Exception {

        /**
         * Generated serialVersionUID
         */
        private static final long serialVersionUID = 2972115936781692681L;
        
        ClipboardBusyException(IllegalStateException e)
        {
            super((Throwable) e);
        }
        
    }
    
    public static boolean isDataFlavorAvailable(Clipboard clipboard, DataFlavor flavor) throws ClipboardBusyException
    {
        try
        {
            return clipboard.isDataFlavorAvailable(flavor);
        }
        catch(IllegalStateException e)
        {
            throw new ClipboardBusyException(e);
        }
    }
    
    public static DataFlavor[] getAvailableDataFlavors(Clipboard clipboard) throws ClipboardBusyException
    {
        try
        {
            return clipboard.getAvailableDataFlavors();
        }
        catch(IllegalStateException e)
        {
            throw new ClipboardBusyException(e);
        }
    }
    
    public static Object getData(Clipboard clipboard, DataFlavor flavor) throws ClipboardBusyException, UnsupportedFlavorException, IOException
    {
        try
        {
            return clipboard.getData(flavor);
        }
        catch(IllegalStateException e)
        {
            throw new ClipboardBusyException(e);
        }
    }
    
    public static Transferable getContents(Clipboard clipboard, Object requestor) throws ClipboardBusyException
    {
        try
        {
            return clipboard.getContents(requestor);
        }
        catch(IllegalStateException e)
        {
            throw new ClipboardBusyException(e);
        }
    }
    
    private static final String mac_imgToClipboardApplescript = "set this_picture to \"%s\"\n" +
            "tell application \"System Events\"\n" +
            "    tell application \"Preview\"\n" +
            "        activate\n" +
            "        do shell \"chmod +w \" & this_picture\n" +
            "        do shell \"open -a /Applications/Preview.app \" & this_picture\n" +
            "    end tell\n" +
            "    tell process \"Preview\"\n" +
            "        keystroke \"a\" using command down\n" +
            "        keystroke \"c\" using command down\n" +
            "        keystroke \"w\" using command down\n" +
            "        do shell script \"rm \" & this_picture\n" +
            "    end tell\n" +
            "end tell\n";
    
    public static void setContents(Clipboard clipboard, Transferable contents, ClipboardOwner owner) throws ClipboardBusyException
    {
        if (!PLATFORM_IS_MAC)
        {
            try
            {
                clipboard.setContents(contents, owner);
            }
            catch(IllegalStateException e)
            {
                throw new ClipboardBusyException(e);
            }
        }
        else
        {
            if (contents.isDataFlavorSupported(DataFlavor.stringFlavor))
            {
                File file = null;
                FileWriter fw = null;
                BufferedWriter bw = null;
                try
                {
                    
                    file = TempFileFactory.get("pbcopy_text_input__", ".txt");
                    bw = new BufferedWriter(fw = new FileWriter(file));
                    bw.write((String) contents.getTransferData(DataFlavor.stringFlavor));
                    bw.flush();
                    bw.close();
                    
                    Process p = Runtime.getRuntime().exec(new String[] {"sh", "-c", String.format("cat %s | pbcopy >~/git/tmp.txt 2>&1", file.getAbsolutePath())});
                    p.waitFor();
                }
                catch (IOException e)
                {
                    LLog.e(e, false);
                }
                catch (InterruptedException e)
                {
                    LLog.e(e);
                }
                catch (UnsupportedFlavorException e)
                {
                    LLog.e(e);
                }
                finally {
                    if (bw != null) try { bw.close(); } catch (IOException e) {}
                    if (fw != null) try { fw.close(); } catch (IOException e) {}
                    if (file != null) try { file.delete(); } catch (SecurityException e) {}
                }
            }
            else if (contents.isDataFlavorSupported(DataFlavor.imageFlavor))
            {
                File file = null;
                try
                {
                    file = TempFileFactory.get("osascript_image_input__", ".png");
                    String escapedFilePath = file.getAbsolutePath().replaceAll("\\\\", "\\\\").replaceAll("\"", "\\\"");
                    String[] macCmd = new String[]{"osascript", "-e", String.format(mac_imgToClipboardApplescript, escapedFilePath)};
                    BufferedImage image = (BufferedImage) contents.getTransferData(DataFlavor.imageFlavor);
                    ImageIO.write(image, "png", file);
                    
                    Process p = Runtime.getRuntime().exec(macCmd);
                    p.waitFor();
                }
                catch (IOException e)
                {
                    LLog.e(e, false);
                }
                catch (UnsupportedFlavorException e)
                {
                    LLog.e(e);
                }
                catch (InterruptedException e)
                {
                    LLog.e(e);
                }
            }
            else
            {
                throw new RuntimeException("No supported dataflavor for mac");
            }
        }
    }
    
    private static final Semaphore lockSema = new Semaphore(1, Boolean.TRUE);
    
    public static class ImageSelection implements Transferable
    {
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
            return new DataFlavor[] { DataFlavor.imageFlavor };
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
    
    private static void getFlavor(ArrayList<Object> pairList, DataFlavor k) throws IOException, ClipboardBusyException
    {
        Object v = null;
        
        try
        {
            v = getData(clipboard, k);
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
    
    public static Clipboard getClipboard()
    {
    	Clipboard rval = null;
    	
    	try
    	{
    		rval = Toolkit.getDefaultToolkit().getSystemClipboard();
    	}
    	catch (HeadlessException e)
    	{
    		LLog.e(new Exception("There is no clipboard here, STOP USING ME!", e), Boolean.TRUE, Boolean.TRUE);
    	}
    	
		return rval;
    }
    
    private static void loadClipboard()
    {
    	clipboard = getClipboard();
    }
    
    public static Object[] get() throws IOException, ClipboardBusyException
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
    
    public static void set(Object[] contents) throws IOException, ClipboardBusyException
    {
        DataFlavor dataFlavor;
        Transferable transferable;
        ClipboardMonitor clipboardMonitor;
        
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
            
            if (ClipboardMonitor.hasInstance())
            {
                clipboardMonitor = ClipboardMonitor.getInstance();
            }
            else
            {
                clipboardMonitor = null;
            }
            
            setContents(clipboard, transferable, clipboardMonitor);
            
            if (null != clipboardMonitor)
            {
                clipboardMonitor.syncObserverCache();
            }
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
