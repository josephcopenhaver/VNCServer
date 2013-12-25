package com.jcope.vnc.client.input.handle;

import static com.jcope.debug.Debug.assert_;

import com.jcope.vnc.client.MainFrame;
import com.jcope.vnc.client.StateMachine;
import com.jcope.vnc.client.input.Handle;

public class ScreenSegmentSizeUpdate extends Handle
{
    
    @Override
    public void handle(StateMachine stateMachine, Object[] args)
    {
        assert_(args != null);
        assert_(args.length == 2);
        assert_(args[0] instanceof Integer);
        assert_(args[1] instanceof Integer);
        
        int width = (Integer) args[0];
        int height = (Integer) args[1];
        
        MainFrame frame = stateMachine.getFrame();
        frame.getImagePanel().setSegmentSize(width, height);
    }
    
}
