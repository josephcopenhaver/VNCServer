package com.jcope.vnc.client.input.handle;

import static com.jcope.debug.Debug.assert_;

import com.jcope.vnc.client.StateMachine;
import com.jcope.vnc.client.input.Handle;
import com.jcope.vnc.shared.StateMachine.CLIENT_EVENT;

public class ClipboardChanged extends Handle
{
    
    @Override
    public void handle(StateMachine stateMachine, Object[] args)
    {
        assert_(null == args);
        
        if (!stateMachine.doSyncClipboard())
        {
            return;
        }
        
        stateMachine.sendEvent(CLIENT_EVENT.GET_CLIPBOARD);
    }
    
}
