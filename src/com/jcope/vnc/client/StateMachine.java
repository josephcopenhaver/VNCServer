package com.jcope.vnc.client;

import static com.jcope.debug.Debug.DEBUG;
import static com.jcope.debug.Debug.assert_;
import static com.jcope.ui.util.Style.positionThenShow;
import static com.jcope.vnc.shared.InputEventInfo.MAX_QUEUE_SIZE;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;

import javax.swing.SwingUtilities;

import com.jcope.debug.LLog;
import com.jcope.ui.ImagePanel;
import com.jcope.ui.JCOptionPane;
import com.jcope.util.FixedLengthBitSet;
import com.jcope.util.TaskDispatcher;
import com.jcope.vnc.Client.CLIENT_PROPERTIES;
import com.jcope.vnc.client.dialogs.ConnectionDialog;
import com.jcope.vnc.client.dialogs.ConnectionDialog.InvalidConnectionConfigurationException;
import com.jcope.vnc.client.input.Handler;
import com.jcope.vnc.client.input.handle.ScreenSegmentChanged;
import com.jcope.vnc.shared.AccessModes.ACCESS_MODE;
import com.jcope.vnc.shared.IOERunnable;
import com.jcope.vnc.shared.InputEvent;
import com.jcope.vnc.shared.Msg;
import com.jcope.vnc.shared.Msg.CompressedObjectReader;
import com.jcope.vnc.shared.StateMachine.CLIENT_EVENT;
import com.jcope.vnc.shared.StateMachine.SERVER_EVENT;

public class StateMachine implements Runnable {
    private MainFrame frame;

    private ACCESS_MODE accessMode = null;

    private Socket socket;
    private volatile BufferedOutputStream out;
    private volatile Exception whyFailed = null;

    private Semaphore setWhyFailedLock = new Semaphore(1, true);

    private Semaphore inputHandlingSema = new Semaphore(1, true);

    private Semaphore sendSema = new Semaphore(1, true);
    private TaskDispatcher<Integer> dispatcher = new TaskDispatcher<Integer>(
            "Client output dispatcher");

    private volatile int gui_tid = -1;
    private TaskDispatcher<Integer> gui_dispatcher = new TaskDispatcher<Integer>(
            "GUI dispatcher");

    private Semaphore queueAccessSema = new Semaphore(1, true);
    private volatile ArrayList<InputEvent> outQueue = null;

    private Semaphore iconifiedSema = new Semaphore(1, true);

    private volatile FixedLengthBitSet changedSegments = null;
    public final Semaphore changedSegmentsSema = new Semaphore(1, true);

    public final Semaphore processingFrameSema = new Semaphore(1, true);

    public StateMachine() throws UnknownHostException, IOException {
        frame = new MainFrame(this);
        positionThenShow(frame);
    }

    private void setWhyFailed(Exception exception) {
        if (whyFailed == null) {
            try {
                setWhyFailedLock.acquire();
            } catch (InterruptedException e) {
                LLog.e(e);
            }
            try {
                if (whyFailed == null) {
                    whyFailed = exception;
                }
            } finally {
                setWhyFailedLock.release();
            }
        }
    }

    public void handleUserAction(Runnable r) {
        try {
            inputHandlingSema.acquire();
        } catch (InterruptedException e) {
            LLog.e(e);
        }
        try {
            r.run();
        } finally {
            inputHandlingSema.release();
        }
    }

    public void run() {
        boolean tryConnect, wasConnected;
        IOException usrCancel = new IOException(
                "User cancelled connection attempt");
        do {
            tryConnect = Boolean.FALSE;
            wasConnected = Boolean.FALSE;
            socket = null;
            OutputStream os = null;
            InputStream is = null;
            out = null;
            BufferedInputStream in = null;
            do {
                try {
                    accessMode = null;
                    ConnectionDialog connectionDialog = new ConnectionDialog(
                            frame);
                    final String hashedPassword;

                    try {
                        if (JCOptionPane.OK_OPTION != connectionDialog
                                .showInputDialog()) {
                            throw usrCancel;
                        }

                        socket = new Socket(
                                (String) CLIENT_PROPERTIES.REMOTE_ADDRESS
                                        .getValue(),
                                (Integer) CLIENT_PROPERTIES.REMOTE_PORT
                                        .getValue());
                        wasConnected = Boolean.TRUE;
                        os = socket.getOutputStream();
                        out = new BufferedOutputStream(os);
                        is = socket.getInputStream();
                        in = new BufferedInputStream(is);

                        accessMode = connectionDialog.getAccessMode();
                        hashedPassword = connectionDialog.removePasswordHash();
                    } finally {
                        connectionDialog.dispose();
                    }

                    connectionDialog = null;

                    SwingUtilities.invokeLater(new Runnable() {

                        @Override
                        public void run() {
                            int selectedScreen = (Integer) CLIENT_PROPERTIES.REMOTE_DISPLAY_NUM
                                    .getValue();
                            sendEvent(CLIENT_EVENT.SELECT_SCREEN,
                                    selectedScreen, accessMode,
                                    CLIENT_PROPERTIES.MONITOR_SCANNING_PERIOD
                                            .getValue(), hashedPassword);
                        }

                    });

                    CompressedObjectReader reader = new CompressedObjectReader();
                    Object obj;

                    while ((obj = reader.readObject(in)) != null) {
                        try {
                            inputHandlingSema.acquire();
                        } catch (InterruptedException e) {
                            LLog.e(e);
                        }
                        try {
                            handleServerEvent(obj);
                        } finally {
                            obj = null;
                            inputHandlingSema.release();
                        }
                    }
                    throw new IOException("Connection reset by peer");
                } catch (UnknownHostException e) {
                    setWhyFailed(e);
                } catch (IOException e) {
                    setWhyFailed(e);
                } catch (InvalidConnectionConfigurationException e) {
                    setWhyFailed(e);
                } finally {
                    if (out != null) {
                        try {
                            out.close();
                        } catch (Exception e) {
                        }
                    }
                    if (os != null) {
                        try {
                            os.close();
                        } catch (Exception e) {
                        }
                    }
                    if (in != null) {
                        try {
                            in.close();
                        } catch (Exception e) {
                        }
                    }
                    if (is != null) {
                        try {
                            is.close();
                        } catch (Exception e) {
                        }
                    }
                    disconnect();
                }
            } while (Boolean.FALSE);
            do {
                if (null == whyFailed) {
                    break;
                }
                if (usrCancel == whyFailed) {
                    whyFailed = null;
                    if (DEBUG) {
                        LLog.w(usrCancel.getMessage());
                    }
                    break;
                }
                try {
                    String msg = wasConnected ? "Connection lost, reconnect?"
                            : "Failed to connect, retry?";
                    int result = JCOptionPane.showConfirmDialog(frame, msg,
                            msg, JCOptionPane.ERROR_MESSAGE,
                            JCOptionPane.OK_CANCEL_OPTION);
                    if (JCOptionPane.OK_OPTION == result) {
                        tryConnect = Boolean.TRUE;
                    } else {
                        LLog.e(whyFailed, false);
                    }
                } finally {
                    whyFailed = null;
                }
            } while (Boolean.FALSE);
        } while (tryConnect);
        frame.dispose();
    }

    private void handleServerEvent(Object obj) {
        if (obj instanceof SERVER_EVENT) {
            _handleServerEvent((SERVER_EVENT) obj, null);
        } else {
            Msg msg = (Msg) obj;
            _handleServerEvent((SERVER_EVENT) msg.event, msg.args);
        }
    }

    private void _handleServerEvent(SERVER_EVENT event, Object[] args) {
        LLog.logEvent("Server", event, args);
        Handler.getInstance().handle(this, event, args);
    }

    public void sendEvent(CLIENT_EVENT event) {
        sendEvent(event, (Object[]) null);
    }

    public void sendEvent(final CLIENT_EVENT event, Object... args) {
        final IOERunnable msgAction;
        final Object[] f_args;
        if (event == CLIENT_EVENT.GET_SCREEN_SEGMENT && args == null) {
            args = new Object[] { changedSegments };
            f_args = args;
            msgAction = new IOERunnable() {

                @Override
                public void run() throws IOException {
                    try {
                        changedSegmentsSema.acquire();
                    } catch (InterruptedException e) {
                        LLog.e(e);
                    }
                    try {
                        changedSegments = null;
                    } finally {
                        changedSegmentsSema.release();
                    }
                    Msg.send(out, event, f_args);
                }
            };
        } else {
            assert_(event != CLIENT_EVENT.GET_SCREEN_SEGMENT
                    || (args != null && args.length == 1
                            && args[0] instanceof Integer && ((Integer) args[0]) == -1));
            f_args = args;
            msgAction = new IOERunnable() {

                @Override
                public void run() throws IOException {
                    Msg.send(out, event, f_args);
                }
            };
        }
        Runnable r = new Runnable() {

            @Override
            public void run() {
                if (out == null) {
                    return; // TODO: fix the fact that this is an event black
                            // hole
                }
                try {
                    sendSema.acquire();
                } catch (InterruptedException e) {
                    LLog.e(e);
                }
                boolean killConnection = true;
                try {
                    try {
                        msgAction.run();
                        if (dispatcher.isEmpty()) {
                            out.flush();
                        }
                        killConnection = false;
                    } catch (IOException e) {
                        setWhyFailed(e);
                    }
                } finally {
                    sendSema.release();
                    if (killConnection) {
                        disconnect();
                    }
                }
            }
        };

        dispatcher.dispatch(event.ordinal(), r);
    }

    public void disconnect() {
        try {
            try {
                ScreenSegmentChanged.segmentFetcher.clear();
            } finally {
                dispatcher.clear();
            }
        } finally {
            try {
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        // Do Nothing
                    } finally {
                        socket = null;
                        changedSegments = null;
                    }
                }
            } finally {
                ImagePanel imagePanel = frame.getImagePanel();
                if (imagePanel != null) {
                    imagePanel.repaintBuffers();
                }
                processingFrameSema.drainPermits();
                processingFrameSema.release();
            }
        }
    }

    public MainFrame getFrame() {
        return frame;
    }

    public void kill() {
        System.exit(0);
    }

    public InputEvent[] popEvents(int avail) {
        InputEvent[] rval = null;
        ArrayList<InputEvent> list;
        int size;

        try {
            queueAccessSema.acquire();
        } catch (InterruptedException e) {
            LLog.e(e);
        }
        try {
            list = outQueue;
            if (list != null && (size = list.size()) > 0) {
                rval = new InputEvent[Math.min(avail, size)];
                for (int i = 0; i < rval.length; i++) {
                    rval[i] = list.remove(0);
                }
                list.clear();
                if (size > avail) {
                    LLog.w(String.format("Dropped %d queued input events", size
                            - avail));
                }
            }
        } finally {
            queueAccessSema.release();
        }

        return rval;
    }

    public void nts_acquireInputqueue() {
        try {
            queueAccessSema.acquire();
        } catch (InterruptedException e) {
            LLog.e(e);
        }
    }

    public void nts_releaseInputqueue() {
        queueAccessSema.release();
    }

    public void nts_addInput(InputEvent event) {
        ArrayList<InputEvent> list = outQueue;
        if (list != null) {
            int size = list.size();
            do {
                if (size > 0) {
                    InputEvent prev = list.get(size - 1);

                    if (prev.merge(event, true)) {
                        // new event is now merged with the tail event of the
                        // event queue a.k.a. 'prev'
                        if (size > 1 && list.get(size - 2).merge(prev, false)) {
                            // A merge occurred, the event 'prev' may have
                            // become something else
                            // e.g. a pressed event...
                            // turns out the 'new' event was merge'able with
                            // it's previous event
                            // so the tail end can now be removed
                            list.remove(size - 1);
                        }
                        break;
                    }
                }
                if (size >= MAX_QUEUE_SIZE) {
                    // Do Nothing
                    LLog.w(String.format("Dropped input event: %s", event
                            .getType().name()));
                } else {
                    list.add(event);
                    sendEvent(CLIENT_EVENT.OFFER_INPUT, Boolean.TRUE, size + 1);
                }
            } while (false);
            return;
        }
        list = new ArrayList<InputEvent>(MAX_QUEUE_SIZE);
        list.add(event);
        outQueue = list;
        sendEvent(CLIENT_EVENT.OFFER_INPUT, Boolean.TRUE, 1);
    }

    public void addInput(InputEvent event) {
        nts_acquireInputqueue();
        try {
            nts_addInput(event);
        } finally {
            nts_releaseInputqueue();
        }
    }

    public ACCESS_MODE getAccessMode() {
        return accessMode;
    }

    public boolean isClipboardSyncEnabled() {
        boolean rval = (((Boolean) CLIENT_PROPERTIES.SYNCHRONIZE_CLIPBOARD
                .getValue()) && getAccessMode() == ACCESS_MODE.FULL_CONTROL);

        return rval;
    }

    public Semaphore getIconifiedSemaphore() {
        return iconifiedSema;
    }

    public FixedLengthBitSet getChangedSegments() {
        return changedSegments;
    }

    public void setChangedSegments(FixedLengthBitSet changedSegments) {
        this.changedSegments = changedSegments;
    }

    public void flushFrameBuffer() {
        try {
            frame.getImagePanel().flushFrameBuffer();
        } finally {
            processingFrameSema.release();
        }
    }

    public void scheduleGUIAction(final Runnable gui_action) {
        int tid = gui_tid + 1;
        if (tid < 0) {
            tid = 0;
        }
        gui_tid = tid;
        gui_dispatcher.dispatch(tid, new Runnable() {

            @Override
            public void run() {
                try {
                    SwingUtilities.invokeAndWait(gui_action);
                } catch (InvocationTargetException e) {
                    LLog.e(e);
                } catch (InterruptedException e) {
                    LLog.e(e);
                }
            }
        });
    }
}
