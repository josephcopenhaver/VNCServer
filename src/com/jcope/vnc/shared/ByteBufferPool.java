package com.jcope.vnc.shared;

import com.jcope.util.BufferPool;

public class ByteBufferPool extends BufferPool<byte[]> {

    @Override
    protected byte[] getInstance(int order) {
        return new byte[order];
    }

}
