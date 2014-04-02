package com.jcope.util;

import static com.jcope.debug.Debug.assert_;

import java.util.ArrayList;

public class Base64
{
	private static final class Encoder
	{
		StringBuffer sb;
		private byte remainder = 0x00;
		private byte state = 0;
		private boolean isFinalized = Boolean.FALSE;
		private String finVal = null;
		
		private static final byte[] offsets = new byte[] {
			(byte)'A',
			((byte)'a')-26,
			((byte)'0')-52,
			((byte)'+')-62,
			((byte)'/')-63
		};
		
		private static final byte[] bounds = new byte[] {
			25,
			51,
			61,
			62
		};
		
		public Encoder(int size)
		{
			sb = new StringBuffer(size);
		}
		
		public void update(byte b)
		{
			assert_(!isFinalized);
			switch (state)
			{
				case 0:
					// top 6, store bot 2
					sb.append(get((byte)((b >> 2) & 0x3F)));
					remainder = (byte) ((b << 4) & 0x30);
					break;
				case 1:
					// top 4, store bot 4
					sb.append(get((byte)((b >> 4) & 0x0F | remainder)));
					remainder = (byte) ((b << 2) & 0x3C);
					break;
				case 2:
					// top 2, bot 6;
					sb.append(get((byte)((b >> 6) & 0x03 | remainder)));
					sb.append(get((byte)(b & 0x3F)));
					break;
				default:
					assert_(false);
					break;
			}
			state++;
			state %= 3;
		}
		
		private String _getFinalized()
		{
			if (!isFinalized)
			{
				isFinalized = Boolean.TRUE;
				switch (state)
				{
					case 0:
						// Do Nothing
						break;
					case 1:
					case 2:
						sb.append(get(remainder));
						break;
					default:
						assert_(false);
						break;
				}
				switch (state)
				{
					case 0:
						// Do Nothing
						break;
					case 1:
						sb.append('=');
						// Fall Through
					case 2:
						sb.append('=');
						break;
					default:
						assert_(false);
						break;
				}
				
				finVal = sb.toString();
				sb = null;
			}
			
			return finVal;
		}
		
		public String getFinalized(boolean popVal)
		{
			String rval = _getFinalized();
			
			if (popVal)
			{
				finVal = null;
			}
			
			return rval;
		}
		
		public String getFinalized()
		{
			return getFinalized(true);
		}
		
		private static char get(byte b)
		{
			byte rval = 0;
			
			assert_(b >= 0);
			assert_(b <= 63);
			
			while (rval<bounds.length)
			{
				if (b <= bounds[rval])
				{
					break;
				}
				rval++;
			}
			
			rval = offsets[rval];
			
			rval += b;
			
			return (char) rval;
		}
	}
	
	private static final class Decoder
	{
		private ArrayList<Byte> bList;
		private int bArrayIdx = 0;
		private byte[] bArray;
		private byte remainder = 0x00;
		private byte padCount = 0;
		private byte state = 0;
		private boolean isFinalized = Boolean.FALSE;
		
		public Decoder(int size, boolean exact)
		{
			if (exact)
			{
				bArray = new byte[size];
				bList = null;
			}
			else
			{
				bArray = null;
				bList = new ArrayList<Byte>(size);
			}
		}
		
		private void add(byte b)
		{
			if (bArray == null)
			{
				bList.add(b);
			}
			else
			{
				bArray[bArrayIdx++] = b;
			}
		}
		
		public void update(byte b)
		{
			assert_(!isFinalized);
			switch (state)
			{
				case 0:
				case 1:
					assert_(b != '=');
					break;
				case 2:
					if (b == '=')
					{
						assert_(padCount <= 1);
						padCount++;
						return;
					}
					break;
				case 3:
					if (b == '=')
					{
						assert_(padCount == 0);
						padCount++;
						return;
					}
					break;
				default:
					assert_(false);
			}
			
			b = get(b);
			
			switch (state)
			{
				case 0:
					// nothing, store bot 6
					remainder = (byte) ((b << 2) & 0xFC);
					break;
				case 1:
					// top 2, store bot 4
					add((byte) (remainder | ((b >> 4) & 0x03)));
					remainder = (byte) ((b << 4) & 0xF0);
					break;
				case 2:
					// top 4, store bot 2
					add((byte) (remainder | ((b >> 2) & 0x0F)));
					remainder = (byte) ((b << 6) & 0xC0);
					break;
				case 3:
					// top 6, store nothing
					add((byte) (remainder | (b & 0x3F)));
					break;
				default:
					assert_(false);
			}
			
			state++;
			state %= 4;
		}
		
		private byte[] _getFinalized()
		{
			if (!isFinalized)
			{
				isFinalized = Boolean.TRUE;
				switch (state)
				{
					case 0:
						assert_(padCount == 0);
						break;
					case 2:
						assert_(padCount == 2);
						break;
					case 3:
						assert_(padCount == 1);
						break;
					default:
						assert_(false);
				}
				switch (state)
				{
					case 0:
						break;
					case 2:
					case 3:
						assert_(remainder == 0x00);
						break;
					default:
						assert_(false);
				}
				if (bArray == null)
				{
					bArray = new byte[bList.size()];
					
					for (Byte b : bList)
					{
						bArray[bArrayIdx++] = b;
					}
					bList = null;
				}
				assert_(bArray.length == bArrayIdx);
			}
			
			return bArray;
		}
		
		public byte[] getFinalized(boolean popVal)
		{
			byte[] rval = _getFinalized();
			
			if (popVal)
			{
				bArray = null;
			}
			
			return rval;
		}
		
		public byte[] getFinalized()
		{
			return getFinalized(true);
		}
		
		private static byte get(byte b)
		{
			byte rval = 0;
			
			if (b == '/')
			{
				rval = 4;
			}
			else if (b == '+')
			{
				rval = 3;
			}
			else if (b >= '0' && b <= '9')
			{
				rval = 2;
			}
			else if (b >= 'a' && b <= 'z')
			{
				rval = 1;
			}
			else if (b >= 'A' && b <= 'Z')
			{
				// Do Nothing
			}
			else
			{
				assert_(false);
			}
			
			rval = Encoder.offsets[rval];
			
			rval = (byte) (b - rval);
			
			return rval;
		}
	}
	
	public static String encode(byte[] b)
	{
		Encoder encoder = new Encoder(((b.length+2)/3)*4);
		
		for (byte x : b)
		{
			encoder.update(x);
		}
		
		return encoder.getFinalized();
	}
	
	public static byte[] decode(byte[] b)
	{
		assert_(b.length%4 == 0);
		
		int size = (b.length/4)*3;
		
		if (b.length > 1 && b[b.length-2] == '=')
		{
			assert_(b[b.length-1] == '=');
			size -= 2;
		}
		else if (b.length > 0 && b[b.length-1] == '=')
		{
			size--;
		}
		
		Decoder decoder = new Decoder(size, true);
		
		for (byte x : b)
		{
			decoder.update(x);
		}
		
		return decoder.getFinalized();
	}
}
