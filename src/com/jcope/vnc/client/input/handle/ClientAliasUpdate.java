package com.jcope.vnc.client.input.handle;

import static com.jcope.debug.Debug.assert_;

import com.jcope.vnc.client.MainFrame;
import com.jcope.vnc.client.StateMachine;
import com.jcope.vnc.client.input.Handle;

public class ClientAliasUpdate extends Handle {

    @Override
    public void handle(StateMachine stateMachine, Object[] args) {
        assert_(args != null);
        assert_(args.length == 2);
        assert_(args[0] instanceof String);
        assert_(args[1] instanceof Boolean);

        String alias = (String) args[0];
        boolean isCurval = (Boolean) args[1];

        MainFrame frame = stateMachine.getFrame();
        String curAlias = frame.getAlias();
        if (isCurval) {
            if (!alias.equals(curAlias)) {
                frame.setAlias(alias);
            }
        } else {
            frame.setAlias(null);
        }
    }

}
