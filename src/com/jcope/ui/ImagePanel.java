package com.jcope.ui;

import static com.jcope.debug.Debug.assert_;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicIntegerArray;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import com.jcope.debug.LLog;
import com.jcope.util.DimensionF;
import com.jcope.util.SegmentationInfo;
import com.jcope.util.SegmentationInfo.SEGMENT_ALGORITHM;

public class ImagePanel extends JPanel
{
    static class SynchronousTransform {
        
        private final Semaphore accessSema = new Semaphore(1, true);
        private final AtomicIntegerArray a_offsets;
        private final AtomicIntegerArray a_scaleFactors;
        private volatile byte scaleState = 0;

        public static final byte SCALING_MASK = 1;
        public static final byte SCALING_DOWN_MASK = 2;
        
        public SynchronousTransform()
        {
            a_offsets = new AtomicIntegerArray(2);
            a_scaleFactors = new AtomicIntegerArray(2);
            int[] offsets = new int[2];
            float[] scaleFactors = new float[]{1.0f, 1.0f};
            nts_write(offsets, scaleFactors);
        }
        
        public AffineTransform get()
        {
            try
            {
                accessSema.acquire();
            }
            catch (InterruptedException e)
            {
                LLog.e(e);
            }
            try
            {
                return new AffineTransform((double) Float.intBitsToFloat(a_scaleFactors.get(0)), 0.0d, 0.0d, (double) Float.intBitsToFloat(a_scaleFactors.get(1)), (double) a_offsets.get(0), (double) a_offsets.get(1));
            }
            finally {
                accessSema.release();
            }
        }
        
        private void nts_write(int[] offsets, float[] scaleFactors)
        {
            nts_write(offsets);
            nts_write(scaleFactors);
        }
        
        private void nts_write(int[] offsets)
        {
            for (int i=0; i < offsets.length; i++)
            {
                a_offsets.set(i, offsets[i]);
            }
        }
        
        private void nts_write(float[] scaleFactors)
        {
            byte scaleState = 0;
            for (int i=0; i < scaleFactors.length; i++)
            {
                if (scaleFactors[i] != 1.0f)
                {
                    scaleState = SCALING_MASK;
                }
                a_scaleFactors.set(i, Float.floatToIntBits(scaleFactors[i]));
            }
            if (
                scaleState == SCALING_MASK
                && scaleFactors[0] < 1.0f
                && scaleFactors[1] < 1.0f
            )
            {
                scaleState |= SCALING_DOWN_MASK;
            }
            this.scaleState = scaleState;
        }
        
        public void nts_offsets(int[] offsets)
        {
            for (int i=0; i < offsets.length; i++)
            {
                offsets[i] = a_offsets.get(i);
            }
        }
        
        public int[] getOffsets()
        {
            int[] offsets = new int[2];
            try
            {
                accessSema.acquire();
            }
            catch (InterruptedException e)
            {
                LLog.e(e);
            }
            try
            {
                nts_offsets(offsets);
                return offsets;
            }
            finally {
                accessSema.release();
            }
        }
        
        public void nts_scaleFactors(float[] scaleFactors)
        {
            for (int i=0; i < scaleFactors.length; i++)
            {
                scaleFactors[i] = Float.intBitsToFloat(a_scaleFactors.get(i));
            }
        }
        
        public float[] getScaleFactors()
        {
            float[] scaleFactors = new float[2];
            try
            {
                accessSema.acquire();
            }
            catch (InterruptedException e)
            {
                LLog.e(e);
            }
            try
            {
                nts_scaleFactors(scaleFactors);
                return scaleFactors;
            }
            finally {
                accessSema.release();
            }
        }
        
        public void getValues(int[] offsets, float[] scaleFactors)
        {
            getValues(offsets, scaleFactors, null);
        }
        
        public void getValues(
            int[] offsets, float[] scaleFactors, byte[] scaleStates
        )
        {
            try
            {
                accessSema.acquire();
            }
            catch (InterruptedException e)
            {
                LLog.e(e);
            }
            try
            {
                nts_offsets(offsets);
                nts_scaleFactors(scaleFactors);
                if (scaleStates == null)
                {
                    return;
                }
                scaleStates[0] = scaleState;
            }
            finally {
                accessSema.release();
            }
        }
        
        public void write(int[] offsets, float[] scaleFactors)
        {
            try
            {
                accessSema.acquire();
            }
            catch (InterruptedException e)
            {
                LLog.e(e);
            }
            try
            {
                nts_write(offsets, scaleFactors);
            }
            finally {
                accessSema.release();
            }
        }
        
        public void write(int[] offsets)
        {
            try
            {
                accessSema.acquire();
            }
            catch (InterruptedException e)
            {
                LLog.e(e);
            }
            try
            {
                nts_write(offsets);
            }
            finally {
                accessSema.release();
            }
        }
        
        public void write(float[] scaleFactors)
        {
            try
            {
                accessSema.acquire();
            }
            catch (InterruptedException e)
            {
                LLog.e(e);
            }
            try
            {
                nts_write(scaleFactors);
            }
            finally {
                accessSema.release();
            }
        }

        public boolean setOffsets(int offX, int offY)
        {
            int[] offsets = new int[2];
            try
            {
                accessSema.acquire();
            }
            catch (InterruptedException e)
            {
                LLog.e(e);
            }
            try
            {
                nts_offsets(offsets);
                if (offsets[0] != offX || offsets[1] != offY)
                {
                    write(offsets);
                    return true;
                }
                return false;
            }
            finally {
                accessSema.release();
            }
        }
        
        public void set(int offX, int offY, float wScale, float hScale)
        {
            set(offX, offY, wScale, hScale, null, null, null);
        }

        public void set(int offX, int offY, float wScale, float hScale,
            Runnable onOffsetChange, Runnable onScaleFactorChange,
            Runnable onChange)
        {
            int[] offsets = new int[]{offX, offY};
            float[] scaleFactors = new float[]{wScale, hScale};
            set(offsets, scaleFactors,
                onOffsetChange, onScaleFactorChange, onChange);
        }

        public void set(int[] offsets, float[] scaleFactors,
            Runnable onOffsetChange, Runnable onScaleFactorChange,
            Runnable onChange)
        {
            boolean fireOffsetChange = false;
            boolean fireScaleFactorChange = false;
            boolean fireChange = false;
            try
            {
                accessSema.acquire();
            }
            catch (InterruptedException e)
            {
                LLog.e(e);
            }
            try
            {
                if (onOffsetChange == null && onChange == null)
                {
                    nts_write(offsets);
                }
                else if (offsets[0] != a_offsets.get(0)
                    || offsets[1] != a_offsets.get(1))
                {
                    fireChange = true;
                    fireOffsetChange = (onOffsetChange != null);
                    nts_write(offsets);
                }
                if (onScaleFactorChange == null && onChange == null)
                {
                    nts_write(scaleFactors);
                }
                else if (scaleFactors[0] != Float.intBitsToFloat(a_scaleFactors.get(0))
                    || scaleFactors[1] != Float.intBitsToFloat(a_scaleFactors.get(1)))
                {
                    fireChange = true;
                    fireScaleFactorChange = (onScaleFactorChange != null);
                    nts_write(scaleFactors);
                }
                if (fireOffsetChange)
                {
                    onOffsetChange.run();
                }
                if (fireScaleFactorChange)
                {
                    onScaleFactorChange.run();
                }
                if (fireChange && onChange != null)
                {
                    onChange.run();
                }
            }
            finally {
                accessSema.release();
            }
        }
        
        public boolean isScalingDown()
        {
            return (scaleState & SCALING_DOWN_MASK) != 0;
        }
    }
    
    // Generated: serialVersionUID
    private static final long serialVersionUID = -4538018101380490678L;
    private static final int cursorSideLength = 32;
    private static final int halfCursorSideLength = cursorSideLength/2;
    
    private BufferedImage image;
    private SegmentationInfo segInfo;
    
    private boolean cursorVisible = false;
    private Point cursorPosition = new Point();
    private Dimension preferredSize = new Dimension();
    private Dimension preferredSizeWithOffsets = new Dimension();
    
    private Rectangle pixelsUnderCursorRect = new Rectangle();
    private volatile BufferedImage scaledImageCache = null;
    private final SynchronousTransform transform;
    
    private volatile int frameBufferIdx = 0;
    private int[][] frameBuffer = new int[][]{null};
    private final Semaphore frameBufferLock = new Semaphore(1, true);
    
    public ImagePanel(int width, int height)
    {
        setBackground(Color.BLACK);
        image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        segInfo = new SegmentationInfo();
        preferredSize.width = width;
        preferredSize.height = height;
        transform = new SynchronousTransform();
        syncPreferredSize(null);
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
        
        int[] offsets = transform.getOffsets();
        
        if (transform.isScalingDown())
        {
            if (scaledImageCache != null)
            {
                g2d.drawImage(scaledImageCache, offsets[0], offsets[1], null);
            }
        }
        else
        {
            g2d.drawImage(image, transform.get(), null);
        }
        
        drawCursor(g2d);
    }
    
    private void drawCursor(Graphics2D g2d)
    {
        if (cursorVisible)
        {
            int[] offsets = transform.getOffsets();
            final int startX = offsets[0] + pixelsUnderCursorRect.x + 1;
            final int endX = offsets[0] + pixelsUnderCursorRect.width + pixelsUnderCursorRect.x - 1;
            final int startY = offsets[1] + pixelsUnderCursorRect.y + 1;
            final int endY = offsets[1] + pixelsUnderCursorRect.height + pixelsUnderCursorRect.y - 1;
            
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
    }
    
    private void nts_clearFrameBuffer()
    {
        frameBufferIdx = 0;
    }
    
    /**
     * Only resets the frame buffer state
     * Does NOT flush the frame buffer
     */
    public void clearFrameBuffer()
    {
        try
        {
            frameBufferLock.acquire();
        } catch (InterruptedException e) {
            LLog.e(e);
        }
        try
        {
            nts_clearFrameBuffer();
        }
        finally {
            frameBufferLock.release();
        }
    }
    
    /**
     * force repainting of everything,
     * including any scaled image buffers
     */
    public void repaintBuffers()
    {
        clearFrameBuffer();
    	scaledImageCache = null;
    	repaint();
    }
    
    private void setSegment(int segmentID, final SEGMENT_ALGORITHM alg, Object... args)
    {
        final int solidPixelColor;
        final int[] pixels;
        
        assert_(segmentID >= -1);
        assert_(args.length == 1);
        
        switch (alg)
        {
            case PIXELS:
                pixels = (int[]) args[0];
                solidPixelColor = 0;
                if (segmentID == -1)
                {
                    loadScreenPixels(pixels);
                    repaintBuffers();
                    return;
                }
                break;
            case SOLID_COLOR:
                assert_(segmentID >= 0);
                pixels = null;
                solidPixelColor = (Integer) args[0];
                if (segmentID == -1)
                {
                    fillRGB(image, solidPixelColor);
                    repaintBuffers();
                	return;
                }
                break;
            default:
                assert_(false);
                pixels = null;
                solidPixelColor = 0;
        }
        
        final int[] tmp = new int[2];
        final int startX;
        final int startY;
        segInfo.getPos(segmentID, tmp);
        startX = tmp[0];
        startY = tmp[1];
        segInfo.getDim(segmentID, tmp);
        
        
        addToFrameBuffer(new Runnable() {

            @Override
            public void run()
            {
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
            }
        
        },
        startX, startY, tmp[0], tmp[1]);
    }
    
    public void addToFrameBuffer(Runnable syncAction, int x, int y, int w, int h)
    {
        try
    	{
    		frameBufferLock.acquire();
    	} catch (InterruptedException e) {
			LLog.e(e);
		}
    	try
    	{
            syncAction.run();
    	    synchronized(frameBuffer) {
    		    int idx = frameBufferIdx;
	    		int[] buffer = frameBuffer[0];
	    		buffer[idx++] = x;
		        buffer[idx++] = y;
		        buffer[idx++] = w;
		        buffer[idx++] = h;
		        frameBufferIdx = idx;
    		}
    	}
    	finally {
    		frameBufferLock.release();
    	}
    }
    
    private void runOnUIThread(Runnable runnable)
    {
        if (SwingUtilities.isEventDispatchThread())
        {
            runnable.run();
            return;
        }
        SwingUtilities.invokeLater(runnable);
    }
    
    public void flushFrameBuffer()
    {
        final int f_frameBufferIdx;
        final int[] f_frameBuffer;
        try {
			frameBufferLock.acquire();
		} catch (InterruptedException e) {
			LLog.e(e);
		}
        try
        {
            f_frameBufferIdx = frameBufferIdx;
            if (f_frameBufferIdx == 0)
            {
                return;
            }
        	if (f_frameBufferIdx == segInfo.numSegments * 4)
            {
        	    nts_clearFrameBuffer();
        	    runOnUIThread(new Runnable() {

        			@Override
        			public void run() {
        				// repaint all buffers, post frame clear
        			    scaledImageCache = null;
        		        repaint();
        			}
                });
                return;
            }
        	synchronized(frameBuffer) {
	        	f_frameBuffer = Arrays.copyOf(frameBuffer[0], f_frameBufferIdx);
        	}
        	nts_clearFrameBuffer();
        }
        finally {
            frameBufferLock.release();
        }
        runOnUIThread(new Runnable() {

			@Override
			public void run() {
				int idx,x,y,w,h;
		        idx = 0;
		        while (idx < f_frameBufferIdx)
		        {
		            x = f_frameBuffer[idx++];
		            y = f_frameBuffer[idx++];
		            w = f_frameBuffer[idx++];
		            h = f_frameBuffer[idx++];
		            repaint(x, y, w, h);
		        }
			}
        	
        });
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
        
        if (transform.isScalingDown() && (scaledImageCache == null || scaledImageCache.getWidth() != preferredSize.width || scaledImageCache.getHeight() != preferredSize.height))
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
        if (transform == null)
        {
            super.repaint();
            return;
        }
        _repaint(true, null);
    }
    
    @Override
    public void repaint(Rectangle r)
    {
        if (transform == null)
        {
            super.repaint(r);
            return;
        }
        if (!_repaint())
        {
            int[] offsets = transform.getOffsets();
            super.repaint(offsets[0] + r.x, offsets[1] + r.y, r.width, r.height);
        }
    }
    
    @Override
    public void repaint(long tm)
    {
        if (transform == null)
        {
            super.repaint(tm);
            return;
        }
        if (!_repaint(false, tm))
        {
            super.repaint(tm);
        }
    }
    
    @Override
    public void repaint(long tm, int x, int y, int w, int h)
    {
        if (transform == null)
        {
            super.repaint(tm, x, y, w, h);
            return;
        }
        if (!_repaint(false, tm))
        {
            super.repaint(tm, x, y, w, h);
        }
    }
    
    @Override
    public void repaint(final int x, final int y, final int w, final int h)
    {
        if (transform == null)
        {
            super.repaint(x, y, w, h);
            return;
        }
        int[] offsets = new int[2];
        float[] scaleFactors = new float[2];
        byte[] scaleStateRef = new byte[1];
        byte scaleState;
        transform.getValues(offsets, scaleFactors, scaleStateRef);
        scaleState = scaleStateRef[0];
        scaleStateRef = null;
        if ((scaleState & SynchronousTransform.SCALING_MASK) != 0)
        {
            if ((scaleState & SynchronousTransform.SCALING_DOWN_MASK) != 0)
            {
                if (!_repaint())
                {
                    final int x2src = x+w;
                    final int y2src = y+h;
                    final int x1dst = Math.round(scaleFactors[0]*((float)x));
                    final int y1dst = Math.round(scaleFactors[1]*((float)y));
                    final int x2dst = Math.round(scaleFactors[0]*((float)x2src));
                    final int y2dst = Math.round(scaleFactors[1]*((float)y2src));
                    Graphics2D g2d = scaledImageCache.createGraphics();
                    setRenderingHints(g2d, Boolean.TRUE);
                    g2d.drawImage(image, x1dst, y1dst, x2dst, y2dst, x, y, x2src, y2src, null);
                    g2d.dispose();
                    super.repaint(offsets[0] + x1dst, offsets[1] + y1dst, x2dst - x1dst, y2dst - y1dst);
                }
            }
            else
            {
                final int x2src = x+w;
                final int y2src = y+h;
                final int x1dst = Math.round(scaleFactors[0]*((float)x));
                final int y1dst = Math.round(scaleFactors[1]*((float)y));
                final int x2dst = Math.round(scaleFactors[0]*((float)x2src));
                final int y2dst = Math.round(scaleFactors[1]*((float)y2src));
                super.repaint(offsets[0] + x1dst, offsets[1] + y1dst, x2dst - x1dst, y2dst - y1dst);
            }
        }
        else
        {
            super.repaint(offsets[0] + x, offsets[1] + y, w, h);
        }
    }

    public void setSegmentSize(int segmentWidth, int segmentHeight)
    {
        int screenWidth = image.getWidth();
        int screenHeight = image.getHeight();
        segInfo.loadConfig(screenWidth, screenHeight, segmentWidth, segmentHeight);
        int numSegmentInfoValues = segInfo.numSegments * 4;
        flushFrameBuffer();
        try {
			frameBufferLock.acquire();
		} catch (InterruptedException e) {
			LLog.e(e);
		}
        try
        {
        	synchronized(frameBuffer) {
		        if (frameBuffer[0] == null || frameBuffer[0].length < numSegmentInfoValues)
		        {
		            frameBuffer[0] = new int[numSegmentInfoValues];
		        }
        	}
        }
        finally {
        	frameBufferLock.release();
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
        
        for (int y=0; y<srch; y++)
        {
            System.arraycopy(srcPixels, src, dstPixels, dst, srcw);
            dstBlock += dstw;
            dst = dstBlock;
            srcBlock += srcScanWidth;
            src = srcBlock;
        }
    }
    
    private static void fillRGB(BufferedImage dstimg, int pixelColor)
    {
    	int[] dstPixels = ((DataBufferInt) dstimg.getRaster().getDataBuffer()).getData();
    	Arrays.fill(dstPixels, pixelColor);
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
        float[] scaleFactors = transform.getScaleFactors();
        final int startX = Math.round(((float)(x-halfCursorSideLength)) * scaleFactors[0]);
        final int endX = Math.round(((float)(x+halfCursorSideLength)) * scaleFactors[0]);
        final int startY = Math.round(((float)(y-halfCursorSideLength)) * scaleFactors[1]);
        final int endY = Math.round(((float)(y+halfCursorSideLength)) * scaleFactors[1]);
        
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
    
    private void setScaleFactors(final int offX, final int offY, final float wScale, final float hScale)
    {
        transform.set(offX, offY, wScale, hScale,
            null,
            new Runnable() {

                @Override
                public void run()
                {
                    float fScaledWidth = wScale * ((float)image.getWidth());
                    float fScaledHeight = hScale * ((float)image.getHeight());
                    // TODO: possibly pass in the expected image dimensions so there is
                    // no need to round
                    preferredSize.width = (int) Math.round(fScaledWidth);
                    preferredSize.height = (int) Math.round(fScaledHeight);
                    if (!transform.isScalingDown() || (scaledImageCache != null && (scaledImageCache.getWidth() != preferredSize.width || scaledImageCache.getHeight() != preferredSize.height)))
                    {
                        scaledImageCache = null;
                    }
                }
                
            },
            new Runnable() {

                @Override
                public void run()
                {
                    syncPreferredSize(new int[]{offX, offY});
                    repaint();
                }
                
            }
        );
    }
    
    private void syncPreferredSize(int[] offsets)
    {
        if (offsets == null)
        {
            offsets = transform.getOffsets();
        }
        preferredSizeWithOffsets.width = preferredSize.width + offsets[0];
        preferredSizeWithOffsets.height = preferredSize.height + offsets[1];
        setPreferredSize(preferredSizeWithOffsets);
    }
    
    public void getImageSize(Dimension d)
    {
        d.width = image.getWidth();
        d.height = image.getHeight();
    }
    
    public boolean worldToScale(Point p)
    {
    	return worldToScale(p, Boolean.TRUE);
    }
    
    public boolean worldToScale(Point p, boolean clamp)
    {
        boolean valid;
        int[] offsets = new int[2];
        float[] scaleFactors = new float[2];
        transform.getValues(offsets, scaleFactors);
        
        p.x -= offsets[0];
        p.y -= offsets[1];
        
        if (clamp)
        {
        	if (p.x < 0)
        	{
        		p.x = 0;
        	}
        	if (p.y < 0)
        	{
        		p.y = 0;
        	}
        	
        	p.x = Math.round(((float)p.x)/scaleFactors[0]);
            p.y = Math.round(((float)p.y)/scaleFactors[1]);
            
        	if (p.x >= image.getWidth())
			{
        		p.x = image.getWidth() - 1;
			}
        	if (p.y >= image.getHeight())
			{
        		p.y = image.getHeight() - 1;
			}
        	
        	if (p.x < 0)
        	{
        		p.x = 0;
        	}
        	if (p.y < 0)
        	{
        		p.y = 0;
        	}
        	
        	valid = true;
        }
        else if (p.x >= 0 && p.y >= 0)
        {
        	p.x = Math.round(((float)p.x)/scaleFactors[0]);
            p.y = Math.round(((float)p.y)/scaleFactors[1]);
            
            valid = (p.x < (image.getWidth()) && p.y < (image.getHeight()));
        }
        else
        {
        	valid = false;
        }
        
        return valid;
    }
    
}
