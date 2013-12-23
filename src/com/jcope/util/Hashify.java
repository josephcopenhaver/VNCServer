package com.jcope.util;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.CRC32;



public class Hashify
{
	
    public static void main(String[] args) throws IOException, NoSuchAlgorithmException
	{
		
		byte[] buffer = new byte[40 * 1024 * 1024];
		String[] hashMethods = {"SHA-1", "MD5", "SHA-256", "crc32"};
		
		Object[] digests = new Object[hashMethods.length];
		int digestNum = 0;
		for (String digestType : hashMethods)
		{
			if (digestType.equalsIgnoreCase("crc32"))
			{
				digests[digestNum] = new CRC32();
			}
			else
			{
				digests[digestNum] = MessageDigest.getInstance(digestType);
			}
			digestNum++;
		}
		
		for (String fileName : args)
		{
			BufferedInputStream in = new BufferedInputStream(new FileInputStream(fileName));
			int bytesRead = 0;
			while ((bytesRead = in.read(buffer)) != -1)
			{
				for (digestNum=0; digestNum < hashMethods.length; digestNum++)
				{
					Object digestObj = digests[digestNum];
					
					if (digestObj instanceof MessageDigest)
					{
						((MessageDigest)digestObj).update(buffer, 0, bytesRead);
					}
					else if (digestObj instanceof CRC32)
					{
						((CRC32)digestObj).update(buffer, 0, bytesRead);
					}
				}
			}
			in.close();
			for (digestNum=0; digestNum < hashMethods.length; digestNum++)
			{
				Object digestObj = digests[digestNum];
				String hexStr = null;
				
				if (digestObj instanceof MessageDigest)
				{
					MessageDigest messageDigestObject = ((MessageDigest)digestObj);
					hexStr = byteArrayToHexString(messageDigestObject.digest());
					messageDigestObject.reset();
				}
				else if (digestObj instanceof CRC32)
				{
					CRC32 crc32Obj = ((CRC32)digestObj);
					hexStr = longToHexString(crc32Obj.getValue());
					crc32Obj.reset();
				}
				
				System.out.println(String.format("\"%s\",%s,%s", fileName, hashMethods[digestNum], hexStr));
			}
		}
	}
	
	public static String longToHexString(long val)
	{
		return String.format("%1$#8s", Long.toHexString(val).toUpperCase(), "0");
	}
	
	public static String byteArrayToHexString(byte[] b)
	{
		StringBuffer sb = new StringBuffer(b.length * 2);
		for (int i = 0; i < b.length; i++)
		{
			int v = b[i] & 0xff;
			if (v < 16)
			{
				sb.append('0');
			}
			sb.append(Integer.toHexString(v));
	    }
		return sb.toString().toUpperCase();
	}
}