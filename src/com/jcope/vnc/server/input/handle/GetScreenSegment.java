package com.jcope.vnc.server.input.handle;

import static com.jcope.debug.Debug.assert_;

import com.jcope.util.FixedLengthBitSet;
import com.jcope.vnc.server.ClientHandler;
import com.jcope.vnc.shared.StateMachine.SERVER_EVENT;
import com.jcope.vnc.server.input.Handle;

public class GetScreenSegment extends Handle
{
    
    @Override
    public void handle(ClientHandler client, Object[] args)
    {
        assert_(args != null);
        assert_(args.length == 1);
        Object arg0 = args[0];
        assert_(((arg0 instanceof Integer) && ((Integer)arg0) == -1) || arg0 instanceof FixedLengthBitSet);
        
        if (arg0 instanceof Integer)
        {
            Object solidColorOrPixelArray = client.getSegmentOptimized(-1);
            client.sendEvent(SERVER_EVENT.SCREEN_SEGMENT_UPDATE, -1, solidColorOrPixelArray);
            return;
        }
        
        FixedLengthBitSet flbs = (FixedLengthBitSet) arg0;
        
        for (int segmentID = flbs.nextSetBit(0); segmentID >= 0; segmentID = flbs.nextSetBit(segmentID + 1))
        {
            Object solidColorOrPixelArray = client.getSegmentOptimized(segmentID);
            client.sendEvent(SERVER_EVENT.SCREEN_SEGMENT_UPDATE, segmentID, solidColorOrPixelArray);
        }
    }
    
}
