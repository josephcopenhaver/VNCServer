package com.jcope.util;

import static com.jcope.debug.Debug.assert_;

public class Base64
{
	private static final class Encoder
	{
		StringBuffer sb;
		private byte remainder = 0x00;
		private int state = 0;
		private boolean isFinalized = Boolean.FALSE;
		
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
					remainder = 0x00;
					break;
				default:
					assert_(false);
					break;
			}
			state++;
			state %= 3;
		}
		
		public String getFinalized()
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
						// Fall Through
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
			}
			
			return sb.toString();
		}
		
		private static char get(byte b)
		{
			char rval;
			
			assert_(b >= 0);
			assert_(b <= 63);
			
			if (b <= 25)
			{
				rval = (char) (((byte)'A') + b);
			}
			else if (b <= 51)
			{
				rval = (char) (((byte)'a') + (b-26));
			}
			else if (b <= 61)
			{
				rval = (char) (((byte)'0') + (b-52));
			}
			else if (b == 62)
			{
				rval = '+';
			}
			else
			{
				rval = '/';
			}
			
			return rval;
		}
	}
	
	public static String encode(byte[] b)
	{
		Encoder encoder = new Encoder((b.length/3)*4 + (b.length%3==0 ? 0 : 3));
		for (byte x : b)
		{
			encoder.update(x);
		}
		
		return encoder.getFinalized();
	}
}
