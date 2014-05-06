package com.jcope.vnc.server.input.handle;

import static com.jcope.debug.Debug.assert_;

import com.jcope.vnc.server.ClientHandler;
import com.jcope.vnc.shared.StateMachine.SERVER_EVENT;
import com.jcope.vnc.server.input.Handle;

public class AcknowledgeNonSerialEvent extends Handle
{
    @Override
    public void handle(ClientHandler client, Object[] args)
    {
        assert_(args != null);
        assert_(args.length > 0);
        assert_(args[0] instanceof SERVER_EVENT);
        
        SERVER_EVENT evt = (SERVER_EVENT) args[0];
        Integer segmentID = null;
        
        assert_(!evt.isSerial());
        
        switch(evt)
        {
            case ALIAS_CHANGED:
            case ALIAS_DISCONNECTED:
            case ALIAS_REGISTERED:
            case ALIAS_UNREGISTERED:
            case AUTHORIZATION_UPDATE:
            case CHAT_MSG_TO_ALL:
            case CHAT_MSG_TO_USER:
            case CLIENT_ALIAS_UPDATE:
            case CONNECTION_CLOSED:
            case CONNECTION_ESTABLISHED:
            case CURSOR_GONE:
            case CURSOR_MOVE:
            case FAILED_AUTHORIZATION:
            case NUM_SCREENS_CHANGED:
            case READ_INPUT_EVENTS:
            case SCREEN_GONE:
            case SCREEN_RESIZED:
            case SCREEN_SEGMENT_SIZE_UPDATE:
                assert_(args.length == 1);
                break;
            case SCREEN_SEGMENT_CHANGED:
                assert_(args.length == 2);
                assert_(args[1] instanceof Integer);
                segmentID = (Integer) args[1];
                break;
            case SCREEN_SEGMENT_UPDATE:
                assert_(Boolean.FALSE);
                // there is already a throttle for this
                break;
            default:
                assert_(Boolean.FALSE);
                break;
        }
        
        client.handleEventAck(evt, segmentID == null ? null : args, 1);
    }
}
