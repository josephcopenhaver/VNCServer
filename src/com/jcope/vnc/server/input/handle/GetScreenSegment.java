package com.jcope.vnc.server.input.handle;

import static com.jcope.debug.Debug.assert_;

import com.jcope.vnc.server.ClientHandler;
import com.jcope.vnc.shared.StateMachine.SERVER_EVENT;
import com.jcope.vnc.shared.input.Handle;

public class GetScreenSegment extends Handle<ClientHandler>
{

    public GetScreenSegment()
    {
        super(ClientHandler.class);
    }

    @Override
    public void handle(ClientHandler client, Object[] args)
    {
        assert_(args != null);
        assert_(args.length == 1);
        assert_(args[0] instanceof Integer);
        client.sendEvent(SERVER_EVENT.SCREEN_SEGMENT_UPDATE, args[0], client.getSegment((Integer) args[0]));
    }
}
