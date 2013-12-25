package com.jcope.ui;

import java.awt.Graphics;
import java.awt.image.BufferedImage;

import javax.swing.JPanel;

public class ImagePanel extends JPanel
{

    // Generated: serialVersionUID
    private static final long serialVersionUID = -4538018101380490678L;
    
    private BufferedImage image;
    private int segmentWidth;
    private int segmentHeight;
    
    public ImagePanel(int width, int height)
    {
        image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        segmentWidth = 0;
        segmentHeight = 0;
    }
    
    @Override
    protected void paintComponent(Graphics g)
    {
        super.paintComponent(g);
        g.drawImage(image, 0, 0, null);
    }
    
    private void loadScreenPixels(int[] pixels)
    {
        int width = image.getWidth();
        int height = image.getHeight();
        image.setRGB(0, 0, width, height, pixels, 0, width);
        repaint();
    }
    
    public void setSegmentPixels(int segmentID, int[] pixels)
    {
        if (segmentID == -1)
        {
            loadScreenPixels(pixels);
            return;
        }
        
        // TODO: utilize segmentWidth and segmentHeight
    }

    public void setSegmentSize(int segmentWidth, int segmentHeight)
    {
        this.segmentWidth = segmentWidth;
        this.segmentHeight = segmentHeight;
    }
    
}
