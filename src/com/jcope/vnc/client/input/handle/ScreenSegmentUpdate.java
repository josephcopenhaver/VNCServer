package com.jcope.vnc.client.input.handle;

import static com.jcope.debug.Debug.assert_;

import com.jcope.ui.ImagePanel;
import com.jcope.vnc.client.MainFrame;
import com.jcope.vnc.client.StateMachine;
import com.jcope.vnc.shared.input.Handle;

public class ScreenSegmentUpdate extends Handle<StateMachine>
{
    public ScreenSegmentUpdate()
    {
        super(StateMachine.class);
    }
    
    @Override
    public void handle(StateMachine stateMachine, Object[] args)
    {
        assert_(args != null);
        assert_(args.length == 2);
        assert_(args[0] instanceof Integer);
        
        MainFrame frame = stateMachine.getFrame();
        ImagePanel imagePanel = frame.getImagePanel();
        
        int segmentID = (Integer) args[0];
        assert_(segmentID >= -1);
        
        if (args[1] instanceof int[])
        {
            int[] pixels = (int[]) args[1];
            assert_(pixels != null);
            
            imagePanel.setSegmentPixels(segmentID, pixels);
        }
        else if (args[1] instanceof Integer)
        {
            Integer solidPixelColor = (Integer) args[1];
            assert_(solidPixelColor != null);
            
            imagePanel.setSegmentSolidColor(segmentID, solidPixelColor);
        }
        else
        {
            assert_(false);
        }
    }
}
