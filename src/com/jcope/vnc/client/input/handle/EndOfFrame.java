package com.jcope.vnc.client.input.handle;

import static com.jcope.debug.Debug.assert_;

import com.jcope.vnc.client.StateMachine;
import com.jcope.vnc.client.input.Handle;

public class EndOfFrame extends Handle
{
    
    @Override
    public void handle(final StateMachine stateMachine, Object[] args)
    {
        assert_(args == null);
        
        stateMachine.scheduleGUIAction(new Runnable() {

            @Override
            public void run() {
                stateMachine.flushFrameBuffer();
            }
            
        });
    }
    
}
