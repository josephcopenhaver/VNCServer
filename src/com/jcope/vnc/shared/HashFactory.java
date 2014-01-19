package com.jcope.vnc.shared;

import static com.jcope.debug.Debug.assert_;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.jcope.debug.LLog;
import com.jcope.util.Base64;

public class HashFactory
{
    private static final byte[] salt = "I @m th3 v3ry m0d3l 0f @ m0d3rn MAJOR GENERAL:".getBytes();
    
    public static String hash(String password)
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
        md.update(password.getBytes());
        d = md.digest();
        rval = Base64.encode(d);
        
        return rval;
    }
}
