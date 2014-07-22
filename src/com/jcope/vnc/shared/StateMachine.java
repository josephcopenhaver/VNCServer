package com.jcope.vnc.shared;

import static com.jcope.debug.Debug.assert_;

public class StateMachine
{
	public static enum CONNECTION_STATE
	{
		INIT,
		SELECTING_SESSION_TYPE,
		AUTHENTICATING_INPUT_ENABLED,
		AUTHENTICATING_VIEW_ONLY,
		SELECTING_SCREEN_INPUT_ENABLED,
		SELECTING_SCREEN_VIEW_ONLY,
		VIEW_WITH_INPUT_ENABLED,
		VIEW_ONLY
	};
	
	public static enum CLIENT_EVENT
	{
		SELECT_SCREEN,
		GET_SCREEN_SEGMENT,
		OFFER_INPUT,
		REQUEST_ALIAS,
		SEND_CHAT_MSG,
		ENABLE_ALIAS_MONITOR,
		ENABLE_CONNECTION_MONITOR,
		ACKNOWLEDGE_NON_SERIAL_EVENT,
		GET_CLIPBOARD,
		CLIPBOARD_CHANGED,
		SET_CLIPBOARD
		
		;
	};
	
	public static enum SERVER_EVENT
	{
	    READ_INPUT_EVENTS, // response to client event OFFER_INPUT
	    
	    AUTHORIZATION_UPDATE,
	    CLIENT_ALIAS_UPDATE, // Response to client event REQUEST_ALIAS
	    
	    
		NUM_SCREENS_CHANGED,
		CURSOR_GONE,
		CURSOR_MOVE,
		SCREEN_SEGMENT_SIZE_UPDATE,
		SCREEN_SEGMENT_UPDATE, // Response to client event GET_SCREEN_SEGMENT
		SCREEN_SEGMENT_CHANGED,
		//ENTIRE_SCREEN_UPDATE, // collapsed into SCREEN_SEGEMENT_* (ID = -1)
		//ENTIRE_SCREEN_CHANGED,
		SCREEN_RESIZED,
		SCREEN_GONE,
		CHAT_MSG_TO_ALL, // includes from and text message
		CHAT_MSG_TO_USER, // for debug purposes, should assert only the target alias
		//gets the message
		
		// for identifying others listening in
		ALIAS_REGISTERED,
		ALIAS_UNREGISTERED,
		ALIAS_DISCONNECTED,
		ALIAS_CHANGED,
		
		// for monitoring connections to the server
		CONNECTION_ESTABLISHED, // server socket was bound
		FAILED_AUTHORIZATION, // a user failed to log in
		CONNECTION_CLOSED,
		
		GET_CLIPBOARD, // loads clipboard from client
		CLIPBOARD_CHANGED, // notifies client that server clipboard contents have changed
		SET_CLIPBOARD // sends clipboard contents to clients that have synchronization enabled
		
		;
	    
	    private static Integer maxOrdinal = null;
        
        public boolean isSerial()
        {
            Boolean rval = null;
            
            switch (this)
            {
                // clients should only be notified of the
                // latest version of these events
                case SCREEN_SEGMENT_UPDATE:
                case CURSOR_GONE:
                case CURSOR_MOVE:
                case NUM_SCREENS_CHANGED:
                case SCREEN_GONE:
                case SCREEN_RESIZED:
                case AUTHORIZATION_UPDATE:
                case SCREEN_SEGMENT_SIZE_UPDATE:
                case CLIENT_ALIAS_UPDATE:
                case READ_INPUT_EVENTS:
                case CLIPBOARD_CHANGED:
                case GET_CLIPBOARD:
                case SET_CLIPBOARD:
                    rval = Boolean.FALSE;
                    break;
                
                // clients need to get each of these events in turn
                // no collapsing or dropping
                case SCREEN_SEGMENT_CHANGED:
                case ALIAS_DISCONNECTED:
                case ALIAS_REGISTERED:
                case ALIAS_UNREGISTERED:
                case CHAT_MSG_TO_ALL:
                case CHAT_MSG_TO_USER:
                case CONNECTION_CLOSED:
                case CONNECTION_ESTABLISHED:
                case FAILED_AUTHORIZATION:
                case ALIAS_CHANGED:
                    rval = Boolean.TRUE;
                    break;
            }
            
            return rval;
        }

        public boolean hasMutableArgs()
        {
            Boolean rval = null;
            
            switch (this)
            {
                // events that have parameterized
                // information that may shift
                // when the event is fired
                case ALIAS_DISCONNECTED:
                case ALIAS_REGISTERED:
                case ALIAS_UNREGISTERED:
                case CHAT_MSG_TO_ALL:
                case CHAT_MSG_TO_USER:
                case CURSOR_MOVE:
                case NUM_SCREENS_CHANGED:
                case SCREEN_RESIZED:
                case SCREEN_SEGMENT_UPDATE:
                case ALIAS_CHANGED:
                case CONNECTION_ESTABLISHED: // should contain WHO
                case CONNECTION_CLOSED: // should contain WHO
                case FAILED_AUTHORIZATION: // should contain WHO
                case AUTHORIZATION_UPDATE:
                case SCREEN_SEGMENT_SIZE_UPDATE:
                case CLIENT_ALIAS_UPDATE:
                case READ_INPUT_EVENTS:
                case SET_CLIPBOARD:
                case SCREEN_SEGMENT_CHANGED:
                    rval = Boolean.TRUE;
                    break;
                
                // events that either have no parameterized
                // information or parameters that are handled
                // in such a way that they never shift
                case CURSOR_GONE:
                case SCREEN_GONE:
                case CLIPBOARD_CHANGED:
                case GET_CLIPBOARD:
                    rval = Boolean.FALSE;
                    break;
            }
            
            return rval;
        }

        public static int getMaxOrdinal()
        {
            int rval;
            
            if (maxOrdinal == null)
            {
                int ordVal;
                rval = -1;
                
                for (SERVER_EVENT event : SERVER_EVENT.values())
                {
                    if (rval < (ordVal = event.ordinal()))
                    {
                        rval = ordVal;
                    }
                }
                
                assert_(rval >= 0);
                
                maxOrdinal = rval;
            }
            else
            {
                rval = maxOrdinal;
            }
            
            return rval;
        }
	};
}
