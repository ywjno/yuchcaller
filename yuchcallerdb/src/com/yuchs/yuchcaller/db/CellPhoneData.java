package com.yuchs.yuchcaller.db;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * cell phone data
 * @author tzz
 *
 */
public class CellPhoneData extends PhoneData
{
	public short			m_phoneNumberEnd;
	public byte		m_carrier;
	
	public void Read(InputStream in)throws Exception{
		super.Read(in);
		
		m_phoneNumberEnd	= sendReceive.ReadShort(in);
		m_carrier		= (byte)in.read(); 
	}
	
	public void Write(OutputStream os)throws Exception{
		super.Write(os);
		
		sendReceive.WriteShort(os,m_phoneNumberEnd);
		os.write(m_carrier);
	}
}
