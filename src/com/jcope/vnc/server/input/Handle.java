package com.jcope.vnc.server.input;

import com.jcope.vnc.server.ClientHandler;

public abstract class Handle extends com.jcope.vnc.shared.input.Handle<ClientHandler>
{

    public Handle()
    {
        super(ClientHandler.class);
    }
    
}
