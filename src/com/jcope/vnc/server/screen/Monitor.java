package com.jcope.vnc.server.screen;

import static com.jcope.debug.Debug.assert_;

import java.awt.Rectangle;
import java.util.ArrayList;

import com.jcope.debug.LLog;
import com.jcope.vnc.server.ClientHandler;
import com.jcope.vnc.server.DirectRobot;
import com.jcope.vnc.server.StateMachine;
import com.jcope.vnc.shared.StateMachine.SERVER_EVENT;

/**
 * 
 * @author Joseph Copenhaver
 * 
 * This class will contain registered listener interfaces that
 * get notified when a particular screen changes graphically.
 * 
 * For each ScreenMonitor there is exactly one screen device
 * being sampled for which there can be one or more listening
 * components.
 * 
 * Components will be notified when a segment of a screen changes.
 * It is then the responsibility of the listening component to fetch
 * from this class the data segment of interest.
 * 
 * Should all listeners be terminated, then the Screen Monitor shall
 * terminate and be ready for garbage collection.
 * 
 * Let each screen be broken up into segmentWidth by segmentHeight pixel segments.
 * 
 * Let each segment be assigned an ID from left to right, top down where the first tile is ID 0
 * Let segment ID -1 indicate the collection of segments as a whole (The entire screen)
 *
 */

public class Monitor extends Thread
{
	public static final long refreshMS = 1000;
	private int screenX, screenY, segmentWidth, segmentHeight, maxSegmentNumPixels, numHorizontalSegments, numVerticalSegments, numSegments, maxHorizontalSegmentIdx, maxVerticalSegmentIdx, maxSegmentID, lastSegmentWidth, lastSegmentHeight, bottomRowNumPixels, rightColNumPixels, bottomRightSegmentNumPixels;
	private Integer screenWidth = null, screenHeight;
	private ArrayList<ClientHandler> clients;
	private DirectRobot dirbot;
	private int[][] segments;
	private boolean[] changedSegments;
	private volatile boolean stopped = Boolean.FALSE;
	private volatile boolean joined = Boolean.FALSE;
	
	public Monitor(int segmentWidth, int segmentHeight, DirectRobot dirbot, ArrayList<ClientHandler> clients)
	{
		super(String.format("Monitor: %s", dirbot.toString()));
		this.segmentWidth = segmentWidth;
		this.segmentHeight = segmentHeight;
		this.dirbot = dirbot;
		this.clients = clients;
		maxSegmentNumPixels = segmentWidth * segmentHeight;
		syncBounds();
	}
	
	private void syncBounds()
	{
		Integer lastWidth = screenWidth;
		Integer lastHeight = screenHeight;
		Rectangle bounds = getScreenBounds();
		screenX = bounds.x;
		screenY = bounds.y;
		screenWidth = bounds.width;
		screenHeight = bounds.height;
		if (lastWidth == null || lastWidth != screenWidth || lastHeight != screenHeight)
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
			bottomRowNumPixels = segmentWidth * lastSegmentHeight;
			rightColNumPixels = lastSegmentWidth * segmentHeight;
			bottomRightSegmentNumPixels = lastSegmentWidth * lastSegmentHeight;
			segments = new int[numSegments][];
			changedSegments = new boolean[numSegments];
			for (int i=0; i<numSegments; i++)
			{
				segments[i] = new int[getSegmentPixelCount(i)];
				changedSegments[i] = Boolean.FALSE;
			}
			if (lastWidth != null)
			{
				// TODO: provide ability to lock a set of clients
				for (ClientHandler client : clients)
				{
					StateMachine.handleServerEvent(client, SERVER_EVENT.SCREEN_RESIZED, screenWidth, screenHeight);
				}
			}
		}
	}
	
	public void run()
	{
		// detect change in a segment of the configured screen
		// notify all listeners of the changed segment
		
		boolean changed;
		
		int[] buffer = new int[maxSegmentNumPixels];
		int[] segmentDim = new int[2];
		int x, y;
		long startAt, timeConsumed;
		
		try
		{
			while (!stopped)
			{
				startAt = System.currentTimeMillis();
				
				changed = Boolean.FALSE;
				
				dirbot.markRGBCacheDirty();
				
				for (int i=0; i<=maxSegmentID; i++)
				{
					getAbsSegmentPos(i, segmentDim);
					x = segmentDim[0];
					y = segmentDim[1];
					getSegmentDim(i, segmentDim);
					dirbot.getRGBPixels(x, y, segmentDim[0], segmentDim[1], buffer);
					if (copyIntArray(segments[i], buffer, segments[i].length))
					{
						changed = Boolean.TRUE;
						changedSegments[i] = Boolean.TRUE;
					}
				}
				
				if (changed)
				{
					for (ClientHandler client : clients)
					{
						ScreenListener l = client.getScreenListener(dirbot);
						for (int i=0; i<changedSegments.length; i++)
						{
							if (changedSegments[i])
							{
								l.onScreenChange(i);
							}
						}
					}
					for (int i=0; i<changedSegments.length; i++)
					{
						changedSegments[i] = Boolean.FALSE;
					}
				}
				
				timeConsumed = System.currentTimeMillis() - startAt;
				
				if (timeConsumed < refreshMS)
				{
					try
					{
						sleep(refreshMS - timeConsumed);
					}
					catch (InterruptedException e)
					{
						LLog.e(e);
					}
				}
			}
		}
		finally {
			
			stopped = Boolean.TRUE;
			joined = Boolean.TRUE;
		}
	}
	
	public void sendDisplayInitEvents(ClientHandler client)
	{
	    Rectangle bounds = getScreenBounds();
	    client.sendEvent(SERVER_EVENT.SCREEN_RESIZED, bounds.width, bounds.height);
	    client.sendEvent(SERVER_EVENT.SCREEN_SEGMENT_SIZE_UPDATE, segmentWidth, segmentHeight);
	}
	
	private boolean copyIntArray(int[] dst, int[] src, int length)
	{
		boolean rval = Boolean.FALSE;
		
		for (int i=0; i<length; i++)
		{
			if (dst[i] != src[i])
			{
				dst[i] = src[i];
				rval = Boolean.TRUE;
			}
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
	
	public void getSegmentDim(int segmentID, int[] dim)
	{
		assert_(segmentID >= 0);
		assert_(segmentID <= maxSegmentID);
		assert_(dim.length >= 2);
		
		int x, y;
		getSegmentPos(segmentID, dim);
		x = dim[0];
		y = dim[1];
		
		dim[0] = (x == maxHorizontalSegmentIdx) ? lastSegmentWidth : segmentWidth;
		dim[1] = (y == maxVerticalSegmentIdx) ? lastSegmentHeight : segmentHeight;
	}
	
	public void getAbsSegmentPos(int segmentID, int[] absPos)
	{
		assert_(segmentID >= 0);
		assert_(segmentID <= maxSegmentID);
		assert_(absPos.length >= 2);
		
		getSegmentPos(segmentID, absPos);
		absPos[0] = (absPos[0] * segmentWidth) + screenX;
		absPos[1] = (absPos[1] * segmentHeight) + screenY;
	}
	
	public void getSegmentPos(int segmentID, int[] pos)
	{
		assert_(segmentID >= 0);
		assert_(segmentID <= maxSegmentID);
		assert_(pos.length >= 2);
		
		pos[0] = segmentID%numHorizontalSegments;
		pos[1] = segmentID/numHorizontalSegments;
	}
	
	public int getSegmentPixelCount(int segmentID)
	{
		assert_(segmentID >= 0);
		assert_(segmentID <= maxSegmentID);
		
		int rval;
		int[] pos = new int[2];
		getSegmentPos(segmentID, pos);
		
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
	
	public int getMaxSegmentPixelCount()
	{
		return maxSegmentNumPixels;
	}
	
	public int getSegmentWidth()
	{
		return segmentWidth;
	}
	
	public int getSegmentHeight()
	{
		return segmentHeight;
	}
	
	public Rectangle getScreenBounds()
	{
		return dirbot.getScreenBounds();
	}
	
	public int getSegmentCount()
	{
		return numSegments;
	}
	
	private void signalStop()
	{
		stopped = true;
	}
	
	public boolean isRunning()
	{
		return !stopped;
	}
	
	public boolean isJoined()
	{
		return joined;
	}
	
	public void kill()
	{
		signalStop();
	}

    public int[] getSegment(int segmentID)
    {
        int[] rval = (segmentID == -1) ? dirbot.getRGBPixels() : segments[segmentID];
        
        return rval;
    }

}
