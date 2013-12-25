package com.jcope.vnc.shared;

public class StateMachine
{
	public enum CONNECTION_STATE
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
	
	public enum CLIENT_EVENT
	{
		SELECT_SESSION_TYPE,
		OFFER_SECURITY_TOKEN,
		SELECT_SCREEN,
		GET_SCREEN_SEGMENT,
		OFFER_INPUT,
		REQUEST_ALIAS,
		SEND_CHAT_MSG,
		ENABLE_ALIAS_MONITOR,
		ENABLE_CONNECTION_MONITOR
	};
	
	public enum SERVER_EVENT
	{
	    AUTHORIZATION_UPDATE, // Response to client event OFFER_SECURITY_TOKEN
	    
	    
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
		CONNECTION_CLOSED;

        public boolean isSerial()
        {
            Boolean rval = null;
            
            switch (this)
            {
                // clients should only be notified of the
                // latest version of these events
                case SCREEN_SEGMENT_CHANGED:
                case SCREEN_SEGMENT_UPDATE:
                case CURSOR_GONE:
                case CURSOR_MOVE:
                case NUM_SCREENS_CHANGED:
                case SCREEN_GONE:
                case SCREEN_RESIZED:
                case AUTHORIZATION_UPDATE:
                case SCREEN_SEGMENT_SIZE_UPDATE:
                    rval = false;
                    break;
                
                // clients need to get each of these events in turn
                // no collapsing or dropping
                case ALIAS_DISCONNECTED:
                case ALIAS_REGISTERED:
                case ALIAS_UNREGISTERED:
                case CHAT_MSG_TO_ALL:
                case CHAT_MSG_TO_USER:
                case CONNECTION_CLOSED:
                case CONNECTION_ESTABLISHED:
                case FAILED_AUTHORIZATION:
                case ALIAS_CHANGED:
                    rval = true;
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
                    rval = true;
                    break;
                
                // events that either have no parameterized
                // information or parameters that are handled
                // in such a way that they never shift
                case CURSOR_GONE:
                case SCREEN_GONE:
                case SCREEN_SEGMENT_CHANGED:
                    rval = false;
                    break;
            }
            
            return rval;
        }
	};
}
