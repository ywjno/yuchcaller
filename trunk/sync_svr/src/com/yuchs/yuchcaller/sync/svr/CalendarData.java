package com.yuchs.yuchcaller.sync.svr;

import java.io.InputStream;
import java.io.OutputStream;

public class CalendarData {

	/**
	 * value of free_busy field
	 */
	public static final int	FB_FREE			= 0;
	public static final int	FB_TENTATIVE	= 1;
	public static final int	FB_BUSY			= 2;
	public static final int	FB_OUT_OF_OFFICE = 3;
	
	
	/**
	 * class style 
	 */
	public static final int CLASS_CONFIDENTIAL 	= 0;
	public static final int CLASS_PRIVATE 		= 1;
	public static final int CLASS_PUBLIC 			= 2;

	
	/**
	 * summary of the calendar
	 */
	public String summary = null;
	
	/**
	 * start time
	 */
	public long	start	= 0;
		
	/**
	 * end time
	 */
	public long	end		= 0;
	
	/**
	 * location
	 */
	public String	location = null;
	
	/**
	 * alarm
	 */
	public int		alarm	= 0;
	
	/**
	 * note of this calendar event
	 */
	public String note		= null;
	
	/**
	 * whether is allday
	 */
	public boolean allDay	= false;
	
	/**
	 * A list of e-mail addresses identifying attendees at an Event.
	 */
	public String[]	attendees = null;
	
	/**
	 * the free_busy status
	 */
	public int free_busy	= FB_BUSY;
	
	/**
	 * the type of class
	 */
	public int event_class	= CLASS_PRIVATE;
	
	/**
	 * the repeat type
	 */
	public String repeat_type = "";
	
	/**
	 * import the data from stream
	 * @param _in
	 */
	public void inputData(InputStream _in)throws Exception{
		summary = sendReceive.ReadString(_in);
		
		start	= sendReceive.ReadLong(_in);
		end		= sendReceive.ReadLong(_in);
		
		location= sendReceive.ReadString(_in);
		alarm	= sendReceive.ReadInt(_in);
		note	= sendReceive.ReadString(_in);
		
		allDay	= sendReceive.ReadBoolean(_in);
		
		attendees = new String[sendReceive.ReadInt(_in)];

		for(int i = 0;i < attendees.length;i++){
			attendees[i] = sendReceive.ReadString(_in);
		}		
		
		free_busy	= _in.read();
		event_class = _in.read();
		repeat_type	= sendReceive.ReadString(_in);
	}
	
	/**
	 * output data to a stream
	 * @param _os
	 * @throws Excetion
	 */
	public void outputData(OutputStream _os)throws Exception{
				
		sendReceive.WriteString(_os,summary);
		
		sendReceive.WriteLong(_os, start);
		sendReceive.WriteLong(_os, end);
		
		sendReceive.WriteString(_os, location);
		sendReceive.WriteInt(_os, alarm);
		sendReceive.WriteString(_os, note);
		
		sendReceive.WriteBoolean(_os, allDay);
		
		if(attendees != null){
			sendReceive.WriteInt(_os, attendees.length);

			for(int i = 0;i < attendees.length;i++){
				sendReceive.WriteString(_os,attendees[i]);
			}
		}else{
			sendReceive.WriteInt(_os, 0);
		}
		
		_os.write(free_busy);
		_os.write(event_class);
		sendReceive.WriteString(_os, repeat_type);
	}
}
