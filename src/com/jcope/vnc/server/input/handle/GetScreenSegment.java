package com.jcope.vnc.server.input.handle;

import static com.jcope.debug.Debug.assert_;

import com.jcope.util.FixedLengthBitSet;
import com.jcope.util.GraphicsSegment;
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
            client.sendEvent(SERVER_EVENT.ENTIRE_SCREEN_UPDATE);
            return;
        }
        
        FixedLengthBitSet flbs = (FixedLengthBitSet) arg0;
        
        client.subscribe(flbs);
        
        try {
            for (int segmentID = flbs.nextSetBit(0); segmentID >= 0; segmentID = flbs.nextSetBit(segmentID + 1))
            {
                GraphicsSegment graphicsSegment = client.getSegment(segmentID);
                client.sendEvent(SERVER_EVENT.SCREEN_SEGMENT_UPDATE, segmentID, graphicsSegment);
            }
        }
        finally {
            client.sendEvent(SERVER_EVENT.END_OF_FRAME);
        }
    }
    
}
