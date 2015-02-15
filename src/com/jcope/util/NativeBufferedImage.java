package com.jcope.util;

import static com.jcope.debug.Debug.DEBUG;
import static com.jcope.debug.Debug.assert_;

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;

import com.jcope.debug.LLog;

public class NativeBufferedImage {
    
    abstract static class Mutator {
        public final int bytesPerPixel;
        public Mutator(int bytesPerPixel) {
            this.bytesPerPixel = bytesPerPixel;
        }
        
        public abstract int mutate(int pixel);
        
        public void mutate(int idx, int[] pixels, int pixel)
        {
        	assert_(bytesPerPixel == 3);
            int midx = idx / 3;
            pixel = mutate(pixel) & 0xFFFFFF;
            switch (idx % 4) {
            case 0:
                // first three
                pixels[midx] = (pixels[midx] & 0x000000FF) | ((pixel <<  8) & 0xFFFFFF00);
                break;
            case 1:
                // last & first two
                pixels[midx] = (pixels[midx] & 0xFFFFFF00) | ((pixel >> 16) & 0x000000FF);
                midx++;
                pixels[midx] = (pixels[midx] & 0x0000FFFF) | ((pixel << 16) & 0xFFFF0000);
                break;
            case 2:
                // last two & first
                pixels[midx] = (pixels[midx] & 0xFFFF0000) | ((pixel >> 8) & 0x0000FFFF);
                midx++;
                pixels[midx] = (pixels[midx] & 0x00FFFFFF) | ((pixel << 24) & 0xFF000000);
                break;
            case 3:
                // last 3
                pixels[midx] = (pixels[midx] & 0xFF000000) | pixel;
                break;
            }
        }
    }
    
    private static final Mutator nopMutator = new Mutator(4) {

        @Override
        public int mutate(int pixel) {
            return pixel;
        }
        
    };
    
    private final BufferedImage img;
    private final Mutator mutator;
    
    public NativeBufferedImage(int width, int height, int type) {
        assert_(type == BufferedImage.TYPE_INT_ARGB);
        GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice device = env.getDefaultScreenDevice();
        GraphicsConfiguration config = device.getDefaultConfiguration();
        img = config.createCompatibleImage(width, height, Transparency.TRANSLUCENT);
        switch (img.getType()) {
        case BufferedImage.TYPE_INT_RGB:
            mutator = new Mutator(4) {
                
                @Override
                public int mutate(int pixel)
                {
                    return pixel & 0x00FFFFFF;
                }
                
            };
            break;
        case BufferedImage.TYPE_INT_ARGB:
            mutator = nopMutator;
            break;
        case BufferedImage.TYPE_INT_BGR:
            mutator = new Mutator(4) {

                @Override
                public int mutate(int pixel) {
                    return (pixel & 0x0000FF00) | ((pixel << 16) & 0x00FF0000) | ((pixel >> 16) & 0x000000FF);
                }
                
            };
            break;
        case BufferedImage.TYPE_4BYTE_ABGR:
            mutator = new Mutator(4) {

                @Override
                public int mutate(int pixel) {
                    return (pixel & 0xFF00FF00) | ((pixel << 16) & 0x00FF0000) | ((pixel >> 16) & 0x000000FF);
                }
                
            };
            break;
        case BufferedImage.TYPE_3BYTE_BGR:
            mutator = new Mutator(3) {

                @Override
                public int mutate(int pixel) {
                    return (pixel & 0x0000FF00) | ((pixel << 16) & 0x00FF0000) | ((pixel >> 16) & 0x000000FF);
                }
                
            };
            break;
        case BufferedImage.TYPE_INT_ARGB_PRE:
            mutator = new Mutator(4) {
                
                @Override
                public int mutate(int pixel) {
                    return pixel | 0xFF000000;
                }
                
            };
            break;
        case BufferedImage.TYPE_4BYTE_ABGR_PRE:
            mutator = new Mutator(4) {

                @Override
                public int mutate(int pixel) {
                    return 0xFF000000 | (pixel & 0x0000FF00) | ((pixel << 16) & 0x00FF0000) | ((pixel >> 16) & 0x000000FF);
                }
                
            };
            break;
        case BufferedImage.TYPE_BYTE_GRAY:
        case BufferedImage.TYPE_BYTE_BINARY:
        case BufferedImage.TYPE_BYTE_INDEXED:
        case BufferedImage.TYPE_USHORT_GRAY:
        case BufferedImage.TYPE_USHORT_565_RGB:
        case BufferedImage.TYPE_USHORT_555_RGB:
            // not interested in these systems
            assert_(false);
            mutator = null;
            break;
        case BufferedImage.TYPE_CUSTOM:
            // how would we use custom stuff
            assert_(false);
            mutator = null;
            break;
        default:
            // undocumented wtf
            assert_(false);
            mutator = null;
            break;
        }
    }
    
    public BufferedImage get() {
        return img;
    }

    public int getWidth() {
        return img.getWidth();
    }

    public int getHeight() {
        return img.getHeight();
    }

    public void setRGB(int x, int y, int rgb) {
        img.setRGB(x, y, rgb);
    }

    public int getType() {
        return img.getType();
    }
    
    public void setRGB(int dstx, int dsty,
            int[] srcPixels, int srcx, int srcy, int srcw, int srch, int srcScanWidth, int srcScanHeight)
    {
        int dstw = img.getWidth();
        int dsth = img.getHeight();
        assert_(srcx + srcw <= srcScanWidth);
        assert_(srcy + srch <= srcScanHeight);
        assert_(srcw + dstx <= dstw);
        assert_(srch + dsty <= dsth);
        
        int[] dstPixels = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
        
        int dst = dsty * dstw + dstx;
        int dstBlock = dst;
        int src = srcy * srcScanWidth + srcx;
        int srcBlock = src;
        
        if (mutator.bytesPerPixel == 4)
        {
            if (mutator != nopMutator)
            {
                int idx = 0;
                do
                {
                    srcPixels[idx] = mutator.mutate(srcPixels[idx]);
                    idx++;
                } while (idx < srcPixels.length);
            }
            for (int y=0; y<srch; y++)
            {
                System.arraycopy(srcPixels, src, dstPixels, dst, srcw);
                dstBlock += dstw;
                dst = dstBlock;
                srcBlock += srcScanWidth;
                src = srcBlock;
            }
        }
        else
        {
            int srcEnd;
            if (srcw <= 0)
            {
                if (DEBUG) {LLog.w("Why on earth is src <= 0 ?");}
                return;
            }
            for (int y=0; y<srch; y++)
            {
                srcEnd = src + srcw;
                do
                {
                    mutator.mutate(dst++, dstPixels, srcPixels[src++]);
                } while (src < srcEnd);
                dstBlock += dstw;
                dst = dstBlock;
                srcBlock += srcScanWidth;
                src = srcBlock;
            }
        }
    }
    
    public void fillRGB(int pixelColor)
    {
        int[] dstPixels = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
        if (mutator.bytesPerPixel == 4)
        {
            pixelColor = mutator.mutate(pixelColor);
            Arrays.fill(dstPixels, pixelColor);
        }
        else
        {
            int[] alignedSolidPixels = new int[3];
            for (int idx=0; idx<4; idx++)
            {
            	mutator.mutate(idx, alignedSolidPixels, pixelColor);
            }
            int idx = 0;
            while (idx < dstPixels.length)
            {
            	dstPixels[idx] = alignedSolidPixels[idx % alignedSolidPixels.length];
            	idx++;
            }
        }
    }
}
