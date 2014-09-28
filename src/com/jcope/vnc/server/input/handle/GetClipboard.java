package com.jcope.vnc.server.input.handle;

import static com.jcope.debug.Debug.assert_;

import java.io.IOException;

import com.jcope.debug.LLog;
import com.jcope.util.ClipboardInterface;
import com.jcope.util.ClipboardInterface.ClipboardBusyException;
import com.jcope.vnc.Server;
import com.jcope.vnc.server.ClientHandler;
import com.jcope.vnc.server.input.Handle;
import com.jcope.vnc.shared.StateMachine.SERVER_EVENT;

public class GetClipboard extends Handle
{
    
    @Override
    public void handle(ClientHandler client, Object[] args)
    {
        assert_(null == args);
        
        if (!((Boolean)Server.SERVER_PROPERTIES.SUPPORT_CLIPBOARD_SYNCHRONIZATION.getValue()))
        {
            System.err.println("\nno clipboard sync for you!\n"); // TODO: remove
            return;
        }
        
        Object[] clipboardContents = null;
        
        ClipboardInterface.lock();
        System.err.println("\nGot CIFace lock\n"); // TODO: remove
        try
        {
            clipboardContents = ClipboardInterface.get();
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
            ClipboardInterface.unlock();
        }
        System.err.println("\nCIFace lock removed\n"); // TODO: remove
        
        if (null != clipboardContents)
        {
            System.err.println("\nSending Set clipboard reply...\n"); // TODO: remove
            client.sendEvent(SERVER_EVENT.SET_CLIPBOARD, clipboardContents);
        }
        System.err.println("\nEnd of GetClipboard handle\n"); // TODO: remove
    }
    
}
