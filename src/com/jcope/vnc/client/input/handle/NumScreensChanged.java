package com.jcope.vnc.client.input.handle;

import static com.jcope.debug.Debug.assert_;

import com.jcope.vnc.client.StateMachine;
import com.jcope.vnc.client.input.Handle;

public class NumScreensChanged extends Handle
{
    
    @Override
    public void handle(StateMachine stateMachine, Object[] args)
    {
        assert_(args != null);
        assert_(args.length == 1);
        assert_(args[0] instanceof Integer);
        
        // TODO: do something with this inforamtion?
    }
    
}
