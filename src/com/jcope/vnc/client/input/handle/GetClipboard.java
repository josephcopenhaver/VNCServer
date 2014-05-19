package com.jcope.vnc.client.input.handle;

import static com.jcope.debug.Debug.assert_;

import com.jcope.util.ClipboardInterface;
import com.jcope.vnc.client.StateMachine;
import com.jcope.vnc.client.input.Handle;
import com.jcope.vnc.shared.StateMachine.CLIENT_EVENT;

public class GetClipboard extends Handle
{
    
    @Override
    public void handle(StateMachine stateMachine, Object[] args)
    {
        assert_(null == args);
        
        if (!stateMachine.doSyncClipboard())
        {
            return;
        }
        
        Object[] clipboardContents = ClipboardInterface.get();
        
        if (clipboardContents == null)
        {
            return;
        }
        
        stateMachine.sendEvent(CLIENT_EVENT.SET_CLIPBOARD, clipboardContents);
    }
    
}
