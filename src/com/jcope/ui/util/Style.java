package com.jcope.ui.util;

import java.awt.Container;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

import javax.swing.JDialog;
import javax.swing.JFrame;

public class Style
{
    public static void positionThenShow(JFrame frame)
    {
        positionThenShow(frame, Boolean.TRUE);
    }
    
    public static void positionThenShow(JFrame frame, boolean packBeforeShow)
    {
        //GraphicsDevice graphicsDevice;
        //GraphicsConfiguration graphicsConfiguration;
        Rectangle gcBounds = null;
        Insets insets = null;
        Point point;
        
        frame.setVisible(Boolean.TRUE);
        point = frame.getLocationOnScreen();
        frame.setVisible(Boolean.FALSE);
        
        for (GraphicsDevice gDevice : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices())
        { 
            for (GraphicsConfiguration gConfig : gDevice.getConfigurations())
            {
                Rectangle bounds = gConfig.getBounds();

                if (bounds.contains(point))
                {
                    gcBounds = bounds;
                    insets = Toolkit.getDefaultToolkit().getScreenInsets(gConfig);
                    //graphicsConfiguration = gConfig;
                    //graphicsDevice = gDevice;
                    break;
                }
            }
        }
        
        Dimension preferredSize = frame.getPreferredSize();
        
        if (gcBounds != null)
        {
            // center the frame in the graphics device
            int w = gcBounds.width - insets.left - insets.right,
                h = gcBounds.height - insets.top - insets.bottom;
            preferredSize.setSize((preferredSize.width > w ? w : preferredSize.width), (preferredSize.height > h ? h : preferredSize.height));
        }
        
        frame.setSize(preferredSize);
        frame.setLocation(((int)gcBounds.getMinX()) + insets.left + (gcBounds.width - preferredSize.width)/2, ((int)gcBounds.getMinY()) + insets.top + (gcBounds.height - preferredSize.height)/2);
        
        if (packBeforeShow)
        {
            frame.pack();
        }
        
        frame.setVisible(Boolean.TRUE);
    }

    public static void positionRelativeToParentWhenShown(JDialog dialog)
    {
        dialog.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent evt)
            {
                JDialog dialog = (JDialog) evt.getSource();
                try
                {
                    Container parent = dialog.getParent();
                    dialog.setLocationRelativeTo(parent);
                }
                finally {
                    dialog.removeComponentListener(this);
                }
            }
        });
    }
}
