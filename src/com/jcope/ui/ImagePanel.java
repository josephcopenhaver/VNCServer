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
        Graphics2D g2d = (Graphics2D) g;
        super.paintComponent(g);
        setRenderingHints(g2d);
        
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
        
        drawCursor(g2d);
    }
    
    private void drawCursor(Graphics2D g2d)
    {
        if (cursorVisible)
        {
            final int startX = offX + pixelsUnderCursorRect.x + 1;
            final int endX = offX + pixelsUnderCursorRect.width + pixelsUnderCursorRect.x - 1;
            final int startY = offY + pixelsUnderCursorRect.y + 1;
            final int endY = offY + pixelsUnderCursorRect.height + pixelsUnderCursorRect.y - 1;
            
            g2d.setColor(Color.BLACK);
            g2d.drawLine(startX, startY, endX, endY);
            g2d.drawLine(startX, endY, endX, startY);
        }
    }

    private void loadScreenPixels(int[] pixels)
    {
        int screenWidth = image.getWidth();
        int screenHeight = image.getHeight();
        setRGB(image, 0, 0, pixels, 0, 0, screenWidth, screenHeight, screenWidth, screenHeight);
        
        repaint();
    }
    
    private void setSegment(int segmentID, SEGMENT_ALGORITHM alg, Object... args)
    {
        int solidPixelColor = 0;
        int[] pixels = null;
        
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
                setRGB(image, startX, startY, pixels, 0, 0, tmp[0], tmp[1], tmp[0], tmp[1]);
                break;
            case SOLID_COLOR:
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
    
    private boolean _repaint()
    {
        return _repaint(false, null);
    }
    
    private boolean _repaint(boolean forceRepaint, Long tm)
    {
        boolean didRepaint = forceRepaint;
        
        if (isScaling && (scaledImageCache == null || scaledImageCache.getWidth() != preferredSize.width || scaledImageCache.getHeight() != preferredSize.height))
        {
            // pull the entire image out and place into scaledImageCache
            scaledImageCache = new BufferedImage(preferredSize.width, preferredSize.height, image.getType());
            Graphics2D g2d = scaledImageCache.createGraphics();
            setRenderingHints(g2d, Boolean.TRUE);
            g2d.drawImage(image, 0, 0, scaledImageCache.getWidth(), scaledImageCache.getHeight(), 0, 0, image.getWidth(), image.getHeight(), null);
            g2d.dispose();
            didRepaint = true;
        }
        
        if (didRepaint)
        {
            if (tm == null)
            {
                super.repaint();
            }
            else
            {
                super.repaint(tm);
            }
        }
        
        return didRepaint;
    }
    
    @Override
    public void repaint()
    {
        _repaint(true, null);
    }
    
    @Override
    public void repaint(Rectangle r)
    {
        if (!_repaint())
        {
            super.repaint(offX + r.x, offY + r.y, r.width, r.height);
        }
    }
    
    @Override
    public void repaint(long tm)
    {
        if (!_repaint(false, tm))
        {
            super.repaint(tm);
        }
    }
    
    @Override
    public void repaint(long tm, int x, int y, int w, int h)
    {
        if (!_repaint(false, tm))
        {
            super.repaint(tm, x, y, w, h);
        }
    }
    
    @Override
    public void repaint(final int x, final int y, final int w, final int h)
    {
        if (isScaling)
        {
            if (!_repaint())
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
        
        for (int y=0; y<srch; y++)
        {
            System.arraycopy(srcPixels, src, dstPixels, dst, srcw);
            dstBlock += dstw;
            dst = dstBlock;
            srcBlock += srcScanWidth;
            src = srcBlock;
        }
    }
    
    public void hideCursor()
    {
        if (cursorVisible)
        {
            cursorVisible = false;
            
            repaint(pixelsUnderCursorRect);
        }
    }
    
    private void setCursorRect(int x, int y)
    {
        final int startX = Math.round(((float)(x-halfCursorSideLength)) * scaleFactors.width);
        final int endX = Math.round(((float)(x+halfCursorSideLength)) * scaleFactors.width);
        final int startY = Math.round(((float)(y-halfCursorSideLength)) * scaleFactors.height);
        final int endY = Math.round(((float)(y+halfCursorSideLength)) * scaleFactors.height);
        
        pixelsUnderCursorRect.x = startX-1;
        pixelsUnderCursorRect.y = startY-1;
        pixelsUnderCursorRect.width = endX-startX+2;
        pixelsUnderCursorRect.height = endY-startY+2;
    }
    
    private void showCursor(int x, int y)
    {
        if (!cursorVisible)
        {
            cursorVisible = true;
            
            setCursorRect(x, y);
            cursorPosition.x = x;
            cursorPosition.y = y;
            
            repaint(pixelsUnderCursorRect);
        }
    }
    
    public void moveCursor(int x, int y)
    {
        if (cursorVisible && x == cursorPosition.x && y == cursorPosition.y)
        {
            // no change
            return;
        }
        
        hideCursor();
        showCursor(x, y);
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
