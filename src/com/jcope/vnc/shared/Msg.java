package com.jcope.vnc.shared;

import static com.jcope.vnc.shared.MsgCache.bufferPool;
import static com.jcope.vnc.shared.MsgCache.bufferPoolLock;
import static com.jcope.vnc.shared.MsgCache.compressionCache;
import static com.jcope.vnc.shared.MsgCache.compressionResultCache;
import static com.jcope.vnc.shared.MsgCache.precompRBOS;
import static com.jcope.vnc.shared.MsgCache.precompSema;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.jcope.debug.LLog;
import com.jcope.util.ReusableByteArrayOutputStream;
import com.jcope.vnc.shared.StateMachine.CLIENT_EVENT;
import com.jcope.vnc.shared.StateMachine.SERVER_EVENT;

public class Msg implements Serializable {
    // Generated: serialVersionUID
    private static final long serialVersionUID = -1197396024588406286L;

    public final Object event;
    public final Object[] args;

    private Msg(Object event, Object[] args) {
        this.event = event;
        this.args = args;
    }

    private static Object decompress(byte[] bArray, int length) {
        Object rval = null;

        ByteArrayInputStream bis = new ByteArrayInputStream(bArray, 0, length);

        try {
            GZIPInputStream gzip_in = new GZIPInputStream(bis);
            ObjectInputStream ois = new ObjectInputStream(gzip_in);

            rval = ois.readObject();
            ois.close();
            gzip_in.close();
        } catch (IOException e) {
            LLog.e(e);
        } catch (ClassNotFoundException e) {
            LLog.e(e);
        }

        try {
            bis.close();
        } catch (IOException e) {
            LLog.e(e);
        }

        return rval;
    }

    public static ByteBufferPool.PoolRef getCompressed(SERVER_EVENT event,
            Object... args) {
        try {
            precompSema.acquire();
        } catch (InterruptedException e) {
            LLog.e(e);
        }
        try {
            return compress(null, (args == null) ? event : new Msg(event, args));
        } finally {
            precompSema.release();
        }
    }

    private static ByteBufferPool.PoolRef compress(BufferedOutputStream out,
            Object obj) {
        ByteBufferPool.PoolRef rval = null;
        ReusableByteArrayOutputStream rbos;
        int resultSize;

        if (out == null) {
            if (precompRBOS == null) {
                precompRBOS = new ReusableByteArrayOutputStream();
            } else {
                precompRBOS.reset();
            }
            rbos = precompRBOS;
        } else {
            rbos = compressionCache.get(out);
            if (rbos == null) {
                rbos = new ReusableByteArrayOutputStream();
                compressionCache.put(out, rbos);
            } else {
                rbos.reset();
            }
        }

        try {
            GZIPOutputStream gzip_out = new GZIPOutputStream(rbos);
            ObjectOutputStream oos = new ObjectOutputStream(gzip_out);
            oos.writeObject(obj);
            oos.flush();
            gzip_out.flush();
            oos.close();
            gzip_out.close();

            resultSize = rbos.size();
            if (out == null) {
                if (bufferPool == null) {
                    try {
                        bufferPoolLock.acquire();
                    } catch (InterruptedException e) {
                        LLog.e(e);
                    }
                    try {
                        if (bufferPool == null) {
                            bufferPool = new ByteBufferPool();
                        }
                    } finally {
                        bufferPoolLock.release();
                    }
                }
                rval = bufferPool.acquire(rbos.size());
                rbos.toByteArray(rval.get());
            } else {
                ByteBufferPool resultCache = compressionResultCache.get(out);
                if (resultCache == null) {
                    resultCache = new ByteBufferPool();
                    compressionResultCache.put(out, resultCache);
                }
                rval = resultCache.acquire(resultSize);
                rbos.toByteArray(rval.get());
            }
        } catch (IOException e) {
            LLog.e(e);
        }

        return rval;
    }

    public static void send(BufferedOutputStream out, JitCompressable jce,
            SERVER_EVENT event, Object... args) throws IOException {
        _send(out, jce, event, args);
    }

    public static void send(BufferedOutputStream out, CLIENT_EVENT event,
            Object... args) throws IOException {
        _send(out, null, event, args);
    }

    private static void _send(BufferedOutputStream out, JitCompressable jce,
            Object event, Object... args) throws IOException {
        ByteBufferPool.PoolRef outBufferRef = null;
        byte[] outBuffer;

        try {
            if (jce == null) {
                if (args == null) {
                    outBufferRef = compress(out, event);
                } else {
                    outBufferRef = compress(out, new Msg(event, args));
                }
                outBuffer = outBufferRef.get();
            } else {
                outBuffer = jce.getCompressed();
            }

            if (outBuffer.length > 0) {
                out.write(outBuffer.length & 0xff);
                out.write((outBuffer.length >> 8) & 0xff);
                out.write((outBuffer.length >> 16) & 0xff);
                out.write((outBuffer.length >> 24) & 0xff);
                out.write(outBuffer);

                // out.flush();
                // Flushing has moved into the higher layer (I/O dispatcher task
                // generation)
                // This layer has full knowledge of all the dispatchers writing
                // to the I/O layers
                // And so a flush can easily occur there when the task see's
                // that
                // the dispatchers have nothing new to write
            }
        } finally {
            if (outBufferRef != null) {
                outBufferRef.release();
            }
        }
    }

    public static class CompressedObjectReader {
        private BufferedInputStream in;
        private byte[] buffer;
        private int pos, dp, size;

        public CompressedObjectReader() {
            buffer = new byte[4];
        }

        private void fillBuffer() throws IOException {
            do {

                dp = in.read(buffer, pos, size - pos);
                if (dp < 0) {
                    break;
                }
                pos += dp;

            } while (pos < size);
        }

        public Object readObject(BufferedInputStream in) throws IOException {
            Object rval = null;

            this.in = in;

            try {

                do {
                    pos = 0;
                    size = 4;

                    fillBuffer();

                    if (dp < 1) {
                        // why would it ever be zero and allowed to continue?
                        break;
                    }

                    pos = 0;
                    size = (0xff & buffer[0]) | ((0xff & buffer[1]) << 8)
                            | ((0xff & buffer[2]) << 16)
                            | ((0xff & buffer[3]) << 24);

                    if (buffer.length < size) {
                        buffer = new byte[size];
                    }

                    fillBuffer();

                    if (dp < 1) {
                        // why would it ever be zero and allowed to continue?
                        break;
                    }

                    rval = decompress(buffer, size);

                } while (Boolean.FALSE);

            } finally {

                this.in = null;

            }

            return rval;
        }
    }
}
