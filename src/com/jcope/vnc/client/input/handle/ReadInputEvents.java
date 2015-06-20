package com.jcope.vnc.client.input.handle;

import static com.jcope.debug.Debug.assert_;

import com.jcope.vnc.client.StateMachine;
import com.jcope.vnc.client.input.Handle;
import com.jcope.vnc.shared.InputEvent;
import com.jcope.vnc.shared.StateMachine.CLIENT_EVENT;

public class ReadInputEvents extends Handle {

    @Override
    public void handle(StateMachine stateMachine, Object[] args) {
        assert_(args != null);
        assert_(args.length == 1);
        assert_(args[0] instanceof Integer);

        int avail = (Integer) args[0];

        InputEvent[] eventArgs = stateMachine.popEvents(avail);

        if (eventArgs != null && eventArgs.length > 0) {
            stateMachine.sendEvent(CLIENT_EVENT.OFFER_INPUT, Boolean.FALSE,
                    eventArgs);
        }
    }

}
