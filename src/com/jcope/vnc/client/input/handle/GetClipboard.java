package com.jcope.vnc.client.input.handle;

import static com.jcope.debug.Debug.assert_;

import java.io.IOException;

import com.jcope.debug.LLog;
import com.jcope.util.ClipboardInterface;
import com.jcope.util.ClipboardInterface.ClipboardBusyException;
import com.jcope.vnc.client.StateMachine;
import com.jcope.vnc.client.input.Handle;
import com.jcope.vnc.shared.StateMachine.CLIENT_EVENT;

public class GetClipboard extends Handle
{
    
    @Override
    public void handle(StateMachine stateMachine, Object[] args)
    {
        assert_(null == args);
        
        if (!stateMachine.isClipboardSyncEnabled())
        {
            System.err.println("\nno clipboard sync for you!\n"); // TODO: remove
            return;
        }
        
        Object[] clipboardContents = null;
        
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
        
        if (clipboardContents == null)
        {
            System.err.println("\nno clipboard contents!\n"); // TODO: remove
            return;
        }
        
        System.err.println("\nforwarding clipboard contents...\n"); // TODO: remove
        stateMachine.sendEvent(CLIENT_EVENT.SET_CLIPBOARD, clipboardContents);
    }
    
}
