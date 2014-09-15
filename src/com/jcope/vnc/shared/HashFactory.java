package com.jcope.vnc.shared;

import static com.jcope.debug.Debug.assert_;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import com.jcope.debug.LLog;
import com.jcope.util.Base64;

public class HashFactory
{
    private static final byte[] salt = "I @m th3 v3ry m0d3l 0f @ m0d3rn MAJOR GENERAL:".getBytes();
    
    public static final byte[] charsToBytes(char[] chars, boolean destroyOriginal)
    {
        byte[] rval = new byte[chars.length * 2];
        
        int idxIn = 0,
            idxOut = 0;
        char c;
        
        while (idxIn < chars.length)
        {
            c = chars[idxIn];
            rval[idxOut++] = (byte) (c & 0xff);
            rval[idxOut++] = (byte) ((c & 0xff00) >> 8);
            if (destroyOriginal)
            {
                chars[idxIn] = 0;
            }
            idxIn++;
        }
        c = (char) 0;
        
        return rval;
    }
    
    public static String hash(char[] password)
    {
        String rval;
        byte[] d;
        MessageDigest md = null;
        
        //assert_(!password.equals(""));
        
        try
        {
            md = MessageDigest.getInstance("SHA-256");
        }
        catch (NoSuchAlgorithmException e)
        {
            LLog.e(e);
        }
        assert_(md != null);
        md.update(salt);
        byte[] tmp = charsToBytes(password, Boolean.TRUE);
        md.update(tmp);
        Arrays.fill(tmp, (byte) 0);
        d = md.digest();
        rval = Base64.encode(d);
        
        return rval;
    }
}
