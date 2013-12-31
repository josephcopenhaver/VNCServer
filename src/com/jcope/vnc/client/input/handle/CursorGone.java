package com.jcope.vnc.client.input.handle;

import static com.jcope.debug.Debug.assert_;

import com.jcope.ui.ImagePanel;
import com.jcope.vnc.client.MainFrame;
import com.jcope.vnc.client.StateMachine;
import com.jcope.vnc.client.input.Handle;

public class CursorGone extends Handle
{
    
    @Override
    public void handle(StateMachine stateMachine, Object[] args)
    {
        assert_(args == null);
        
        MainFrame frame = stateMachine.getFrame();
        ImagePanel imagePanel = frame.getImagePanel();
        imagePanel.hideCursor();
    }
    
}
