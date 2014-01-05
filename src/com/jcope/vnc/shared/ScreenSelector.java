package com.jcope.vnc.shared;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagLayout;
import java.awt.Rectangle;
import java.awt.event.WindowEvent;
import java.util.ArrayList;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import com.jcope.debug.LLog;

public class ScreenSelector
{
    public static final Color transparent = new Color(0, true);
    
    public static GraphicsDevice selectScreen(JFrame parent, Integer defaultSelection)
    {
        GraphicsDevice rval = null;
        
        GraphicsDevice[] devices = GraphicsEnvironment
                .getLocalGraphicsEnvironment().getScreenDevices();
        ArrayList<JDialog> enumFrames = new ArrayList<JDialog>(devices.length);
        
        if (devices.length == 1)
        {
            return devices[0];
        }
        
        try
        {
            
            JDialog lastJFrame = null;
            
            for (int i=0; i<devices.length; i++)
            {
                GraphicsDevice device = devices[i];
                JDialog enumFrame = new JDialog(parent, false);
                enumFrame.setUndecorated(true);
                enumFrame.setBackground(transparent);
                
                JPanel transparentPanel = new JPanel();
                transparentPanel.setBackground(transparent);
                transparentPanel.setLayout(new GridBagLayout());
                transparentPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
                enumFrame.setContentPane(transparentPanel);
                
                Rectangle bounds = device.getDefaultConfiguration().getBounds();
                final Dimension size = new Dimension(bounds.width, bounds.height);
                
                final String enumMsg = ((Integer) ( i )).toString();
                final boolean[] maximized = new boolean[]{false};
                JLabel enumLabel = new JLabel(enumMsg) {
                    
                    // Generated: serialVersionUID
                    private static final long serialVersionUID = 3781269942421259076L;

                    @Override
                    protected void paintComponent(Graphics g)
                    {
                        if (!maximized[0])
                        {
                            maximized[0] = true;
                            Font maxFont = getMaximumFontForBounds(g, enumMsg, size, getFont());
                            setFont(maxFont);
                        }
                        super.paintComponent(g);
                    }
                };
                enumLabel.setOpaque(false);
                transparentPanel.add(enumLabel);
                
                enumFrame.setLocation(bounds.x, bounds.y);
                enumFrame.setPreferredSize(size);
                enumFrame.pack();
                enumFrame.setVisible(true);
                
                enumFrames.add(enumFrame);
                lastJFrame = enumFrame;
            }
            
            Integer[] selections = new Integer[devices.length];
            for (int i=0; i<selections.length; i++)
            {
                selections[i] = (Integer) i;
            }
            Integer selection = (Integer) JOptionPane.showInputDialog(lastJFrame,
                    "Choose Display Device",
                    "Choose Display Device", JOptionPane.PLAIN_MESSAGE,
                    null, selections, null);
            
            if (selection == null)
            {
                selection = (defaultSelection == null) ? 0 : defaultSelection;
            }
            
            if (selection >= 0 && selection < devices.length)
            {
                rval = devices[selection];
            }
        }
        finally {
            ArrayList<Exception> exceptions = new ArrayList<Exception>();
            for (JDialog enumFrame : enumFrames)
            {
                try
                {
                    enumFrame.setVisible(false);
                    enumFrame.dispatchEvent(new WindowEvent(enumFrame, WindowEvent.WINDOW_CLOSING));
                }
                catch (Exception e)
                {
                    exceptions.add(e);
                }
            }
            if (exceptions.size() > 0)
            {
                for (Exception e : exceptions)
                {
                    LLog.e(e, false);
                }
                LLog.e(exceptions.get(0));
            }
        }
        
        return rval;
    }
    
    private static Font getMaximumFontForBounds(Graphics g, String msg, Dimension size,
            Font font)
    {
        String fontName = font.getFontName();
        int fontStyle = font.getStyle();
        int curSize = font.getSize();
        FontMetrics m;
        
        int nextSize = curSize + 1;
        
        while ((m = g.getFontMetrics(new Font(fontName, fontStyle, nextSize))).getHeight() < size.height && m.stringWidth(msg) < size.width)
        {
            curSize = nextSize;
            nextSize++;
            if (nextSize < curSize)
            {
                break;
            }
        }
        
        return new Font(fontName, fontStyle, curSize);
    }
}
