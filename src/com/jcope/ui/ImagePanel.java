package com.jcope.ui;

import static com.jcope.debug.Debug.assert_;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import javax.swing.JPanel;

import com.jcope.util.SegmentationInfo;

public class ImagePanel extends JPanel
{
    
    // Generated: serialVersionUID
    private static final long serialVersionUID = -4538018101380490678L;
    private static final int cursorSideLength = 32;
    private static final int halfCursorSideLength = cursorSideLength/2;
    private static final int invalRectOffset = 1;
    private int[] pixelsUnderCursor = new int[ (cursorSideLength + invalRectOffset) * (cursorSideLength + invalRectOffset) ];
    
    private BufferedImage image;
    private SegmentationInfo segInfo;
    
    private boolean cursorVisible = false;
    private Point cursorPosition = new Point();
    
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
        
        if (cursorVisible && SegmentationInfo.updateIntersection(pixelsUnderCursor, pixelsUnderCursorRect, pixels, 0, 0, screenWidth, screenHeight))
        {
            reshowCursor(cursorPosition.x, cursorPosition.y);
        }
        
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
        
        if (cursorVisible && SegmentationInfo.updateIntersection(pixelsUnderCursor, pixelsUnderCursorRect, pixels, startX, startY, tmp[0], tmp[1]))
        {
            reshowCursor(cursorPosition.x, cursorPosition.y);
        }
        
        repaint(startX, startY, tmp[0], tmp[1]);
    }

    public void setSegmentSize(int segmentWidth, int segmentHeight)
    {
        int screenWidth = image.getWidth();
        int screenHeight = image.getHeight();
        segInfo.loadConfig(screenWidth, screenHeight, segmentWidth, segmentHeight);
    }
    
    public void hideCursor()
    {
        if (cursorVisible)
        {
            cursorVisible = false;
            
            image.setRGB(pixelsUnderCursorRect.x, pixelsUnderCursorRect.y, pixelsUnderCursorRect.width, pixelsUnderCursorRect.height, pixelsUnderCursor, 0, pixelsUnderCursorRect.width);
            
            repaint(pixelsUnderCursorRect.x, pixelsUnderCursorRect.y, pixelsUnderCursorRect.width, pixelsUnderCursorRect.height);
        }
    }
    
    private Rectangle pixelsUnderCursorRect = new Rectangle();
    
    private void storePixelsUnderCursor(int x, int y)
    {
        int w = cursorSideLength + (x < 0 ? x : 0) + invalRectOffset;
        int h = cursorSideLength + (y < 0 ? y : 0) + invalRectOffset;
        
        if (x < 0)
        {
            x = 0;
        }
        if (y < 0)
        {
            y = 0;
        }
        
        if (x + w > image.getWidth())
        {
            w = image.getWidth() - x;
        }
        if (y + h > image.getHeight())
        {
            h = image.getHeight() - y;
        }
        
        assert_(x >= 0);
        assert_(y >= 0);
        assert_(w > 0);
        assert_(h > 0);
        
        pixelsUnderCursorRect.x = x;
        pixelsUnderCursorRect.y = y;
        pixelsUnderCursorRect.width = w;
        pixelsUnderCursorRect.height = h;
        
        image.getRGB(x, y, w, h, pixelsUnderCursor, 0, w);
    }
    
    private void drawCursor(int x, int y)
    {
        if (!cursorVisible)
        {
            cursorVisible = true;
            
            final int startX = x-halfCursorSideLength;
            final int endX = x+halfCursorSideLength;
            final int startY = y-halfCursorSideLength;
            final int endY = y+halfCursorSideLength;
            
            // store the old cursor before drawing
            storePixelsUnderCursor(startX, startY);
            
            final Graphics g = image.getGraphics();
            
            g.setColor(Color.BLACK);
            g.drawLine(startX, startY, endX, endY);
            g.drawLine(startX, endY, endX, startY);
            
            repaint(startX, startY, cursorSideLength, cursorSideLength);
            
            cursorPosition.x = x;
            cursorPosition.y = y;
        }
    }
    
    private void reshowCursor(int x, int y)
    {
        cursorPosition.x = -1;
        cursorPosition.y = -1;
        
        showCursor(x, y);
    }
    
    public void showCursor(int x, int y)
    {
        if (cursorVisible && x == cursorPosition.x && y == cursorPosition.y)
        {
            // no change
            return;
        }
        
        hideCursor();
        drawCursor(x, y);
    }
    
}
