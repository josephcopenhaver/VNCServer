package com.jcope.vnc.client.input.handle;

import static com.jcope.debug.Debug.assert_;

import com.jcope.ui.ImagePanel;
import com.jcope.vnc.client.MainFrame;
import com.jcope.vnc.client.StateMachine;
import com.jcope.vnc.client.input.Handle;

public class ScreenResized extends Handle
{
    
    @Override
    public void handle(StateMachine stateMachine, Object[] args)
    {
        assert_(args != null);
        assert_(args.length == 2);
        assert_(args[0] instanceof Integer);
        assert_(args[1] instanceof Integer);
        
        int newWidth = (Integer) args[0];
        int newHeight = (Integer) args[1];
        
        MainFrame frame = stateMachine.getFrame();
        ImagePanel imagePanel = frame.getImagePanel();
        if (imagePanel == null || (imagePanel.getWidth() != newWidth || imagePanel.getHeight() != newHeight))
        {
            imagePanel = new ImagePanel(newWidth, newHeight);
            frame.setImagePanel(imagePanel);
            frame.invalidate();
            frame.repaint();
        }
        else if (imagePanel != null)
        {
            frame.onReconnect();
        }
    }
    
}
