package com.jcope.vnc.client.input.handle;

import static com.jcope.debug.Debug.assert_;

import com.jcope.vnc.client.StateMachine;
import com.jcope.vnc.shared.StateMachine.CLIENT_EVENT;
import com.jcope.vnc.shared.StateMachine.SERVER_EVENT;
import com.jcope.vnc.shared.input.Handle;

public class ScreenSegmentChanged extends Handle<StateMachine>
{
    public ScreenSegmentChanged()
    {
        super(StateMachine.class);
    }
    
    @Override
    public void handle(StateMachine stateMachine, Object[] args)
    {
        assert_(args != null);
        assert_(args.length == 1);
        assert_(args[0] instanceof Integer);
        int segmentID = (Integer) args[0];
        assert_(segmentID >= -1);
        
        try
        {
            stateMachine.sendEvent(CLIENT_EVENT.GET_SCREEN_SEGMENT, segmentID);
        }
        finally {
            stateMachine.sendEvent(CLIENT_EVENT.ACKNOWLEDGE_NON_SERIAL_EVENT, SERVER_EVENT.SCREEN_SEGMENT_CHANGED, segmentID);
        }
    }
}
