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
            return;
        }
        
        Object[] clipboardContents = null;
        
        ClipboardInterface.lock();
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
        
        if (null != clipboardContents)
        {
            client.sendEvent(SERVER_EVENT.SET_CLIPBOARD, clipboardContents);
        }
    }
    
}
