package com.jcope.vnc.client.input.handle;

import static com.jcope.debug.Debug.assert_;

import com.jcope.ui.JCOptionPane;
import com.jcope.vnc.client.StateMachine;
import com.jcope.vnc.client.input.Handle;

public class AuthorizationUpdate extends Handle {

    @Override
    public void handle(StateMachine stateMachine, Object[] args) {
        assert_(args != null);
        assert_(args.length == 1);
        assert_(args[0] instanceof Boolean);

        boolean isAuthorized = (Boolean) args[0];

        if (!isAuthorized) {
            stateMachine.disconnect();
            JCOptionPane.showMessageDialog(stateMachine.getFrame(),
                    "Incorrect password or selected display not an option",
                    "Access Denied", JCOptionPane.ERROR_MESSAGE);
        }
    }

}
