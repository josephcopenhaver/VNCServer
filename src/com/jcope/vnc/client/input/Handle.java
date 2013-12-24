package com.jcope.vnc.client.input;

import com.jcope.vnc.client.StateMachine;

public abstract class Handle extends com.jcope.vnc.shared.input.Handle<StateMachine>
{

    public Handle()
    {
        super(StateMachine.class);
    }
    
}
