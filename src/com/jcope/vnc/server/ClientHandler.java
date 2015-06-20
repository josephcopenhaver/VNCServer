package com.jcope.vnc.server;

import static com.jcope.debug.Debug.assert_;
import static com.jcope.vnc.shared.ScreenSelector.getScreenDevicesOrdered;

import java.awt.GraphicsDevice;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.Semaphore;

import javax.swing.SwingUtilities;

import com.jcope.debug.LLog;
import com.jcope.util.FixedLengthBitSet;
import com.jcope.util.GraphicsSegment;
import com.jcope.util.TaskDispatcher;
import com.jcope.vnc.server.screen.Manager;
import com.jcope.vnc.server.screen.Monitor;
import com.jcope.vnc.server.screen.ScreenListener;
import com.jcope.vnc.shared.AccessModes.ACCESS_MODE;
import com.jcope.vnc.shared.IOERunnable;
import com.jcope.vnc.shared.Msg;
import com.jcope.vnc.shared.Msg.CompressedObjectReader;
import com.jcope.vnc.shared.StateMachine.SERVER_EVENT;

public class ClientHandler extends Thread {
    private static final Object[] jce_id_ptr = new Object[] { null };
    private static GraphicsSegment.Synchronously getJCE = new GraphicsSegment.Synchronously() {

        @Override
        public Object run(GraphicsSegment receiver, int[] pixels,
                Integer[] solidColorPtr) {
            Integer solidColor = solidColorPtr[0];
            Object serialized = (solidColor == null) ? pixels : solidColor;
            Object id = jce_id_ptr[0];
            jce_id_ptr[0] = null;
            JitCompressedEvent jce = receiver.acquireJitCompressedEvent(id,
                    serialized);
            return jce;
        }

    };

    private Socket socket;
    private BufferedInputStream in = null;
    private BufferedOutputStream out = null;
    private ArrayList<Runnable> onDestroyActions = new ArrayList<Runnable>(1);
    private volatile boolean dying = Boolean.FALSE;
    private volatile boolean alive = Boolean.TRUE;

    private ClientState clientState = null;
    private DirectRobot dirbot = null;

    private TaskDispatcher<Integer> unserializedDispatcher;
    private TaskDispatcher<Integer> serializedDispatcher;
    private boolean isNewFlag = Boolean.TRUE;

    private ScreenListener[] screenListenerRef = new ScreenListener[] { null };

    private Semaphore sendSema = new Semaphore(1, true);
    private Semaphore serialSema = new Semaphore(1, true);
    volatile int tid = -1;

    private Semaphore handleIOSema = new Semaphore(1, true);
    private Semaphore queueSema = new Semaphore(1, true);
    private HashMap<Integer, SERVER_EVENT> nonSerialEventOutboundQueue = new HashMap<Integer, SERVER_EVENT>();
    private HashMap<Integer, Object[]> nonSerialEventQueue = new HashMap<Integer, Object[]>();
    private LinkedList<Integer> nonSerialOrderedEventQueue = new LinkedList<Integer>();

    private Semaphore changedSegmentsSema = new Semaphore(1, true);
    private volatile FixedLengthBitSet stagedChanges = null;
    private FixedLengthBitSet[] publishedChanges = new FixedLengthBitSet[] { null };
    private Semaphore scanPeriodSema = new Semaphore(1, true);
    private volatile Long scanPeriod = null;
    private volatile Long newScanPeriod;

    private Semaphore monitorLock = new Semaphore(1, true);
    private WeakReference<?>[] monitorRef = new WeakReference<?>[] { null };
    private volatile boolean paused = false;

    private Semaphore transactionQueueSema = new Semaphore(1, true);
    private volatile int transaction_tid = -1;
    private TaskDispatcher<Integer> transactionDispatcher;

    public ClientHandler(Socket socket) throws IOException {
        super(toString(socket));
        this.socket = socket;
        out = new BufferedOutputStream(socket.getOutputStream());
        in = new BufferedInputStream(socket.getInputStream());
        String strID = toString();
        unserializedDispatcher = new TaskDispatcher<Integer>(String.format(
                "Non-serial dispatcher: %s", strID));
        serializedDispatcher = new TaskDispatcher<Integer>(String.format(
                "Serial dispatcher: %s", strID));
        transactionDispatcher = new TaskDispatcher<Integer>(String.format(
                "Transaction dispatcher: %s", strID));

        unserializedDispatcher.setImmediate(true,
                getNonSerialTID(SERVER_EVENT.READ_INPUT_EVENTS, null, 0));
    }

    public String toString() {
        String rval = toString(socket);

        return rval;
    }

    private static String toString(Socket socket) {
        String rval = String
                .format("ClientHandler: %s", socketToString(socket));

        return rval;
    }

    private static String socketToString(Socket socket) {
        InetAddress addr = socket.getInetAddress();
        return String.format("%s - %s - %d - %d", addr.getHostName(),
                addr.getHostAddress(), socket.getLocalPort(), socket.getPort());
    }

    private static Runnable getUnbindAliasAction(final ClientHandler thiz) {
        Runnable rval = new Runnable() {

            @Override
            public void run() {
                if (AliasRegistry.hasInstance()) {
                    AliasRegistry.getInstance().unbind(thiz);
                }
            }

        };

        return rval;
    }

    public boolean getIsNewFlag() {
        return isNewFlag;
    }

    public void setIsNewFlag(boolean x) {
        isNewFlag = x;
    }

    private Runnable killIOAction = new Runnable() {
        @Override
        public void run() {
            try {
                in.close();
            } catch (IOException e) {
                LLog.e(e);
            } finally {
                try {
                    out.close();
                } catch (IOException e) {
                    LLog.e(e);
                } finally {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        LLog.e(e);
                    }
                }
            }
        }
    };

    private Runnable releaseIOResources = new Runnable() {
        @Override
        public void run() {
            try {
                setPaused(true);
            } finally {
                try {
                    queueSema.acquire();
                } catch (InterruptedException e) {
                    LLog.e(e);
                }
                try {
                    synchronized (nonSerialEventQueue) {
                        for (Object[] sargs : nonSerialEventQueue.values()) {
                            if (sargs == null || sargs[0] == null) {
                                continue;
                            }
                            ((JitCompressedEvent) sargs[0]).release();
                        }
                    }
                } finally {
                    queueSema.release();
                }
            }
        }
    };

    public void run() {
        try {
            // Destroy actions are now LIFO
            addOnDestroyAction(getUnbindAliasAction(this));
            addOnDestroyAction(releaseIOResources);
            addOnDestroyAction(killIOAction);

            CompressedObjectReader reader = new CompressedObjectReader();
            Object obj = null;

            while (!dying) {
                try {
                    obj = reader.readObject(in);
                    if (obj == null) {
                        throw new IOException("Connection reset by peer");
                    }
                } catch (IOException e) {
                    LLog.e(e);
                }
                StateMachine.handleClientInput(this, obj);
                obj = null;
            }
        } catch (Exception e) {
            LLog.e(e, false);
        } finally {
            kill();
        }
    }

    public Long getScanPeriod() {
        return scanPeriod;
    }

    public Long commitNewScanPeriod() {
        Long rval;
        try {
            scanPeriodSema.acquire();
        } catch (InterruptedException e) {
            LLog.e(e);
        }
        try {
            rval = scanPeriod;
            scanPeriod = newScanPeriod;
            newScanPeriod = null;
        } finally {
            scanPeriodSema.release();
        }
        return rval;
    }

    public boolean selectGraphicsDevice(int graphicsDeviceID,
            ACCESS_MODE accessMode, long scanPeriod, String password) {
        boolean rval;

        GraphicsDevice[] devices = getScreenDevicesOrdered();
        GraphicsDevice graphicsDevice = devices[graphicsDeviceID];

        this.newScanPeriod = scanPeriod;
        rval = Manager.getInstance().bind(this, graphicsDevice, accessMode,
                password);

        return rval;
    }

    public void addOnDestroyAction(Runnable r) {
        onDestroyActions.add(r);
    }

    public void kill() {
        synchronized (this) {
            if (dying) {
                return;
            }
            dying = true;
        }

        Exception topE = null;
        Runnable r;

        try {
            Manager.getInstance().unbind(this);
        } catch (Exception e) {
            LLog.e(e, false);
        }

        try {
            try {
                try {
                    serializedDispatcher.dispose();
                } finally {
                    unserializedDispatcher.dispose();
                }
            } finally {
                SwingUtilities.invokeLater(new Runnable() {

                    @Override
                    public void run() {
                        try {
                            try {
                                serializedDispatcher.join();
                            } finally {
                                unserializedDispatcher.join();
                            }
                        } catch (InterruptedException e) {
                            LLog.e(e, Boolean.FALSE);
                        }
                    }

                });
            }
        } finally {
            try {
                for (int idx = onDestroyActions.size(); idx > 0;) {
                    r = onDestroyActions.get(--idx);
                    try {
                        r.run();
                    } catch (Exception e) {
                        if (topE == null) {
                            topE = e;
                        } else {
                            System.err.println(e.getMessage());
                            e.printStackTrace(System.err);
                        }
                    }
                }
                if (topE != null) {
                    throw new RuntimeException(topE);
                }
            } finally {
                try {
                    onDestroyActions.clear();
                    onDestroyActions = null;
                } finally {
                    alive = false;
                }
            }
        }
    }

    public boolean isRunning() {
        return !dying;
    }

    public boolean isDead() {
        return !alive;
    }

    public ClientState getClientState() {
        return clientState;
    }

    public void initClientState() {
        clientState = new ClientState();
    }

    public ScreenListener getScreenListener(final DirectRobot dirbot) {
        ScreenListener l = screenListenerRef[0];
        if (l == null || dirbot != this.dirbot) {
            this.dirbot = dirbot;
            l = new ScreenListener() {

                @Override
                public void onScreenChange(FixedLengthBitSet changedSegments) {
                    sendEvent(SERVER_EVENT.SCREEN_SEGMENT_CHANGED,
                            changedSegments);
                }
            };
            screenListenerRef[0] = l;
        }
        return l;

    }

    public void sendEvent(JitCompressedEvent jce) {
        _sendEvent(jce.getEvent(), jce, (Object[]) null);
    }

    public void sendEvent(SERVER_EVENT event) {
        sendEvent(event, (Object[]) null);
    }

    public void sendEvent(final SERVER_EVENT event, final Object... args) {
        _sendEvent(event, null, args);
    }

    public void _sendEvent(final SERVER_EVENT event,
            final JitCompressedEvent jce, final Object... args) {
        if (event == SERVER_EVENT.SCREEN_SEGMENT_CHANGED) {
            assert_(jce == null);
            assert_(args.length == 1);
            assert_(args[0] instanceof FixedLengthBitSet);
            FixedLengthBitSet newChanges = (FixedLengthBitSet) args[0];
            try {
                changedSegmentsSema.acquire();
            } catch (InterruptedException e) {
                LLog.e(e);
            }
            try {
                synchronized (publishedChanges) {
                    // limit to only bits changed and not in a published-changed
                    // state
                    FixedLengthBitSet l_publishedChanges = publishedChanges[0];
                    if (l_publishedChanges == null) {
                        publishedChanges[0] = new FixedLengthBitSet(
                                newChanges.length);
                    } else {
                        newChanges.andNot(l_publishedChanges);
                        if (newChanges.isEmpty()) {
                            return;
                        }
                    }
                }
                FixedLengthBitSet l_stagedChanges = stagedChanges;
                if (l_stagedChanges != null) {
                    l_stagedChanges.or(newChanges);
                    return;
                }
                l_stagedChanges = newChanges.clone();
                args[0] = l_stagedChanges;
                stagedChanges = l_stagedChanges;
            } finally {
                changedSegmentsSema.release();
            }
        }
        try {
            handleIOSema.acquire();
        } catch (InterruptedException e) {
            LLog.e(e);
        }
        try {
            nts_sendEvent(event, jce, args);
        } finally {
            handleIOSema.release();
        }
    }

    private void nts_sendEvent(final SERVER_EVENT event,
            final JitCompressedEvent jce, final Object... args) {
        int tidTmp;
        TaskDispatcher<Integer> dispatcher;
        boolean dispatch;
        boolean isMutable;

        if (event.isSerial()) {
            try {
                serialSema.acquire();
            } catch (InterruptedException e) {
                LLog.e(e);
            }
            try {
                tidTmp = tid;
                tidTmp++;
                if (tidTmp < 0) {
                    tidTmp = 0;
                }
                tid = tidTmp;
            } finally {
                serialSema.release();
            }
            dispatcher = serializedDispatcher;
            dispatch = Boolean.TRUE;
        } else {
            tidTmp = getNonSerialTID(event, args, 0);
            dispatcher = unserializedDispatcher;
            // TODO: only dispatch if we know for sure that the arguments have
            // changed
            if ((isMutable = event.hasMutableArgs())
                    || !unserializedDispatcher.queueContains(tidTmp)) {
                if (event == SERVER_EVENT.SCREEN_SEGMENT_UPDATE) {
                    dispatch = Boolean.TRUE;
                } else {
                    try {
                        queueSema.acquire();
                    } catch (InterruptedException e) {
                        LLog.e(e);
                    }
                    try {
                        synchronized (nonSerialEventQueue) {
                            synchronized (nonSerialEventOutboundQueue) {
                                synchronized (nonSerialOrderedEventQueue) {
                                    if (nonSerialEventOutboundQueue.get(tidTmp) == null) {
                                        dispatch = Boolean.TRUE;
                                        if (event != SERVER_EVENT.READ_INPUT_EVENTS) {
                                            nonSerialEventOutboundQueue.put(
                                                    tidTmp, event);
                                            nonSerialOrderedEventQueue
                                                    .addLast(tidTmp);
                                        }
                                    } else {
                                        Object[] sargs = null;
                                        dispatch = Boolean.FALSE;
                                        if (isMutable
                                                || (sargs = nonSerialEventQueue
                                                        .get(tidTmp)) == null) {
                                            JitCompressedEvent jce2;
                                            if (isMutable) {
                                                sargs = nonSerialEventQueue
                                                        .get(tidTmp);
                                            }
                                            if (sargs == null) {
                                                sargs = new Object[] { jce,
                                                        args };
                                                nonSerialEventQueue.put(tidTmp,
                                                        sargs);
                                            } else {
                                                jce2 = (JitCompressedEvent) sargs[0];
                                                if (jce2 != null) {
                                                    jce2.release();
                                                }
                                                sargs[0] = jce;
                                                sargs[1] = args;
                                            }
                                            if (jce != null) {
                                                jce.acquire();
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } finally {
                        queueSema.release();
                    }
                }
            } else {
                dispatch = Boolean.FALSE;
            }

        }
        if (dispatch) {
            final IOERunnable f_msgAction;
            IOERunnable msgAction = null;
            switch (event) {
            case ALIAS_CHANGED:
            case ALIAS_DISCONNECTED:
            case ALIAS_REGISTERED:
            case ALIAS_UNREGISTERED:
            case AUTHORIZATION_UPDATE:
            case CHAT_MSG_TO_ALL:
            case CHAT_MSG_TO_USER:
            case CLIENT_ALIAS_UPDATE:
            case CLIPBOARD_CHANGED:
            case CONNECTION_CLOSED:
            case CONNECTION_ESTABLISHED:
            case CURSOR_GONE:
            case CURSOR_MOVE:
            case END_OF_FRAME:
            case FAILED_AUTHORIZATION:
            case GET_CLIPBOARD:
            case NUM_SCREENS_CHANGED:
            case READ_INPUT_EVENTS:
            case SCREEN_GONE:
            case SCREEN_RESIZED:
            case SET_CLIPBOARD:
            case SCREEN_SEGMENT_SIZE_UPDATE:
                msgAction = new IOERunnable() {

                    @Override
                    public void run() throws IOException {
                        Msg.send(out, jce, event, args);
                    }

                };
                break;
            case ENTIRE_SCREEN_UPDATE:
                assert_(jce == null);
                assert_(args == null);
                msgAction = new IOERunnable() {

                    @Override
                    public void run() throws IOException {
                        DirectRobot dirbot = ClientHandler.this.dirbot;
                        if (dirbot != null) {
                            Object[] args = new Object[] { dirbot
                                    .getRGBPixels() };
                            Msg.send(out, jce, event, args);
                        }
                    }

                };
                break;
            case SCREEN_SEGMENT_UPDATE:
                assert_(jce == null);
                assert_(args.length == 2);

                msgAction = new IOERunnable() {

                    @Override
                    public void run() throws IOException {
                        GraphicsSegment graphicsSegment = (GraphicsSegment) args[1];
                        jce_id_ptr[0] = args[0];
                        JitCompressedEvent new_jce = (JitCompressedEvent) graphicsSegment
                                .synchronously(getJCE);
                        Msg.send(out, new_jce, event);
                    }

                };
                break;
            case SCREEN_SEGMENT_CHANGED:
                msgAction = new IOERunnable() {

                    @Override
                    public void run() throws IOException {
                        try {
                            changedSegmentsSema.acquire();
                        } catch (InterruptedException e) {
                            LLog.e(e);
                        }
                        try {
                            FixedLengthBitSet flbs = ClientHandler.this.stagedChanges;
                            ClientHandler.this.stagedChanges = null;
                            synchronized (ClientHandler.this.publishedChanges) {
                                ClientHandler.this.publishedChanges[0].or(flbs);
                            }
                        } finally {
                            changedSegmentsSema.release();
                        }
                        Msg.send(out, jce, event, args);
                    }

                };
                break;
            }
            f_msgAction = msgAction;
            Runnable r = new Runnable() {

                @Override
                public void run() {
                    boolean killSelf = true;
                    boolean flushed = false;
                    try {
                        try {
                            sendSema.acquire();
                        } catch (InterruptedException e) {
                            LLog.e(e);
                        }
                        try {
                            f_msgAction.run();
                            if ((!event.isCursor())
                                    && serializedDispatcher.isEmpty()
                                    && unserializedDispatcher.isEmpty()) {
                                flushed = true;
                                out.flush();
                            }

                            // connection related post send handling...

                            switch (event) {
                            case AUTHORIZATION_UPDATE:
                                if (!flushed) {
                                    flushed = true;
                                    out.flush();
                                }
                                if (!((Boolean) args[0])) {
                                    SwingUtilities.invokeLater(new Runnable() {

                                        @Override
                                        public void run() {
                                            kill();
                                        }

                                    });
                                }
                                break;
                            case ALIAS_CHANGED:
                            case ALIAS_DISCONNECTED:
                            case ALIAS_REGISTERED:
                            case ALIAS_UNREGISTERED:
                            case CHAT_MSG_TO_ALL:
                            case CHAT_MSG_TO_USER:
                            case CLIENT_ALIAS_UPDATE:
                            case CONNECTION_CLOSED:
                            case CONNECTION_ESTABLISHED:
                            case CURSOR_GONE:
                            case CURSOR_MOVE:
                            case FAILED_AUTHORIZATION:
                            case NUM_SCREENS_CHANGED:
                            case SCREEN_GONE:
                            case SCREEN_RESIZED:
                            case SCREEN_SEGMENT_CHANGED:
                            case SCREEN_SEGMENT_SIZE_UPDATE:
                            case SCREEN_SEGMENT_UPDATE:
                            case READ_INPUT_EVENTS:
                            case CLIPBOARD_CHANGED:
                            case GET_CLIPBOARD:
                            case SET_CLIPBOARD:
                            case ENTIRE_SCREEN_UPDATE:
                                break;
                            case END_OF_FRAME:
                                break;
                            }
                        } catch (IOException e) {
                            LLog.e(e);
                        } finally {
                            sendSema.release();
                        }
                        killSelf = false;
                    } finally {
                        if (killSelf) {
                            kill();
                        }
                    }
                }
            };

            Runnable rOnDestroy;

            if (jce == null) {
                rOnDestroy = null;
            } else {
                jce.acquire();
                rOnDestroy = jce.getOnDestroy();
            }

            dispatcher.dispatch(tidTmp, r, rOnDestroy);
        }
    }

    private int getNonSerialTID(SERVER_EVENT event, Object[] refStack,
            int idxSegmentID) {
        int rval;
        if (event == SERVER_EVENT.SCREEN_SEGMENT_UPDATE) {
            rval = ((Integer) refStack[idxSegmentID]) + 2;
            rval += SERVER_EVENT.getMaxOrdinal();
        } else {
            rval = event.ordinal();
        }

        return rval;
    }

    public GraphicsSegment getSegment(int segmentID) {
        return Manager.getInstance().getSegment(dirbot, segmentID);
    }

    public DirectRobot getDirbot() {
        return dirbot;
    }

    public void handleEventAck(SERVER_EVENT ackForEvent, Object[] refStack,
            int idxSegmentID) {
        ArrayList<SERVER_EVENT> evtlist = new ArrayList<SERVER_EVENT>();
        ArrayList<Object[]> plist = new ArrayList<Object[]>();
        int tTid = getNonSerialTID(ackForEvent, refStack, idxSegmentID);
        int idx = 0;
        Exception firstE = null;
        int tid;
        SERVER_EVENT hiddenAckEvt;
        Object[] sargs;
        JitCompressedEvent jce;

        try {
            handleIOSema.acquire();
        } catch (InterruptedException e) {
            LLog.e(e);
        }
        try {
            try {
                queueSema.acquire();
            } catch (InterruptedException e) {
                LLog.e(e);
            }
            try {
                synchronized (nonSerialEventQueue) {
                    synchronized (nonSerialEventOutboundQueue) {
                        synchronized (nonSerialOrderedEventQueue) {
                            do {
                                tid = nonSerialOrderedEventQueue.removeFirst();
                                hiddenAckEvt = nonSerialEventOutboundQueue
                                        .remove(tid);
                                sargs = nonSerialEventQueue.remove(tid);
                                if (sargs != null) {
                                    evtlist.add(hiddenAckEvt);
                                    plist.add(sargs);
                                }
                            } while (tid != tTid);
                        }
                    }
                }
            } finally {
                queueSema.release();
            }
            for (Object[] targs : plist) {
                hiddenAckEvt = evtlist.get(idx++);
                jce = (JitCompressedEvent) targs[0];
                Object[] args = (Object[]) targs[1];

                // erase targs to unbind objects
                targs[0] = null;
                targs[1] = null;

                // flush deferred queue contents
                if (firstE == null) {
                    try {
                        // LLog.i(String.format("Sending pending event %s with %d # of args",
                        // hiddenAckEvt.name(), args == null ? 0 :
                        // args.length));
                        nts_sendEvent(hiddenAckEvt, jce, args);
                    } catch (Exception e) {
                        firstE = e;
                    } finally {
                        if (jce != null) {
                            jce.release();
                        }
                    }
                } else if (jce != null) {
                    jce.release();
                }
            }

            if (firstE != null) {
                throw new RuntimeException(firstE);
            }

            assert_(plist.size() == idx);
        } finally {
            handleIOSema.release();
        }
    }

    public void subscribe(FixedLengthBitSet flbs) {
        try {
            changedSegmentsSema.acquire();
        } catch (InterruptedException e) {
            LLog.e(e);
        }
        try {
            synchronized (publishedChanges) {
                publishedChanges[0].andNot(flbs);
            }
        } finally {
            changedSegmentsSema.release();
        }
    }

    public void setPaused(boolean newPaused) {
        try {
            monitorLock.acquire();
        } catch (InterruptedException e) {
            LLog.e(e);
        }
        try {
            if (newPaused == paused) {
                return;
            }
            Monitor monitor;
            synchronized (monitorRef) {
                monitor = (Monitor) monitorRef[0].get();
            }
            paused = newPaused;
            monitor.setPaused(newPaused);
        } finally {
            monitorLock.release();
        }
    }

    public void bindMonitor(Monitor monitor) {
        try {
            monitorLock.acquire();
        } catch (InterruptedException e) {
            LLog.e(e);
        }
        try {
            boolean l_paused = paused;
            synchronized (monitorRef) {
                Object oldMonitor;
                if (!l_paused
                        && (oldMonitor = monitorRef[0] == null ? null
                                : monitorRef[0].get()) != null) {
                    ((Monitor) oldMonitor).setPaused(true);
                }
                monitorRef[0] = new WeakReference<Monitor>(monitor);
            }
            if (!l_paused) {
                monitor.setPaused(false);
            }
        } finally {
            monitorLock.release();
        }
    }

    public void dispatchTransaction(Runnable action) {
        try {
            transactionQueueSema.acquire();
        } catch (InterruptedException e) {
            LLog.e(e);
        }
        try {
            int tid = transaction_tid;
            tid++;
            transaction_tid = tid;
            transactionDispatcher.dispatch(tid, action);
        } finally {
            transactionQueueSema.release();
        }
    }
}
