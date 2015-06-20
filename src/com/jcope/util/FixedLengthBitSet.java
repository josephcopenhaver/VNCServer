package com.jcope.util;

import static com.jcope.debug.Debug.assert_;

import java.io.Serializable;
import java.util.BitSet;

public class FixedLengthBitSet implements Serializable {
    /**
     * Generated serialVersionUID
     */
    private static final long serialVersionUID = -2000986485513344737L;

    public final int length;
    private BitSet bset;

    public FixedLengthBitSet clone() {
        return new FixedLengthBitSet(this);
    }

    private FixedLengthBitSet(int length, BitSet bitSetToWrap) {
        this.length = length;
        this.bset = bitSetToWrap;
    }

    private FixedLengthBitSet(FixedLengthBitSet origin) {
        this(origin.length, (BitSet) origin.bset.clone());
    }

    public FixedLengthBitSet(int length) {
        this(length, Boolean.FALSE);
    }

    public FixedLengthBitSet(int length, boolean initBitOn) {
        this.length = length;
        bset = new BitSet(length);

        if (initBitOn) {
            fill(initBitOn);
        }
    }

    private final void checkIdx(int idx) {
        if (idx < 0 || idx >= length) {
            throw new IllegalArgumentException();
        }
    }

    public void set(int idx, boolean bitOn) {
        checkIdx(idx);
        bset.set(idx, bitOn);
    }

    public boolean get(int idx) {
        checkIdx(idx);
        return bset.get(idx);
    }

    public void fill(boolean bitOn) {
        bset.set(0, length, bitOn);
    }

    public int nextSetBit(int fromIndex) {
        return bset.nextSetBit(fromIndex);
    }

    public void or(FixedLengthBitSet flbs) {
        assert_(length == flbs.length);
        bset.or(flbs.bset);
    }

    public void andNot(FixedLengthBitSet flbs) {
        assert_(length == flbs.length);
        bset.andNot(flbs.bset);
    }

    public boolean isEmpty() {
        return bset.isEmpty();
    }

}
