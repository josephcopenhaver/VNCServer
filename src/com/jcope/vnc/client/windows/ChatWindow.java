package com.jcope.vnc.client.windows;

import javax.swing.JFrame;

import com.jcope.vnc.client.MainFrame;

public class ChatWindow extends JFrame {
    /**
     * Generated Value
     */
    private static final long serialVersionUID = -5046705738778651186L;

    private static ChatWindow[] selfRef = new ChatWindow[] { null };

    private ChatWindow(MainFrame mainFrame) {
        // TODO: complete
    }

    public ChatWindow getInstance() {
        ChatWindow rval = selfRef[0];

        if (rval == null) {
            synchronized (selfRef) {
                rval = selfRef[0];
                if (rval == null) {
                    rval = new ChatWindow(MainFrame.getCachedInstance());
                }
            }
        }

        return rval;
    }
}
