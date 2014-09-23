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
			return getFinalized(Boolean.TRUE);
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
	    private static abstract class Adder {
	        public abstract void add(byte b);
            public abstract byte[] get();
	    };
	    
		private byte remainder = 0x00;
		private byte padCount = 0;
		private byte state = 0;
		private Adder adder;
		private byte[] bArray = null;
		
		public Decoder(int size, boolean exact)
		{
			if (exact)
			{
				adder = new Adder() {
				    private int bArrayIdx = 0;
			        private byte[] array;
			        

                    @Override
                    public void add(byte b)
                    {
                        array[bArrayIdx++] = b;
                    }


                    @Override
                    public byte[] get()
                    {
                        byte[] rval = array;
                        
                        array = null;
                        
                        return rval;
                    }
				    
				};
			}
			else
			{
				adder = new Adder() {
                    private ArrayList<Byte> bList;
                    

                    @Override
                    public void add(byte b)
                    {
                        bList.add(b);
                    }


                    @Override
                    public byte[] get()
                    {
                        int idx = 0;
                        int size = bList.size();
                        byte[] rval = new byte[size];
                        
                        for (; idx < size; idx++)
                        {
                            rval[idx] = bList.get(idx);
                        }
                        
                        bList = null;
                        
                        return rval;
                    }
                    
                };
			}
		}
		
		public void update(byte b)
		{
			assert_(adder != null); // not finalized
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
			
			assert_(padCount == 0);
			
			b = get(b);
			
			switch (state)
			{
				case 0:
					// nothing, store bot 6
					remainder = (byte) ((b << 2) & 0xFC);
					break;
				case 1:
					// top 2, store bot 4
					adder.add((byte) (remainder | ((b >> 4) & 0x03)));
					remainder = (byte) ((b << 4) & 0xF0);
					break;
				case 2:
					// top 4, store bot 2
				    adder.add((byte) (remainder | ((b >> 2) & 0x0F)));
					remainder = (byte) ((b << 6) & 0xC0);
					break;
				case 3:
					// top 6, store nothing
				    adder.add((byte) (remainder | (b & 0x3F)));
					break;
				default:
					assert_(false);
			}
			
			state++;
			state %= 4;
		}
		
		private byte[] _getFinalized()
		{
			if (adder != null)
			{
			    Adder adder = this.adder;
			    this.adder = null;
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
				bArray = adder.get();
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
			return getFinalized(Boolean.TRUE);
		}
		
		private static byte get(byte b)
		{
			byte rval;
			
			switch (b)
			{
			    case '/':
			        rval = 4;
			    break;
                case '+':
                    rval = 3;
                break;
                default:
                    if (b >= '0' && b <= '9')
                    {
                        rval = 2;
                        break;
                    }
                    if (b >= 'a' && b <= 'z')
                    {
                        rval = 1;
                        break;
                    }
                    if (b >= 'A' && b <= 'Z')
                    {
                        rval = 0;
                        break;
                    }
                    assert_(false);
                    // unreachable, but makes compiler happy
                    rval = 0;
                break;
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
