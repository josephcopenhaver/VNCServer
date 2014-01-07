package com.jcope.ui;

import static com.jcope.debug.Debug.assert_;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import javax.swing.JPanel;

import com.jcope.util.DimensionF;
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
    private volatile boolean isScaling = false;
    private Point cursorPosition = new Point();
    private Dimension preferredSize = new Dimension();
    private Dimension preferredSizeWithOffsets = new Dimension();
    
    private DimensionF scaleFactors = new DimensionF(1.0f, 1.0f);
    private volatile BufferedImage scaledImageCache = null;
    private Rectangle pixelsUnderCursorRect = new Rectangle();
    private volatile int offX=0;
    private volatile int offY=0;
    
    public ImagePanel(int width, int height)
    {
        setBackground(Color.BLACK);
        image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        segInfo = new SegmentationInfo();
        preferredSize.width = width;
        preferredSize.height = height;
        syncPreferredSize();
    }
    
    @Override
    protected void paintComponent(Graphics g)
    {
        super.paintComponent(g);
        if (isScaling)
        {
            if (scaledImageCache != null)
            {
                g.drawImage(scaledImageCache, offX, offY, null);
            }
        }
        else
        {
            g.drawImage(image, offX, offY, null);
        }
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

    public void setSegmentSolidColor(int segmentID, int solidPixelColor)
    {
        assert_(segmentID >= 0);
        
        int[] tmp = new int[2];
        int startX, startY, endX, endY;
        segInfo.getPos(segmentID, tmp);
        startX = tmp[0];
        startY = tmp[1];
        segInfo.getDim(segmentID, tmp);
        // assert_(tmp[0] * tmp[1] == pixels.length);
        
        endX = startX + tmp[0];
        endY = startY + tmp[1];
        
        for (int x=startX; x<endX; x++)
        {
            for (int y=startY; y<endY; y++)
            {
                image.setRGB(x, y, solidPixelColor);
            }
        }
        
        if (cursorVisible && SegmentationInfo.updateIntersection(pixelsUnderCursor, pixelsUnderCursorRect, solidPixelColor, startX, startY, tmp[0], tmp[1]))
        {
            reshowCursor(cursorPosition.x, cursorPosition.y);
        }
        
        repaint(startX, startY, tmp[0], tmp[1]);
    }
    
    @Override
    public void repaint(final int x, final int y, final int w, final int h)
    {
        if (isScaling)
        {
            if (scaledImageCache == null || scaledImageCache.getWidth() != preferredSize.width || scaledImageCache.getHeight() != preferredSize.height)
            {
                // pull the entire image out and place into scaledImageCache
                scaledImageCache = new BufferedImage(preferredSize.width, preferredSize.height, image.getType());
                Graphics2D g2d = scaledImageCache.createGraphics();
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2d.drawImage(image, 0, 0, scaledImageCache.getWidth(), scaledImageCache.getHeight(), 0, 0, image.getWidth(), image.getHeight(), null);
                g2d.dispose();
                repaint();
            }
            else
            {
                final float _xScaled = ((float)scaleFactors.width*((float)x));
                final float _yScaled = ((float)scaleFactors.height*((float)y));
                final int xScaled = (int) Math.floor((double)_xScaled);
                final int yScaled = (int) Math.floor((double)_yScaled);
                final int wScaled = (int) Math.ceil((double)(((float)(scaleFactors.width*((float)(x+w))))-_xScaled));
                final int hScaled = (int) Math.ceil((double)(((float)(scaleFactors.height*((float)(y+h))))-_yScaled));
                
                // pull the region out and place into scaledImageCache
                Graphics2D g2d = scaledImageCache.createGraphics();
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2d.drawImage(image, xScaled, yScaled, xScaled+wScaled, yScaled+hScaled, x, y, x+w, y+h, null);
                g2d.dispose();
                super.repaint(offX + xScaled, offY + yScaled, wScaled, hScaled);
            }
        }
        else
        {
            super.repaint(offX + x, offY + y, w, h);
        }
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
    
    public void setScaleFactors(int offX, int offY, DimensionF scaleFactors)
    {
        setScaleFactors(offX, offY, scaleFactors.width, scaleFactors.height);
    }
    
    private void setScaleFactors(int offX, int offY, float wScale, float hScale)
    {
        boolean doSync = false;
        
        if (setPaintOffset(offX, offY))
        {
            doSync = true;
        }
        
        if (scaleFactors.width != wScale || scaleFactors.height != hScale)
        {
            isScaling = (wScale != 1.0f || hScale != 1.0f);
            scaleFactors.width = wScale;
            scaleFactors.height = hScale;
            float fScaledWidth = scaleFactors.width * ((float)image.getWidth());
            float fScaledHeight = scaleFactors.height * ((float)image.getHeight());
            // TODO: possibly pass in the expected image dimensions so there is
            // no need to round
            preferredSize.width = (int) Math.round(fScaledWidth);
            preferredSize.height = (int) Math.round(fScaledHeight);
            if (!isScaling || (scaledImageCache != null && (scaledImageCache.getWidth() != preferredSize.width || scaledImageCache.getHeight() != preferredSize.height)))
            {
                scaledImageCache = null;
            }
            doSync = true;
        }
        
        if (doSync)
        {
            syncPreferredSize();
            repaint();
        }
    }
    
    private void syncPreferredSize()
    {
        preferredSizeWithOffsets.width = preferredSize.width + offX;
        preferredSizeWithOffsets.height = preferredSize.height + offY;
        setPreferredSize(preferredSizeWithOffsets);
    }
    
    public void getImageSize(Dimension d)
    {
        d.width = image.getWidth();
        d.height = image.getHeight();
    }
    
    private boolean setPaintOffset(int offX, int offY)
    {
        boolean rval = false;
        
        if (this.offX != offX || this.offY != offY)
        {
            this.offX = offX;
            this.offY = offY;
            rval = true;
        }
        
        return rval;
    }
    
}
