package com.jcope.vnc.client.input.handle;

import static com.jcope.debug.Debug.assert_;
import static com.jcope.vnc.client.input.handle.ScreenSegmentUpdate.handleSetSegmentPixels;

import com.jcope.vnc.client.StateMachine;
import com.jcope.vnc.shared.input.Handle;

public class EntireScreenUpdate extends Handle<StateMachine>
{
    public EntireScreenUpdate()
    {
        super(StateMachine.class);
    }
    
    @Override
    public void handle(StateMachine stateMachine, Object[] args)
    {
        assert_(args != null);
        assert_(args.length == 1);
        assert_(args[0] instanceof int[]);
        
        final int[] pixels = (int[]) args[0];
        handleSetSegmentPixels(stateMachine, -1, pixels);
    }
}
