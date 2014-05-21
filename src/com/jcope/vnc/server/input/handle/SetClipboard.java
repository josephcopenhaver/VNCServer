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
    private ClipboardMonitor clipboardMonitor = null;
    
    @Override
    public void handle(ClientHandler client, Object[] args)
    {
        assert_(null != args);
        assert_(args.length > 0);
        assert_(args.length % 2 == 0);
        
        if (!((Boolean)Server.SERVER_PROPERTIES.SUPPORT_CLIPBOARD_SYNCHRONIZATION.getValue()))
        {
            return;
        }
        
        if (null == clipboardMonitor)
        {
            clipboardMonitor = ClipboardMonitor.getInstance();
        }
        
        boolean forwardChangeNotice = Boolean.FALSE;
        
        ClipboardInterface.lock();
        try
        {
            clipboardMonitor.lockAndPause();
            try
            {
                ClipboardInterface.set(args);
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
            }
        }
        finally {
            ClipboardInterface.unlock();
        }
        
        // Notify all OTHER clients of the clipboard contents change
        if (forwardChangeNotice)
        {
            Manager.getInstance().sendToAllExcept(client, SERVER_EVENT.CLIPBOARD_CHANGED, (Object[]) null);
        }
    }
    
}
