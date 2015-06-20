package com.jcope.util;

import static com.jcope.debug.Debug.DEBUG;
import static com.jcope.util.Platform.PLATFORM_IS_MAC;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.Semaphore;

import javax.imageio.ImageIO;

import com.jcope.debug.LLog;
import com.jcope.util.ClipboardInterface.ClipboardBusyException;

public class ClipboardMonitor extends Thread implements ClipboardOwner {
    // On mac the clipboard cannot be cached for some reason..

    public static interface ClipboardListener {
        public void onChange(Clipboard clipboard);
    }

    private static abstract class SyncRunnable {
        public abstract boolean run() throws UnsupportedFlavorException,
                IOException;
    }

    private static volatile boolean hasInstance = Boolean.FALSE;
    private static final long busy_owner_retry_delay_ms = 200L;
    private static final long mac_observer_ms = 400L;
    private static ArrayList<ClipboardListener> listeners;
    private static final ClipboardMonitor[] selfRef = new ClipboardMonitor[] { null };
    private static final Semaphore instanceSema = new Semaphore(1, Boolean.TRUE);

    private volatile boolean disposed;
    private Semaphore observerPausedSema;
    private Semaphore idleSema;
    private Semaphore notificationSema;
    private Semaphore ownershipSema;
    private volatile boolean changed;
    private volatile boolean locked;
    private volatile boolean ownsClipboard;
    private Thread clipboardChangeObserver;
    private SyncRunnable syncCacheAndDetectChange;

    private ClipboardMonitor() {
        // static
        hasInstance = Boolean.TRUE;
        listeners = new ArrayList<ClipboardListener>(1);

        // instance member initialization
        disposed = Boolean.FALSE;
        observerPausedSema = new Semaphore(1, Boolean.TRUE);
        idleSema = new Semaphore(1, Boolean.TRUE);
        locked = Boolean.FALSE;
        notificationSema = new Semaphore(0, Boolean.TRUE);
        changed = Boolean.FALSE;
        ownershipSema = new Semaphore(1, Boolean.TRUE);
        ownsClipboard = Boolean.FALSE;

        final SyncRunnable[] syncAndSignalChanged = new SyncRunnable[] { null };
        clipboardChangeObserver = new Thread() {

            private final SyncRunnable updateCacheIfChanged;

            {
                updateCacheIfChanged = new SyncRunnable() {

                    @Override
                    public boolean run() throws UnsupportedFlavorException,
                            IOException {
                        DataFlavor[] flavors;
                        Clipboard clipboard = null;
                        boolean fire = Boolean.FALSE;
                        HashMap<DataFlavor, Object> newCache;
                        DataFlavor flavor;
                        DataFlavor[] newPrevFlavors;

                        try {
                            cacheSema.acquire();
                        } catch (InterruptedException e) {
                            LLog.e(e);
                        }
                        try {
                            while (Boolean.TRUE) {
                                fire = Boolean.TRUE;
                                try {
                                    clipboard = ClipboardInterface
                                            .getClipboard();
                                    synchronized (prevFlavors) {
                                        synchronized (cache) {
                                            newPrevFlavors = prevFlavors[0];
                                            newCache = cache;
                                            flavors = ClipboardInterface
                                                    .getAvailableDataFlavors(clipboard);

                                            something_changed: do {
                                                if (null == newPrevFlavors
                                                        && null != flavors) {
                                                    break;
                                                } else if (null != newPrevFlavors
                                                        && null != flavors) {
                                                    if (newPrevFlavors.length != flavors.length) {
                                                        break;
                                                    }

                                                    // Detect shift in
                                                    // availableDataFlavorSet

                                                    for (int i = 0; i < flavors.length; i++) {
                                                        flavor = flavors[i];
                                                        if (!newPrevFlavors[i]
                                                                .equals(flavor)) {
                                                            break something_changed;
                                                        }
                                                    }

                                                    // Detect shift in value
                                                    // data associated
                                                    // with this DataFlavor set

                                                    for (int i = 0; i < flavors.length; i++) {
                                                        flavor = flavors[i];
                                                        if (ClipboardInterface
                                                                .isFlavorSupported(flavor)
                                                                && !isDataMatch(
                                                                        clipboard,
                                                                        newCache,
                                                                        flavor)) {
                                                            break something_changed;
                                                        }
                                                    }
                                                }

                                                // No Change detected
                                                fire = Boolean.FALSE;

                                            } while (Boolean.FALSE);
                                        }
                                    }
                                    if (fire) {
                                        cacheSupportedData(clipboard, flavors);
                                    }
                                    break;
                                } catch (ClipboardBusyException e) {
                                    LLog.e(e, Boolean.FALSE);
                                }
                                try {
                                    Thread.sleep(busy_owner_retry_delay_ms);
                                } catch (InterruptedException e) {
                                    LLog.e(e);
                                }
                            }
                        } finally {
                            cacheSema.release();
                        }

                        return fire;
                    }

                };

                syncAndSignalChanged[0] = updateCacheIfChanged;
            }

            private final Semaphore cacheSema = new Semaphore(1, Boolean.TRUE);
            private volatile DataFlavor[][] prevFlavors = new DataFlavor[][] { null };
            private volatile HashMap<DataFlavor, Object> cache = new HashMap<DataFlavor, Object>();

            private Object getComparableData(Clipboard clipboard,
                    DataFlavor flavor) throws UnsupportedFlavorException,
                    IOException, ClipboardBusyException {
                Object rval = ClipboardInterface.getData(clipboard, flavor);

                if (null != rval) {
                    if (flavor.equals(DataFlavor.imageFlavor)) {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        ImageIO.write((BufferedImage) rval, "png", baos);
                        rval = baos.toByteArray();
                    }
                }

                return rval;
            }

            private boolean isDataMatch(Clipboard clipboard,
                    HashMap<DataFlavor, Object> cache, DataFlavor flavor)
                    throws UnsupportedFlavorException, IOException,
                    ClipboardBusyException {
                Object cObj = cache.get(flavor);
                Object obj = getComparableData(clipboard, flavor);

                boolean rval = (null == obj && null == cObj);

                if (!rval && null != obj && null != cObj) {
                    rval = (obj instanceof byte[] && cObj instanceof byte[]) ? Arrays
                            .equals(((byte[]) obj), ((byte[]) cObj)) : obj
                            .equals(cObj);
                }

                return rval;
            }

            private void cacheSupportedData(Clipboard clipboard,
                    DataFlavor[] flavors) throws UnsupportedFlavorException,
                    IOException, ClipboardBusyException {
                HashMap<DataFlavor, Object> cache = new HashMap<DataFlavor, Object>();
                Iterator<DataFlavor> sFlavors = ClipboardInterface
                        .getSupportedFlavorsIterator();
                DataFlavor flavor;

                while (sFlavors.hasNext()) {
                    flavor = sFlavors.next();
                    if (ClipboardInterface.isDataFlavorAvailable(clipboard,
                            flavor)) {
                        cache.put(flavor, getComparableData(clipboard, flavor));
                    }
                }

                this.cache = cache;
                prevFlavors[0] = flavors;
            }

            @Override
            public void run() {
                boolean fire = Boolean.FALSE;

                while (!disposed) {
                    try {
                        observerPausedSema.acquire();
                    } catch (InterruptedException e) {
                        LLog.e(e);
                    }
                    try {
                        do {
                            try {
                                fire = updateCacheIfChanged.run();
                            } catch (Exception e) {
                                LLog.e(e, Boolean.FALSE);
                                break;
                            }

                            if (fire) {
                                fireChangeNotification();
                            }
                        } while (false);
                    } finally {
                        observerPausedSema.release();
                    }

                    try {
                        Thread.sleep(mac_observer_ms);
                    } catch (InterruptedException e) {
                        LLog.e(e);
                    }
                }
            }

        };

        syncCacheAndDetectChange = syncAndSignalChanged[0];
        syncAndSignalChanged[0] = null;

        clipboardChangeObserver.setName("Clipboard Change Observer");
        clipboardChangeObserver.setDaemon(Boolean.TRUE);
        clipboardChangeObserver.setPriority(NORM_PRIORITY);
        clipboardChangeObserver.start();

        // instance config
        setName("Clipboard Monitor");
        setDaemon(Boolean.TRUE);
        setPriority(NORM_PRIORITY);
        start();
    }

    public static boolean hasInstance() {
        return hasInstance;
    }

    public static ClipboardMonitor getInstance() {
        ClipboardMonitor rval = selfRef[0];

        if (null == rval) {
            try {
                instanceSema.acquire();
            } catch (InterruptedException e) {
                LLog.e(e);
            }
            try {
                synchronized (selfRef) {
                    rval = selfRef[0];
                    if (null == rval) {
                        rval = new ClipboardMonitor();
                        selfRef[0] = rval;
                    }
                }
            } finally {
                instanceSema.release();
            }
        }

        return rval;
    }

    private void fireChangeNotification() {
        notificationSema.drainPermits();
        changed = Boolean.TRUE;
        notificationSema.release();
    }

    @Override
    public void run() {
        Clipboard clipboard = null;

        IS_DISPOSED: while (!disposed) {
            try {
                notificationSema.acquire();
            } catch (InterruptedException e) {
                LLog.e(e);
            }
            try {
                idleSema.acquire();
            } catch (InterruptedException e) {
                LLog.e(e);
            }

            try {
                do {
                    // attempt to get the current clipboard
                    clipboard = ClipboardInterface.getClipboard();
                    if (clipboard != null) {
                        break;
                    }

                    try {
                        Thread.sleep(busy_owner_retry_delay_ms);
                    } catch (InterruptedException e) {
                        LLog.e(e);
                    }

                    if (disposed) {
                        break IS_DISPOSED;
                    }
                } while (Boolean.TRUE);

                if (changed) {
                    changed = Boolean.FALSE;

                    for (ClipboardListener l : listeners) {
                        if (changed) {
                            break;
                        }

                        try {
                            l.onChange(clipboard);
                        } catch (Exception e) {
                            LLog.e(e, Boolean.FALSE);
                            // The show must go on
                        }
                    }
                }
            } finally {
                clipboard = null;
                idleSema.release();
            }
        }

        listeners.clear();
    }

    public void dispose() {
        disposed = Boolean.TRUE;
    }

    public void addListener(ClipboardListener l) {
        listeners.add(l);
    }

    public boolean removeListener(ClipboardListener l) {
        return listeners.remove(l);
    }

    public void lockAndPause() {
        try {
            idleSema.acquire();
        } catch (InterruptedException e) {
            LLog.e(e);
        }
        locked = true;
    }

    public void unlockAndUnpause() {
        changed = Boolean.FALSE;
        locked = false;
        idleSema.release();
    }

    @Override
    public void lostOwnership(Clipboard clipboard, Transferable transferable) {
        try {
            ownershipSema.acquire();
        } catch (InterruptedException e) {
            LLog.e(e);
        }
        try {
            if (!ownsClipboard) {
                LLog.w("WTF: Got notification that clipboard ownership was lost when already in relinquished state");
                return;
            }
            ownsClipboard = Boolean.FALSE;
            if (!PLATFORM_IS_MAC) {
                // if platform not mac (windows)
                // then the observer needs to be unpaused!
                if (DEBUG) {
                    LLog.i("App lost clipboard ownership, enabling observer scanner to detect changes");
                }
                observerPausedSema.release();
            } else {
                LLog.w("As of 5/19/2014, MAC does not broadcast clipboard ownership changes, so why is this logged?");
                return;
            }
        } finally {
            ownershipSema.release();
        }
    }

    /**
     * Called by an external wrapper interface to notify this module that the
     * clipboard owner is NOW this module
     */
    public void notifyOwnershipGained() {
        try {
            ownershipSema.acquire();
        } catch (InterruptedException e) {
            LLog.e(e);
        }
        try {
            boolean fire;
            try {
                fire = syncCacheAndDetectChange.run();
            } catch (UnsupportedFlavorException e) {
                fire = Boolean.FALSE;
                LLog.e(e);
            } catch (IOException e) {
                fire = Boolean.FALSE;
                LLog.e(e);
            }
            if (fire && !locked) {
                fireChangeNotification();
            }
            if (ownsClipboard) {
                if (DEBUG) {
                    LLog.i("App changed clipboard contents, but already owned the clipboard");
                }
                return;
            }
            ownsClipboard = Boolean.TRUE;
            if (DEBUG) {
                LLog.i("Clipboard now owned by app");
            }
            if (!PLATFORM_IS_MAC) {
                if (DEBUG) {
                    LLog.i("App no longer scanning clipboard for changes, expecting future notification of ownership lost to re-enable clipboard scanner");
                }
                // observer should not be paused on mac because of mac bug: MAC
                // does not broadcast clipboard ownership changes
                try {
                    observerPausedSema.acquire();
                } catch (InterruptedException e) {
                    LLog.e(e);
                }
            }
        } finally {
            ownershipSema.release();
        }
    }
}
