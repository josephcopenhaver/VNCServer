package com.jcope.vnc.server.screen;

import static com.jcope.debug.Debug.assert_;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.TreeSet;
import java.util.concurrent.Semaphore;

import com.jcope.debug.LLog;
import com.jcope.util.FixedLengthBitSet;
import com.jcope.util.SegmentationInfo;
import com.jcope.vnc.Server.SERVER_PROPERTIES;
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
	public static final long NO_LISTENER_MS = 5000; // dummy value to allow things to settle into nop state
	private static final boolean OBEY_SPEED_LIMITS = (Boolean) SERVER_PROPERTIES.OBEY_SPEED_LIMITS.getValue();
    private static final long MIN_REFRESH_MS = (Long) SERVER_PROPERTIES.MIN_MONITOR_SCANNING_PERIOD.getValue();
    int screenX, screenY;
    SegmentationInfo segInfo = new SegmentationInfo();
    private Integer screenWidth = null, screenHeight;
    private ArrayList<ClientHandler> clients;
    private DirectRobot dirbot;
    private int[][] segments;
    private Integer[][] solidSegments;
    private FixedLengthBitSet changedSegments;
    private volatile boolean stopped = Boolean.FALSE;
    private volatile boolean joined = Boolean.FALSE;
    private Boolean mouseOnMyScreen = null;
    private final Point mouseLocation = new Point();
    
    private Semaphore limitLock = new Semaphore(1, true);
    private TreeSet<Long> limitTreeSet = new TreeSet<Long>();
    private volatile long refreshMS;
    
    private Semaphore unpausedClientSema = new Semaphore(0, true);
    // TODO: refactor segmentSemas out by adding a concept of segments with volatile solidity indicators,
    // thus no longer making a segment typed and deferring this optimization to serialization time
    private Semaphore[] segmentSemas;
    
    public Monitor(int segmentWidth, int segmentHeight, DirectRobot dirbot, ArrayList<ClientHandler> clients)
    {
        super(String.format("Monitor: %s", dirbot.toString()));
        segInfo.segmentWidth = segmentWidth;
        segInfo.segmentHeight = segmentHeight;
        this.dirbot = dirbot;
        this.clients = clients;
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
            segInfo.loadConfig(screenWidth, screenHeight, segInfo.segmentWidth, segInfo.segmentHeight);
            segmentSemas = new Semaphore[segInfo.numSegments];
            segments = new int[segInfo.numSegments][];
            solidSegments = new Integer[segInfo.numSegments][];
            changedSegments = new FixedLengthBitSet(segInfo.numSegments);
            for (int i=0; i<segInfo.numSegments; i++)
            {
            	solidSegments[i] = new Integer[]{null};
                segments[i] = new int[getSegmentPixelCount(i)];
                segmentSemas[i] = new Semaphore(1, true);
            }
            if (lastWidth != null)
            {
                // TODO: provide ability to lock a set of clients
                StateMachine.handleServerEvent(clients, SERVER_EVENT.SCREEN_RESIZED, screenWidth, screenHeight);
            }
        }
    }
    
    private void syncMouse()
    {
        Boolean mouseWasOnMyScreen = mouseOnMyScreen;
        int lastX = mouseLocation.x, lastY = mouseLocation.y;
        mouseOnMyScreen = (DirectRobot.getMouseInfo(mouseLocation) == dirbot.device);
        if (mouseOnMyScreen)
        {
            if (mouseWasOnMyScreen == null || lastX != mouseLocation.x || lastY != mouseLocation.y)
            {
                StateMachine.handleServerEvent(clients, SERVER_EVENT.CURSOR_MOVE, Integer.valueOf(mouseLocation.x), Integer.valueOf(mouseLocation.y));
            }
        }
        else if (mouseWasOnMyScreen != null && mouseWasOnMyScreen)
        {
            StateMachine.handleServerEvent(clients, SERVER_EVENT.CURSOR_GONE);
        }
    }
    
    public void run()
    {
        // detect change in a segment of the configured screen
        // notify all listeners of the changed segment
        
        boolean changed;
        boolean discrete_change;
        
        int[] buffer = new int[segInfo.maxSegmentNumPixels];
        int[] segmentDim = new int[2];
        int x, y;
        long startAt, timeConsumed;
        ArrayList<ClientHandler> newClients = new ArrayList<ClientHandler>();
        
        startAt = 0;
        
        try
        {
            while (true)
            {
            	try {
					unpausedClientSema.acquire();
				} catch (InterruptedException e) {
					LLog.e(e);
				}
            	try {
	            	if (stopped)
	            	{
	            		break;
	            	}
	            	if (OBEY_SPEED_LIMITS)
	            	{
	            		startAt = System.currentTimeMillis();
	            	}
	                
	                syncMouse();
	                
	                changed = Boolean.FALSE;
	                
	                dirbot.markRGBCacheDirty();
	                
	                for (int i=0; i<=segInfo.maxSegmentID; i++)
	                {
	                    getSegmentPos(i, segmentDim);
	                    x = segmentDim[0];
	                    y = segmentDim[1];
	                    getSegmentDim(i, segmentDim);
	                    dirbot.getRGBPixels(x, y, segmentDim[0], segmentDim[1], buffer);
	                    try {
							segmentSemas[i].acquire();
						} catch (InterruptedException e) {
							LLog.e(e);
						}
	                    try
	                    {
	                    	synchronized(segments[i]){synchronized(solidSegments[i]){
		                    	discrete_change = copyIntArray(segments[i], buffer, segments[i].length, solidSegments[i]);
		                    }}
	                    }
	                    finally {
	                    	segmentSemas[i].release();
	                    }
	                    if (discrete_change)
	                    {
	                        changed = Boolean.TRUE;
	                        changedSegments.set(i, Boolean.TRUE);
	                    }
	                }
	                
	                for (ClientHandler client : clients)
	                {
	                    if (client.getIsNewFlag())
	                    {
	                        newClients.add(client);
	                    }
	                }
	                
	                if (changed)
	                {
	                    FixedLengthBitSet tmp = changedSegments.clone();
	                    for (ClientHandler client : clients)
	                    {
	                        if (client.getIsNewFlag())
	                        {
	                            continue;
	                        }
	                        ScreenListener l = client.getScreenListener(dirbot);
	                        l.onScreenChange(tmp);
	                    }
	                    changedSegments.fill(Boolean.FALSE);
	                }
	                
	                if (newClients.size() > 0)
	                {
	                    FixedLengthBitSet tmp = new FixedLengthBitSet(changedSegments.length, Boolean.TRUE);
	                    for (ClientHandler client : newClients)
	                    {
	                        client.setIsNewFlag(Boolean.FALSE);
	                        ScreenListener l = client.getScreenListener(dirbot);
	                        l.onScreenChange(tmp);
	                    }
	                    newClients.clear();
	                }
	                
	                if (OBEY_SPEED_LIMITS)
	                {
		                timeConsumed = System.currentTimeMillis() - startAt;
		                
		                long l_refreshMS = refreshMS;
		                
		                if (timeConsumed < l_refreshMS)
		                {
		                    try
		                    {
		                        sleep(l_refreshMS - timeConsumed);
		                    }
		                    catch (InterruptedException e)
		                    {
		                        LLog.e(e);
		                    }
		                }
	                }
            	}
                finally {
                	unpausedClientSema.release();
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
        client.sendEvent(SERVER_EVENT.SCREEN_SEGMENT_SIZE_UPDATE, segInfo.segmentWidth, segInfo.segmentHeight);
        if (mouseOnMyScreen != null && mouseOnMyScreen)
        {
            client.sendEvent(SERVER_EVENT.CURSOR_MOVE, Integer.valueOf(mouseLocation.x), Integer.valueOf(mouseLocation.y));
        }
    }
    
    private boolean isOneColor(int[] ints, int idx, int sentinelIdx)
    {
    	if (idx + 1 == sentinelIdx)
    	{
    		return true;
    	}
    	int color = ints[idx];
    	idx++;
    	while (idx < sentinelIdx)
    	{
    		if (ints[idx++] == color)
    		{
    			continue;
    		}
    		return false;
    	}
    	return true;
    }
    
    /**
     * Eratta: src array content MAY CHANGE as a result of this function!
     * It is faster to fill a buffer with a SOLID color and compare arrays
     * using a syscall than it is to iterate over the array and compare
     * individual elements with a sample color.
     * 
     * Note that this can only occur when the src and dst buffers are length
     * aligned and the length parameter is aligned with them as well.
     * 
     * @param dst
     * @param src
     * @param length
     * @param cachedSolidColor
     * @return true iff. something differs
     */
    private boolean copyIntArray(int[] dst, int[] src, int length, Integer[] cachedSolidColor)
    {
    	if (length <= 0)
    	{
    		if (length == 0)
    		{
    			return false;
    		}
    		throw new IllegalArgumentException();
    	}
        
        if (cachedSolidColor != null && cachedSolidColor.length > 0)
        {
        	// handle solid color concerns
        	if (dst.length == src.length && length == src.length)
        	{
        		boolean isDiff = !Arrays.equals(src, dst);
        		if (isDiff)
        		{
        			System.arraycopy(src, 0, dst, 0, src.length);
        		}
        		Arrays.fill(src, dst[0]);
        		cachedSolidColor[0] = Arrays.equals(src, dst) ? dst[0] : null;
        		return isDiff;
        	}
        	int i;
        	if (cachedSolidColor[0] != null)
        	{
        		// was a solid color in a former life
        		i = 0;
        		for (; i<length; i++)
        		{
            		if (dst[i] == src[i])
            		{
            			continue;
            		}
            		System.arraycopy(src, i, dst, i, length-i);
        			cachedSolidColor[0] = (i == 0 && isOneColor(dst, 0, length)) ? dst[0] : null;
        			return true;
        		}
        		return false;
        	}
        	// was never a solid color in a former life
        	if (src[0] != dst[0])
        	{
        		// only looking for solid colors now
        		System.arraycopy(src, 0, dst, 0, length);
        		cachedSolidColor[0] = isOneColor(dst, 0, length) ? dst[0] : null;
        		return true;
        	}
        	int solidColor = src[0];
        	i = 1;
        	for (; i<length; i++)
        	{
        		if (src[i] != dst[i])
        		{
        			//only looking for solid color now
        			System.arraycopy(src, i, dst, i, length-i);
        			cachedSolidColor[0] = isOneColor(dst, i-1, length) ? dst[0] : null;
        			return true;
        		}
        		if (src[i] != solidColor)
        		{
        			//only looking for diff now
        			cachedSolidColor[0] = null;
        			i++;
        			while (i < length)
        			{
        				if (src[i] == dst[i])
        				{
        					i++;
        					continue;
        				}
        				System.arraycopy(src, i, dst, i, length-i);
    					return true;
        			}
        			return false;
        		}
        	}
        	return false;
        }
    	// solid colors are not to be addressed
    	if (dst.length == src.length && length == src.length)
    	{
    		if (Arrays.equals(src, dst))
    		{
    			return false;
    		}
    		System.arraycopy(src, 0, dst, 0, src.length);
			return true;
    	}
		for (int i=0; i<length; i++)
		{
			if (dst[i] == src[i])
			{
				continue;
			}
			System.arraycopy(src, i, dst, i, length-i);
			return true;
		}
		return false;
    }
    
    public int getSegmentID(int x, int y)
    {
        int rval = segInfo.getSegmentID(x, y);
        
        return rval;
    }
    
    public void getSegmentDim(int segmentID, int[] dim)
    {
        segInfo.getDim(segmentID, dim);
    }
    
    public void getSegmentPos(int segmentID, int[] absPos)
    {
        segInfo.getPos(segmentID, absPos);
    }
    
    public void getSegmentIdxPos(int segmentID, int[] pos)
    {
        segInfo.getIdxPos(segmentID, pos);
    }
    
    public int getSegmentPixelCount(int segmentID)
    {
        int rval = segInfo.getSegmentPixelCount(segmentID);
        
        return rval;
    }
    
    public int getMaxSegmentPixelCount()
    {
        return segInfo.maxSegmentNumPixels;
    }
    
    public int getSegmentWidth()
    {
        return segInfo.segmentWidth;
    }
    
    public int getSegmentHeight()
    {
        return segInfo.segmentHeight;
    }
    
    public Rectangle getScreenBounds()
    {
        return dirbot.getScreenBounds();
    }
    
    public int getSegmentCount()
    {
        return segInfo.numSegments;
    }
    
    private void signalStop()
    {
        stopped = true;
        unpausedClientSema.release();
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
    
    public Object getSegmentOptimized(int segmentID)
    {
    	if (segmentID == -1)
    	{
    		return dirbot.getRGBPixels();
    	}
    	Object rval;
    	try {
			segmentSemas[segmentID].acquire();
		} catch (InterruptedException e) {
			LLog.e(e);
		}
    	try
        {
    		synchronized(segments[segmentID]){synchronized(solidSegments[segmentID]){
		        rval = solidSegments[segmentID][0];
		        
		        if (rval == null)
		        {
		            return segments[segmentID];
		        }
		        return rval;
        	}}
        }
        finally {
        	segmentSemas[segmentID].release();
        }
    }

    public void getOrigin(int[] pos)
    {
        assert_(pos != null);
        assert_(pos.length > 1);
        
        pos[0] = screenX;
        pos[1] = screenY;
    }

	public void throttle(boolean addPeriod, Long periodMS)
	{
		assert_(!addPeriod || periodMS != null);
		if (periodMS == null)
		{
			// refresh rate NOT affected by this value/config
			return;
		}
		if (periodMS < MIN_REFRESH_MS)
		{
			periodMS = MIN_REFRESH_MS;
		}
		try {
			limitLock.acquire();
		} catch (InterruptedException e) {
			LLog.e(e);
		}
		try
		{
			synchronized(limitTreeSet)
			{
				if (addPeriod)
				{
					limitTreeSet.add(periodMS);
					refreshMS = limitTreeSet.ceiling(0L);
					return;
				}
				else if (limitTreeSet.remove(periodMS))
				{
					if (limitTreeSet.isEmpty())
					{
						refreshMS = NO_LISTENER_MS;
						return;
					}
					refreshMS = limitTreeSet.ceiling(0L);
					return;
				}
				assert_(false);
			}
		}
		finally {
			limitLock.release();
		}
	}

	public void setPaused(boolean paused) {
		if (!paused)
		{
			unpausedClientSema.release();
		}
		else
		{
			try {
				unpausedClientSema.acquire();
			} catch (InterruptedException e) {
				LLog.e(e);
			}
		}
	}

}
