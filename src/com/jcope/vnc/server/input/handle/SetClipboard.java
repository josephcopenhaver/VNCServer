package com.jcope.vnc.server.input.handle;

import static com.jcope.debug.Debug.assert_;

import java.io.IOException;

import com.jcope.debug.LLog;
import com.jcope.util.ClipboardInterface;
import com.jcope.util.ClipboardInterface.ClipboardBusyException;
import com.jcope.util.ClipboardMonitor;
import com.jcope.vnc.Server;
import com.jcope.vnc.server.ClientHandler;
import com.jcope.vnc.server.input.Handle;
import com.jcope.vnc.server.screen.Manager;
import com.jcope.vnc.shared.StateMachine.SERVER_EVENT;

public class SetClipboard extends Handle
{
    @Override
    public void handle(ClientHandler client, Object[] args)
    {
        assert_(null != args);
        assert_(args.length > 0);
        assert_(args.length % 2 == 0);
        
        if (!((Boolean)Server.SERVER_PROPERTIES.SUPPORT_CLIPBOARD_SYNCHRONIZATION.getValue()))
        {
            System.err.println("\nno synchronization for you!\n"); // TODO: remove
            return;
        }
        
        ClipboardMonitor clipboardMonitor = ClipboardMonitor.getInstance();
        boolean forwardChangeNotice = Boolean.FALSE;
        
        ClipboardInterface.lock();
        System.err.println("\nGot CIFace lock\n"); // TODO: remove
        try
        {
            clipboardMonitor.lockAndPause();
            System.err.println("\nGot CM lock and paused\n"); // TODO: remove
            try
            {
                System.err.println("\nBEFORE ClipboardInterface.set(args)\n"); // TODO: remove
                ClipboardInterface.set(args);
                System.err.println("\nAFTER ClipboardInterface.set(args)\n"); // TODO: remove
                forwardChangeNotice = Boolean.TRUE;
            }
            catch (IOException e)
            {
                LLog.e(e, Boolean.FALSE);
            }
            catch (ClipboardBusyException e)
            {
                LLog.e(e, Boolean.FALSE);
            }
            finally {
                clipboardMonitor.unlockAndUnpause();
                System.err.println("\nCM lock released and unpaused\n"); // TODO: remove
            }
        }
        finally {
            ClipboardInterface.unlock();
        }
        
        // Notify all OTHER clients of the clipboard contents change
        System.err.println("\nCIFace lock removed\n"); // TODO: remove
        if (forwardChangeNotice)
        {
            System.err.println("\nForwarding...\n"); // TODO: remove
            Manager.getInstance().sendToAllExcept(client, SERVER_EVENT.CLIPBOARD_CHANGED, (Object[]) null);
        }
        System.err.println("\nEnd of SetClipboard input handle\n"); // TODO: remove
    }
    
}
