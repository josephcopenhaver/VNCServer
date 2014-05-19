package com.jcope.vnc.client.input.handle;

import static com.jcope.debug.Debug.assert_;

import com.jcope.util.ClipboardInterface;
import com.jcope.vnc.client.StateMachine;
import com.jcope.vnc.client.input.Handle;

public class SetClipboard extends Handle
{
    
    @Override
    public void handle(StateMachine stateMachine, Object[] args)
    {
        assert_(null != args);
        assert_(1 == args.length);
        assert_(args[0] instanceof Object[]);
        
        if (!stateMachine.doSyncClipboard())
        {
            return;
        }
        
        ClipboardInterface.set((Object[]) args[0]);
    }
    
}
