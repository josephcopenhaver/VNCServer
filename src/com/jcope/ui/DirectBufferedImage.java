package com.jcope.ui;

import static com.jcope.debug.Debug.assert_;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.DirectColorModel;
import java.awt.image.Raster;
import java.util.Hashtable;

public class DirectBufferedImage extends BufferedImage
{
	
	private static DirectColorModel dcm = null;
	private int[] src;
	private int size;
	private int width;
	private int height;
	private boolean hasAlpha;
	
	public static final int INFO_INT_NUM_FIELDS = 4;

	public DirectBufferedImage(int [] src, int size, int width, int height, boolean hasAlpha)
	{
		super((dcm = (dcm != null) ? dcm : new DirectColorModel(32, 0xff0000, 0xff00, 0xff, (hasAlpha ? 0x00000000 : 0xff000000))), Raster.createWritableRaster(dcm.createCompatibleSampleModel(width, height), new DataBufferInt(src, size), null), false, new Hashtable<Object, Object>());
		this.src = src;
		this.size = size;
		this.width = width;
		this.height = height;
		this.hasAlpha = hasAlpha;
	}
	
	public DirectBufferedImage(int [] src, int[] info)
	{
		this(src, info[0], info[1], info[2], info[3] != 0);
		assert_(info.length >= INFO_INT_NUM_FIELDS);
	}
	
	public DirectBufferedImage(DirectBufferedImage src)
	{
		this(src.src, src.size, src.width, src.height, src.hasAlpha);
	}
	
	public void getInfo(int[] info)
	{
		info[0] = size;
		info[1] = width;
		info[2] = height;
		info[3] = hasAlpha ? 1 : 0;
	}
	
	public int[] getSrc()
	{
		int[] rval;
		
		if (size <= src.length)
		{
			rval =  src;
		}
		else
		{
			rval = new int[size];
			System.arraycopy(src, 0, rval, 0, rval.length);
		}
		
		return rval;
	}

}