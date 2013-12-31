package com.jcope.util;

import static com.jcope.debug.Debug.assert_;

import java.awt.Rectangle;

public class SegmentationInfo
{
    
    public int
        numHorizontalSegments,
        numVerticalSegments,
        numSegments,
        maxHorizontalSegmentIdx,
        maxVerticalSegmentIdx,
        maxSegmentID,
        lastSegmentWidth,
        lastSegmentHeight,
        bottomRowNumPixels,
        rightColNumPixels,
        bottomRightSegmentNumPixels,
        screenWidth,
        screenHeight,
        segmentWidth,
        segmentHeight,
        maxSegmentNumPixels;
    
    public void loadConfig(int screenWidth, int screenHeight, int segmentWidth, int segmentHeight)
    {
        numHorizontalSegments = ((screenWidth+ (segmentWidth-1))/segmentWidth);
        numVerticalSegments = ((screenHeight + (segmentHeight-1))/segmentHeight);
        numSegments = numHorizontalSegments * numVerticalSegments;
        
        maxHorizontalSegmentIdx = numHorizontalSegments - 1;
        maxVerticalSegmentIdx = numVerticalSegments - 1;
        maxSegmentID = numSegments - 1;
        lastSegmentWidth = screenWidth%segmentWidth;
        lastSegmentHeight = screenHeight%segmentHeight;
        if (lastSegmentWidth == 0)
        {
            lastSegmentWidth = segmentWidth;
        }
        if (lastSegmentHeight == 0)
        {
            lastSegmentHeight = segmentHeight;
        }
        maxSegmentNumPixels = segmentWidth * segmentHeight;
        bottomRowNumPixels = segmentWidth * lastSegmentHeight;
        rightColNumPixels = lastSegmentWidth * segmentHeight;
        bottomRightSegmentNumPixels = lastSegmentWidth * lastSegmentHeight;
        
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.segmentWidth = segmentWidth;
        this.segmentHeight = segmentHeight;
    }
    
    public void getIdxPos(int segmentID, int[] pos)
    {
        assert_(segmentID >= 0);
        assert_(segmentID <= maxSegmentID);
        assert_(pos.length >= 2);
        
        pos[0] = segmentID%numHorizontalSegments;
        pos[1] = segmentID/numHorizontalSegments;
    }
    
    public void getDim(int segmentID, int[] dim)
    {
        assert_(segmentID >= 0);
        assert_(segmentID <= maxSegmentID);
        assert_(dim.length >= 2);
        
        getIdxPos(segmentID, dim);
        
        dim[0] = (dim[0] == maxHorizontalSegmentIdx) ? lastSegmentWidth : segmentWidth;
        dim[1] = (dim[1] == maxVerticalSegmentIdx) ? lastSegmentHeight : segmentHeight;
    }
    
    public void getPos(int segmentID, int[] absPos)
    {
        assert_(segmentID >= 0);
        assert_(segmentID <= maxSegmentID);
        assert_(absPos.length >= 2);
        
        getIdxPos(segmentID, absPos);
        absPos[0] = (absPos[0] * segmentWidth);
        absPos[1] = (absPos[1] * segmentHeight);
    }

    public int getSegmentPixelCount(int segmentID)
    {
        assert_(segmentID >= 0);
        assert_(segmentID <= maxSegmentID);
        
        int rval;
        int[] pos = new int[2];
        getIdxPos(segmentID, pos);
        
        if (pos[0] == maxHorizontalSegmentIdx)
        {
            rval = (pos[1] == maxVerticalSegmentIdx) ? bottomRightSegmentNumPixels : rightColNumPixels;
        }
        else if (pos[1] == maxVerticalSegmentIdx)
        {
            rval = bottomRowNumPixels;
        }
        else
        {
            rval = maxSegmentNumPixels;
        }
        
        return rval;
    }

    public int getSegmentID(int x, int y)
    {
        assert_(x >= 0);
        assert_(y >= 0);
        
        int rval = x + y*numHorizontalSegments;
        
        assert_(rval >= 0);
        assert_(rval <= maxSegmentID);
        
        return rval;
    }

    public static boolean updateIntersection(int[] dst, Rectangle dstRect, int[] src, int srcx, int srcy, int srcw,
            int srch)
    {
        boolean rval = Boolean.FALSE;
        
        int top = Math.max(dstRect.y, srcy);
        int bottom = Math.min(dstRect.y + dstRect.height, srcy + srch);
        
        if (top <= bottom)
        {
            int left = Math.max(dstRect.x, srcx);
            int right = Math.min(dstRect.x + dstRect.width, srcx + srcw);
            
            if (left <= right)
            {
                rval = Boolean.TRUE;
                
                int ub = bottom-top;
                int scanSize = right-left;
                int srcIdx = left-srcx + (top-srcy)*srcw;
                int dstIdx = left-dstRect.x + (top-dstRect.y)*dstRect.width;
                
                for (int i=0; i<ub; i++)
                {
                    System.arraycopy(src, srcIdx, dst, dstIdx, scanSize);
                    
                    srcIdx += srcw;
                    dstIdx += dstRect.width;
                }
            }
        }
        
        return rval;
    }
    
}
