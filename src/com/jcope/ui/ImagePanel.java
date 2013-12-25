package com.jcope.ui;

import java.awt.Graphics;
import java.awt.image.BufferedImage;

import javax.swing.JPanel;

import com.jcope.util.SegmentationInfo;

public class ImagePanel extends JPanel
{
    
    // Generated: serialVersionUID
    private static final long serialVersionUID = -4538018101380490678L;
    
    private BufferedImage image;
    private SegmentationInfo segInfo;
    
    public ImagePanel(int width, int height)
    {
        image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        segInfo = new SegmentationInfo();
    }
    
    @Override
    protected void paintComponent(Graphics g)
    {
        super.paintComponent(g);
        g.drawImage(image, 0, 0, null);
    }
    
    private void loadScreenPixels(int[] pixels)
    {
        int screenWidth = image.getWidth();
        int screenHeight = image.getHeight();
        image.setRGB(0, 0, screenWidth, screenHeight, pixels, 0, screenWidth);
        repaint();
    }
    
    public void setSegmentPixels(int segmentID, int[] pixels)
    {
        if (segmentID == -1)
        {
            loadScreenPixels(pixels);
            return;
        }
        
        int[] tmp = new int[2];
        int startX, startY;
        segInfo.getPos(segmentID, tmp);
        startX = tmp[0];
        startY = tmp[1];
        segInfo.getDim(segmentID, tmp);
        // assert_(tmp[0] * tmp[1] == pixels.length);
        
        
        image.setRGB(startX, startY, tmp[0], tmp[1], pixels, 0, tmp[0]);
        
        repaint(startX, startY, tmp[0], tmp[1]);
    }

    public void setSegmentSize(int segmentWidth, int segmentHeight)
    {
        int screenWidth = image.getWidth();
        int screenHeight = image.getHeight();
        segInfo.loadConfig(screenWidth, screenHeight, segmentWidth, segmentHeight);
    }
    
}
