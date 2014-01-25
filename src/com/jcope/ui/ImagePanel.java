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
import java.awt.image.DataBufferInt;

import javax.swing.JPanel;

import com.jcope.util.DimensionF;
import com.jcope.util.SegmentationInfo;
import com.jcope.util.SegmentationInfo.SEGMENT_ALGORITHM;

public class ImagePanel extends JPanel
{
    
    // Generated: serialVersionUID
    private static final long serialVersionUID = -4538018101380490678L;
    private static final int cursorSideLength = 32;
    private static final int halfCursorSideLength = cursorSideLength/2;
    private int[] pixelsUnderCursor = new int[ (int) Math.pow(cursorSideLength+2, 2) ];
    
    private BufferedImage image;
    private SegmentationInfo segInfo;
    
    private boolean cursorVisible = false;
    private boolean suppressCursorRepaint = false;
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
    
    private static void setRenderingHints(Graphics2D g2d)
    {
        setRenderingHints(g2d, Boolean.FALSE);
    }
    
    private static void setRenderingHints(Graphics2D g2d, boolean withIntentToScale)
    {
        if (withIntentToScale)
        {
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        }
        g2d.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_DISABLE);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }
    
    @Override
    protected void paintComponent(Graphics g)
    {
        Graphics2D g2d;
        g2d = (Graphics2D) g;
        setRenderingHints(g2d);
        
        super.paintComponent(g);
        //setRenderingHints(g2d);// TODO: see if required
        
        if (isScaling)
        {
            if (scaledImageCache != null)
            {
                g2d.drawImage(scaledImageCache, offX, offY, null);
            }
        }
        else
        {
            g2d.drawImage(image, offX, offY, null);
        }
    }
    
    private void loadScreenPixels(int[] pixels)
    {
        int screenWidth = image.getWidth();
        int screenHeight = image.getHeight();
        setRGB(image, 0, 0, pixels, 0, 0, screenWidth, screenHeight, screenWidth, screenHeight);
        
        if (cursorVisible)
        {
            cursorVisible = false;
            boolean prevSuppressCursorRepaint = suppressCursorRepaint;
            suppressCursorRepaint = true;
            try
            {
                reshowCursor(cursorPosition.x, cursorPosition.y);
            }
            finally {
                suppressCursorRepaint = prevSuppressCursorRepaint;
            }
        }
        
        repaint();
    }
    
    private void setSegment(int segmentID, SEGMENT_ALGORITHM alg, Object... args)
    {
        int solidPixelColor = 0;
        int[] pixels = null;
        Object updateArg = null;
        
        assert_(args.length == 1);
        
        switch (alg)
        {
            case PIXELS:
                pixels = (int[]) args[0];
                if (segmentID == -1)
                {
                    loadScreenPixels(pixels);
                    return;
                }
                break;
            case SOLID_COLOR:
                assert_(segmentID >= 0);
                solidPixelColor = (Integer) args[0];
                break;
        }
        
        int[] tmp = new int[2];
        int startX, startY;
        segInfo.getPos(segmentID, tmp);
        startX = tmp[0];
        startY = tmp[1];
        segInfo.getDim(segmentID, tmp);
        // assert_(tmp[0] * tmp[1] == pixels.length);
        
        
        switch (alg)
        {
            case PIXELS:
                updateArg = pixels;
                setRGB(image, startX, startY, pixels, 0, 0, tmp[0], tmp[1], tmp[0], tmp[1]);
                break;
            case SOLID_COLOR:
                updateArg = solidPixelColor;
                int endX = startX + tmp[0];
                int endY = startY + tmp[1];
                
                for (int x=startX; x<endX; x++)
                {
                    for (int y=startY; y<endY; y++)
                    {
                        image.setRGB(x, y, solidPixelColor);
                    }
                }
                break;
        }
        
        if (cursorVisible && SegmentationInfo.updateIntersection(alg, pixelsUnderCursor, pixelsUnderCursorRect, startX, startY, tmp[0], tmp[1], updateArg))
        {
            reshowCursor(cursorPosition.x, cursorPosition.y);
        }
        
        repaint(startX, startY, tmp[0], tmp[1]);
    }
    
    public void setSegmentPixels(int segmentID, int[] pixels)
    {
        setSegment(segmentID, SEGMENT_ALGORITHM.PIXELS, pixels);
    }

    public void setSegmentSolidColor(int segmentID, int solidPixelColor)
    {
        setSegment(segmentID, SEGMENT_ALGORITHM.SOLID_COLOR, solidPixelColor);
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
                setRenderingHints(g2d, Boolean.TRUE);
                g2d.drawImage(image, 0, 0, scaledImageCache.getWidth(), scaledImageCache.getHeight(), 0, 0, image.getWidth(), image.getHeight(), null);
                g2d.dispose();
                repaint();
            }
            else
            {
                final int x2src = x+w;
                final int y2src = y+h;
                final int x1dst = Math.round(scaleFactors.width*((float)x));
                final int y1dst = Math.round(scaleFactors.height*((float)y));
                final int x2dst = Math.round(scaleFactors.width*((float)x2src));
                final int y2dst = Math.round(scaleFactors.height*((float)y2src));
                Graphics2D g2d = scaledImageCache.createGraphics();
                setRenderingHints(g2d, Boolean.TRUE);
                g2d.drawImage(image, x1dst, y1dst, x2dst, y2dst, x, y, x2src, y2src, null);
                g2d.dispose();
                super.repaint(offX + x1dst, offY + y1dst, x2dst - x1dst, y2dst - y1dst);
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
    
    // TODO: figure out why cursor hide/showing/moving is causing 1 pixel artifacts
    
    public void hideCursor()
    {
        if (cursorVisible)
        {
            cursorVisible = false;
            
            setRGB(image, pixelsUnderCursorRect.x, pixelsUnderCursorRect.y, pixelsUnderCursor, 0, 0, pixelsUnderCursorRect.width, pixelsUnderCursorRect.height, pixelsUnderCursorRect.width, pixelsUnderCursorRect.height);
            
            repaint(pixelsUnderCursorRect.x, pixelsUnderCursorRect.y, pixelsUnderCursorRect.width, pixelsUnderCursorRect.height);
        }
    }
    
    private static void setRGB(BufferedImage dstimg, int dstx, int dsty,
            int[] srcPixels, int srcx, int srcy, int srcw, int srch, int srcScanWidth, int srcScanHeight)
    {
        int dstw = dstimg.getWidth();
        int dsth = dstimg.getHeight();
        assert_(srcx + srcw <= srcScanWidth);
        assert_(srcy + srch <= srcScanHeight);
        assert_(srcw + dstx <= dstw);
        assert_(srch + dsty <= dsth);
        
        int[] dstPixels = ((DataBufferInt) dstimg.getRaster().getDataBuffer()).getData();
        
        int dst = dsty * dstw + dstx;
        int dstBlock = dst;
        int src = srcy * srcScanWidth + srcx;
        int srcBlock = src;
        int yub = dsty+srch;
        int xub = dstx+srcw;
        int x,y;
        
        for (y=dsty; y<yub; y++)
        {
            for (x=dstx; x<xub; x++)
            {
                dstPixels[dst] = srcPixels[src];
                dst++;
                src++;
            }
            dstBlock += dstw;
            dst = dstBlock;
            srcBlock += srcScanWidth;
            src = srcBlock;
        }
    }

    private void storePixelsUnderCursor(int x1, int y1, int x2, int y2)
    {
        final int[] ints;
        final int scanSize;
        int outIdx,scanStart,pos,y,x,pix;
        
        if (x1 < 0)
        {
            x1 = 0;
        }
        if (y1 < 0)
        {
            y1 = 0;
        }
        
        if (x2 > image.getWidth())
        {
            x2 = image.getWidth();
        }
        if (y2 > image.getHeight())
        {
            y2 = image.getHeight();
        }
        
        pixelsUnderCursorRect.x = x1;
        pixelsUnderCursorRect.y = y1;
        pixelsUnderCursorRect.width = x2-x1;
        pixelsUnderCursorRect.height = y2-y1;
        
        outIdx = image.getHeight();
        outIdx = 0;
        scanSize = image.getWidth();
        scanStart = (y1 * scanSize) + x1;
        ints = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        for (y=y1; y<y2; y++)
        {
            pos = scanStart;
            for (x=x1; x<x2; x++)
            {
                pix = ints[pos];
                pos++;
                pixelsUnderCursor[outIdx] = pix;
                outIdx++;
            }
            scanStart += scanSize;
        }
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
            storePixelsUnderCursor(startX-1, startY-1, endX+1, endY+1);
            
            final Graphics2D g2d = image.createGraphics();
            setRenderingHints(g2d);
            
            g2d.setColor(Color.BLACK);
            g2d.drawLine(startX, startY, endX, endY);
            g2d.drawLine(startX, endY, endX, startY);
            g2d.dispose();
            
            if (!suppressCursorRepaint)
            {
                repaint(startX-1, startY-1, endX-startX+2, endY-startY+2);
            }
            
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
    
    public boolean worldToScale(Point p)
    {
        boolean valid = false;
        
        p.x -= offX;
        p.y -= offY;
        
        if (p.x >= 0 && p.y >= 0)
        {
            p.x = Math.round(((float)p.x)/scaleFactors.width);
            p.y = Math.round(((float)p.y)/scaleFactors.height);
            
            if (p.x < (image.getWidth()) && p.y < (image.getHeight()))
            {
                valid = true;
            }
        }
        
        return valid;
    }
    
}
