package com.yuchs.yuchcaller.sync;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.TimeZone;
import java.util.Vector;

import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;
import javax.microedition.io.file.FileConnection;
import javax.microedition.pim.Event;
import javax.microedition.pim.EventList;
import javax.microedition.pim.PIM;

import net.rim.device.api.compress.GZIPInputStream;
import net.rim.device.api.compress.GZIPOutputStream;
import net.rim.device.api.crypto.MD5Digest;
import net.rim.device.api.io.http.HttpProtocolConstants;

import com.yuchs.yuchcaller.ConnectorHelper;
import com.yuchs.yuchcaller.YuchCaller;
import com.yuchs.yuchcaller.YuchCallerProp;
import com.yuchs.yuchcaller.sendReceive;

public class SyncMain {
	
	public YuchCaller	m_mainApp;
	
	private boolean	m_isSyncing = false;
	
	// mark whether read the calendar list
	private boolean	mReadCalendarList = false;
		
	// calendar sync list
	private Vector	mCalendarSyncList = new Vector();
	
	/**
	 * find the calendar sync data in list
	 * @param _bbID		bid of event
	 * @return
	 */
	private CalendarSyncData getCalendarSyncData(String _bbid){
		
		for(int i = 0;i < mCalendarSyncList.size();i++){
			CalendarSyncData d = (CalendarSyncData)mCalendarSyncList.elementAt(i); 
			if(d.getBBID().equals(_bbid)){
				return d;
			}
		}
		
		return null;
	}
	
	/**
	 * remove the calendar sync data in main list
	 * @param _bbid
	 */
	private void removeCalendarSyncData(String _bbid){
		
		for(int i = 0;i < mCalendarSyncList.size();i++){
			CalendarSyncData d = (CalendarSyncData)mCalendarSyncList.elementAt(i); 
			if(d.getBBID().equals(_bbid)){
				mCalendarSyncList.removeElementAt(i);
				break;
			}
		}
	}
	
	
		
	public SyncMain(YuchCaller _mainApp){
		m_mainApp = _mainApp;
		
	}
	
	/**
	 * get the md5 string
	 * @param _org
	 * @return
	 */
	public static String md5(String _org){
		
		byte[] bytes = null;
		try{
			bytes = _org.getBytes("UTF-8");
		}catch(Exception e){
			bytes = _org.getBytes();
		}
		
		return md5(bytes);
		
	}
	
	/**
	 * get the md5
	 * @param _data
	 * @return
	 */
	public static String md5(byte[] _data){
		MD5Digest digest = new MD5Digest();
		
		digest.update(_data, 0, _data.length);

		byte[] md5 = new byte[digest.getDigestLength()];
		digest.getDigest(md5, 0, true);
		
		return convertToHex(md5);
	}

	/**
	 * convert bytes to hex string
	 * @param data
	 * @return
	 */
	public static String convertToHex(byte[] data) {
        StringBuffer buf = new StringBuffer();
        for (int i = 0; i < data.length; i++) {
            int halfbyte = (data[i] >>> 4) & 0x0F;
            int two_halfs = 0;
            do {
                if ((0 <= halfbyte) && (halfbyte <= 9))
                    buf.append((char) ('0' + halfbyte));
                else
                    buf.append((char) ('a' + (halfbyte - 10)));
                halfbyte = data[i] & 0x0F;
            } while(two_halfs++ < 1);
        }
        
        return buf.toString();
    }
		
	public void startSync(){
		
		if(m_isSyncing){
			return ;
		}
		
		m_mainApp.invokeLater(new Runnable() {
			
			// started in own context
			//
			public void run() {

				(new Thread(){
					public void run(){
						m_isSyncing = true;
						startSyncImpl();
						m_isSyncing = false;
					}
				}).start();	
			}
		});
	}
	
	private void startSyncImpl(){
		
		// read yuch account 
//		if(!readYuchAccount()){
//			return;
//		}
		
		// read the calendar information in bb database
		readBBCalendar();
		
		// read sync file (gID for bID)
		readWriteSyncFile(true);
				
		// sync
		syncRequest();
	}
	
	//! report error
	private void reportError(String error){
		System.err.println("Error: " + error);
	}
	
	//! report error
	private void reportError(String errorLabel,Exception e){
		System.err.println(errorLabel + ": " + e.getClass().getName() + " " + e.getMessage());
		m_mainApp.SetErrorString(errorLabel, e);
	}
	
	// report the information
	private void reportInfo(String info){
		System.out.println("info: " + info);
	}
	
	//! read yuch account and request the Refresh/Access token
	private boolean readYuchAccount(){
		
		YuchCallerProp tProp = m_mainApp.getProperties();
		
		if(tProp.getYuchAccount().length() == 0 || tProp.getYuchPass().length() == 0){
			return false;
		}
		
		reportInfo("Reading Yuch Account...");
		
		if(tProp.getYuchAccessToken().length() == 0 || tProp.getYuchRefreshToken().length() == 0){
			
			// request the yuch server
			
			String url = "http://192.168.10.4:8888/f/login/";
			//String url = "http://www.yuchs.com/f/login/";
			
			url += YuchCaller.getHTTPAppendString();
			
			String[] tParamName = {
				"acc",	"pass",	"type",
			};
			
			String[] tParamValue = {
				tProp.getYuchAccount(),
				tProp.getYuchPass(),				
				"yuchcaller",
			};
		
			try{
				String tResult = requestPOSTHTTP(url,tParamName,tParamValue);
				
				if(tResult.startsWith("<Error>")){
					reportError(tResult.substring(7));
				}else{
					int tSpliter = tResult.indexOf("|");
					
					if(tSpliter != -1){
						tProp.setYuchRefreshToken(tResult.substring(0,tSpliter));
						tProp.setYuchAccessToken(tResult.substring(tSpliter + 1));
						
						tProp.save();
						
						m_mainApp.SetErrorString("sync: read Yuch done!");
						
						return true;
						
					}else{
						reportError("Unkown:" + tResult);
					}					
				}
				
			}catch(Exception e){
				// network problem
				reportError("Can not get the YuchAccount",e);
			}			
		}
		
		return false;
	}
	
	/**
	 * read the calendar information from bb calendar
	 */
	private void readBBCalendar(){
		
		if(mReadCalendarList){
			return ;
		}
		
		reportInfo("Reading Bb Calendar...");
		
		try{
			
			EventList t_events = (EventList)PIM.getInstance().openPIMList(PIM.EVENT_LIST,PIM.READ_ONLY);
			try{

				Enumeration t_allEvents = t_events.items();
				
				Vector t_eventList = new Vector();
			    if(t_allEvents != null){
				    while(t_allEvents.hasMoreElements()) {			    	
				    	t_eventList.addElement(t_allEvents.nextElement());
				    }
			    }
			    			    
			    mCalendarSyncList.removeAllElements();
			    for(int i = 0;i < t_eventList.size();i++){
			    	
			    	Event event = (Event)t_eventList.elementAt(i);
			    	
			    	CalendarSyncData syncData = new CalendarSyncData();
			    	syncData.importData(event,t_events);
			    					    	
			    	mCalendarSyncList.addElement(syncData);
			    }			    
			    
			    mReadCalendarList = true;
			    
			}finally{
				t_events.close();
				t_events = null;
			}
		    
		}catch(Exception e){
			reportError("Can not read calendar event list",e);
		}
	}

	/**
	 * the calendar data file
	 */
	static final String fsm_calendarFilename 		= YuchCallerProp.fsm_rootPath_back + "YuchCaller/calendar.data";

	/**
	 * the version fo sync file
	 */
	static final int SyncFileVersion = 0;
	
	/**
	 * check the sync file
	 */
	private void readWriteSyncFile(boolean _read){
		
		m_mainApp.getProperties().preWriteReadIni(_read, fsm_calendarFilename);
				
		try{
			FileConnection fc = (FileConnection) Connector.open(fsm_calendarFilename,Connector.READ_WRITE);
			try{
				if(_read){
					
					if(!fc.exists()){
						return;
					}
					
					InputStream in = fc.openInputStream();
					try{
						int tVersion = in.read();
						
						int num = sendReceive.ReadInt(in);
						for(int i= 0;i < num;i++){
							String	bid = sendReceive.ReadString(in);
							String	gid = sendReceive.ReadString(in);
							long	mod = sendReceive.ReadLong(in);
													
							CalendarSyncData syncData = getCalendarSyncData(bid);
							if(syncData != null){
								syncData.setGID(gid);
								syncData.setLastMod(mod);
							}
						}
					}finally{
						in.close();
						in = null;
					}
					
					
				}else{
					
					if(!fc.exists()){
						fc.create();					
					}
					
					OutputStream os = fc.openOutputStream();
					try{
						os.write(SyncFileVersion);
						sendReceive.WriteInt(os, mCalendarSyncList.size());
						
						for(int idx = 0;idx < mCalendarSyncList.size();idx++){

							CalendarSyncData syncData = (CalendarSyncData)mCalendarSyncList.elementAt(idx);
							
							sendReceive.WriteString(os, syncData.getBBID());
							sendReceive.WriteString(os, syncData.getGID());
							sendReceive.WriteLong(os, syncData.getLastMod());
						}
						
					}finally{
						os.close();
						os = null;
					}				
				}			
				
			}finally{
				fc.close();
				fc = null;
			}
		}catch(Exception e){
			reportError("Can not read " + fsm_calendarFilename,e);
		}
		
		m_mainApp.getProperties().postWriteReadIni(fsm_calendarFilename);
	}
	
	/**
	 * write the account information to a OutputStream
	 * @param os
	 * @param type
	 * @param md5		sync data md5
	 * @param diffType 	of sync
	 * @throws Exception
	 */
	private void writeAccountInfo(OutputStream os,String type,String md5,long minSyncTime,int diffType)throws Exception{
		
		YuchCallerProp tProp = m_mainApp.getProperties();
		
		// write the version
		sendReceive.WriteShort(os,(short)0);
		sendReceive.WriteString(os, type);
		
		// send the min time for sync
		sendReceive.WriteLong(os, minSyncTime);
		
		//sendReceive.WriteString(os,tProp.getYuchRefreshToken());
		//sendReceive.WriteString(os,tProp.getYuchAccessToken());
		
		sendReceive.WriteString(os,"1/VQPrbZhyWhXrYP6eVNnwkQwj2RQK3Gyc1q-3k08sKxE");
		sendReceive.WriteString(os,"ya29.AHES6ZQr1KFYYlAqCoU0H6ag1q9EI7kwOcMNynpIYXsxtJlqUAe9");
		
		sendReceive.WriteString(os,tProp.getYuchAccount());
		
		sendReceive.WriteString(os,TimeZone.getDefault().getID());
		sendReceive.WriteString(os,md5);
		
		// write the diff type
		os.write(diffType);
	}
	
	/**
	 * sync request
	 * 
	 * work following
	 * 
	 * 		client			server
	 * 		|					|
	 * 		Mod md5------------>md5 compare
	 * 		|					|
	 * 		succ(no change)<----nochange or diff 
	 * 		|					|
	 * 		|					|
	 * 		|					|
	 * 		diff list---------->diff list process ( diff type 0)
	 * 		|					|
	 * 		process<-----------add/updat/upload/delete/needlist
	 * 		|					|
	 * 		needList---------->updated google calendar  ( diff type 1)
	 * 		|					|
	 * 		succ<--------------mod time list
	 * 
	 */
	private void syncRequest(){
		try{

			reportInfo("Request sync...");
			
			ByteArrayOutputStream os = new ByteArrayOutputStream();
			try{
				
				long tSyncMinTime = System.currentTimeMillis() - m_mainApp.getProperties().getSyncFormerDays() * 24 * 3600000L;
				
				String md5 = prepareCalenderMD5(tSyncMinTime);
				
				writeAccountInfo(os,"calendar",md5,tSyncMinTime,0);
				
				String url = "http://192.168.10.7:8888" + YuchCaller.getHTTPAppendString();
				//String url = "http://192.168.100.116:8888" + YuchCaller.getHTTPAppendString();
				//String url = "http://sync.yuchs.com:6029" + YuchCaller.getHTTPAppendString();
				
				String tResultStr = new String(requestPOSTHTTP(url,os.toByteArray(),true),"UTF-8");
										
				if(tResultStr.equals("succ")){
					
					// successfully!
					reportInfo("sync without any changed successfully!");
					
				}else if(tResultStr.equals("diff")){
					
					// write the diff sign
					InputStream diffIn = new ByteArrayInputStream(requestPOSTHTTP(url, outputDiffList(md5,tSyncMinTime) ,true));
					try{
						
						Vector tNeedList = processDiffList(diffIn);
						if(tNeedList != null){
							// send the need list to update server's event
							//
							sendNeedList(tNeedList,url,md5,tSyncMinTime);
						}
						
						reportInfo("sync succ!");
						
					}finally{
						diffIn.close();
						diffIn = null;
					}								
				}					
				
				
			}finally{
				os.close();
				os = null;
			}
			
		}catch(Exception e){
			// sync request failed
			reportError("Sync Error",e);
		}
	}
	
	/**
	 * get the different list 
	 */
	private byte[] outputDiffList(String md5,long minSyncTime)throws Exception{

		ByteArrayOutputStream os = new ByteArrayOutputStream();
		try{
			
			writeAccountInfo(os,"calendar",md5,minSyncTime,1);
			sendReceive.WriteInt(os, mCalendarSyncList.size());
			
			for(int idx = 0;idx < mCalendarSyncList.size();idx++){

				CalendarSyncData syncData = (CalendarSyncData)mCalendarSyncList.elementAt(idx);
				syncData.output(os,syncData.getGID() == null || syncData.getGID().length()  == 0);
			}			
			
			return os.toByteArray();
		}finally{
			os.close();
			os = null;
		}
	}
	
	/**
	 * send need list and process result
	 * @param needList
	 * @param _url
	 * @throws Exception
	 */
	private void sendNeedList(Vector needList,String _url,String _md5,long minSyncTime)throws Exception{
		
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		try{
			
			writeAccountInfo(os,"calendar",_md5,minSyncTime,2);
						
			// write the sync data
			sendReceive.WriteInt(os,needList.size());
			
			for(int i = 0;i < needList.size();i++){
				CalendarSyncData d = getCalendarSyncData(needList.elementAt(i).toString());
				d.output(os, true);				
			}
			
			InputStream in = new ByteArrayInputStream(requestPOSTHTTP(_url, os.toByteArray(), true));
			try{
				
				int num = sendReceive.ReadInt(in);
				for(int i = 0;i < num;i++){
					String bid		= sendReceive.ReadString(in);
					long modTime	= sendReceive.ReadLong(in);
					
					CalendarSyncData d = getCalendarSyncData(bid);
					d.setLastMod(modTime);
				}
				
				readWriteSyncFile(false);
				
			}finally{
				in.close();
				in = null;
			}
			
		}finally{
			os.close();
			os = null;
		}
		
	}
	
	/**
	 * process the different list
	 * @param in
	 * @return need list
	 */
	private Vector processDiffList(InputStream in)throws Exception{
		
		Vector tAddList		= null;
		Vector tDelList	 	= null;
		Vector tUploadList	 = null;
		Vector tUpdateList	= null;
		Vector tNeedList	= null;
		
		// get the add list
		int num = sendReceive.ReadInt(in);
		for(int i = 0;i < num;i++){
			CalendarSyncData e = new CalendarSyncData();
			e.input(in);
			
			if(tAddList == null){
				tAddList = new Vector();
			}
			tAddList.addElement(e);
		}
		
		// get the delete list
		num = sendReceive.ReadInt(in);
		for(int i = 0;i < num;i++){
			
			if(tDelList == null){
				tDelList = new Vector();
			}
			
			// add the BID to delete
			tDelList.addElement(sendReceive.ReadString(in));
		}
		
		// get the update list
		num = sendReceive.ReadInt(in);
		for(int i = 0;i < num;i++){
			CalendarSyncData e = new CalendarSyncData();
			e.input(in);
			
			if(tUpdateList == null){
				tUpdateList = new Vector();
			}
			tUpdateList.addElement(e);
		}
		
		// get the upload list
		num = sendReceive.ReadInt(in);
		for(int i = 0;i < num;i++){
			CalendarSyncData e = new CalendarSyncData();
			
			e.setBBID(sendReceive.ReadString(in));
			e.setGID(sendReceive.ReadString(in));
			e.setLastMod(sendReceive.ReadLong(in));
			
			if(tUploadList == null){
				tUploadList = new Vector();
			}
			tUploadList.addElement(e);
		}
		
		// get the need list
		num = sendReceive.ReadInt(in);
		for(int i = 0;i < num;i++){
			if(tNeedList == null){
				tNeedList = new Vector();
			}
			
			// add the BID to update
			tNeedList.addElement(sendReceive.ReadString(in));
		}
		
		EventList tEvents = null;
		Vector t_eventList =null;
		try{
			
			if(tAddList != null || tDelList != null || tUpdateList != null){
				
				tEvents = (EventList)PIM.getInstance().openPIMList(PIM.EVENT_LIST,PIM.READ_WRITE);
				
				Enumeration t_allEvents = tEvents.items();
				t_eventList =  new Vector();
				
			    if(t_allEvents != null){
				    while(t_allEvents.hasMoreElements()) {			    	
				    	t_eventList.addElement(t_allEvents.nextElement());
				    }
			    }
			}
			
			if(tAddList != null){
				// add the event to bb system
				//
				for(int i = 0;i < tAddList.size();i++){
					CalendarSyncData d = (CalendarSyncData)tAddList.elementAt(i);
					
					Event e = tEvents.createEvent();
					d.exportData(e,tEvents);
					
					e.commit();
					d.setBBID(e.getString(Event.UID, 0));
					
					// added to main list
					mCalendarSyncList.addElement(d);
				}
			}
			
			if(tDelList != null){
				// delete the event of bb system
				//
				if(tEvents == null){
					tEvents = (EventList)PIM.getInstance().openPIMList(PIM.EVENT_LIST,PIM.READ_WRITE);
				}
				
				for(int i = 0;i < tDelList.size();i++){
					String bid = (String)tDelList.elementAt(i);
					
					CalendarSyncData d = getCalendarSyncData(bid);
					if(d != null){
						
						for(int idx = 0;idx < t_eventList.size();idx++){
							
							Event e = (Event)t_eventList.elementAt(idx);
							
							if(e.getString(Event.UID, 0).equals(d.getBBID())){
								tEvents.removeEvent(e);
								t_eventList.removeElement(e);
								break;
							}
						}
						
						removeCalendarSyncData(d.getBBID());
					}
				}
			}
			
			if(tUploadList != null){
				// upload list (refresh current bb calendar's GID)
				//
				for(int i = 0;i < tUploadList.size();i++){
					CalendarSyncData uploaded = (CalendarSyncData)tUploadList.elementAt(i);
					
					CalendarSyncData d = getCalendarSyncData(uploaded.getBBID());
					if(d != null){
						d.setGID(uploaded.getGID());
						d.setLastMod(uploaded.getLastMod());
					}
				}
			}
			
			if(tUpdateList != null){
				// update the event in bb system
				//
				if(tEvents == null){
					tEvents = (EventList)PIM.getInstance().openPIMList(PIM.EVENT_LIST,PIM.READ_WRITE);
				}
				
				for(int i = 0;i < tUpdateList.size();i++){
					CalendarSyncData update = (CalendarSyncData)tUpdateList.elementAt(i);				
					mCalendarSyncList.addElement(update);
					
					for(int idx = 0;idx < t_eventList.size();idx++){
						Event e = (Event)t_eventList.elementAt(idx);
						
						if(e.getString(Event.UID, 0).equals(update.getBBID())){
							update.exportData(e,tEvents);
							e.commit();
						}
					}
				}
			}
			
		}finally{
			if(tEvents != null){
				tEvents.close();
				tEvents  = null;
			}
		}
		
		
		if((tAddList != null || tDelList != null || tUpdateList != null || tUploadList != null) && tNeedList == null){
			readWriteSyncFile(false);
		}
		
		return tNeedList;
	}
	
	
	/**
	 * prepare the calendar and calculate the MD5
	 * @return
	 */
	private String prepareCalenderMD5(long _minTime){
				
		Vector tRemoveList = null;
		Vector tSortList = new Vector();
		
		for(int idx = 0;idx < mCalendarSyncList.size();idx++){

			CalendarSyncData syncData = (CalendarSyncData)mCalendarSyncList.elementAt(idx);
			
			if(syncData.getData().start < _minTime 
	    	&& syncData.getData().repeat_type.length() == 0){

				if(tRemoveList == null){
					tRemoveList = new Vector();
				}
				
				tRemoveList.addElement(syncData);
	    		continue;
	    	}
			
			// sort insert the calendar by the start time
			for(int i = 0;i < tSortList.size();i++){
				CalendarSyncData d = (CalendarSyncData)tSortList.elementAt(i);
				if(syncData.getLastMod() > d.getLastMod()){
					tSortList.insertElementAt(syncData, i);
					
					syncData = null;
					break;
				}
			}
			
			if(syncData != null){
				tSortList.addElement(syncData);				
			}
		}
				
		// remove the former time event
		if(tRemoveList != null){
			for(int i = 0;i < tRemoveList.size();i++){
				CalendarSyncData syncData = (CalendarSyncData)tRemoveList.elementAt(i);
				removeCalendarSyncData(syncData.getBBID());
			}			
		}
		
		StringBuffer sb = new StringBuffer();
		StringBuffer debug = new StringBuffer();
		
		// calculate the md5
		for(int i = 0;i < tSortList.size();i++){
			CalendarSyncData d = (CalendarSyncData)tSortList.elementAt(i);
			sb.append(d.getLastMod());
			
			debug.append(d.getLastMod()).append(":").append(d.getGID()).append("-").append(d.getData().summary).append("\n");
		}
		
		System.out.println(debug.toString());
		
		return md5(sb.toString());
	}
	
	
	
	/**
	 * request the url via POST
	 * @param _url
	 * @param _paramsName
	 * @param _paramsValue
	 * @param _gzip
	 * @return
	 * @throws Exception
	 */
	private static String requestPOSTHTTP(String _url,String[] _paramsName,String[] _paramsValue)throws Exception{
		
		if(_paramsName == null || _paramsValue == null || _paramsName.length != _paramsValue.length){
			throw new IllegalArgumentException("_paramsName == null || _paramsValue == null || _paramsName.length != _paramsValue.length");
		}
		
		StringBuffer tParam = new StringBuffer();
		for(int i = 0;i < _paramsName.length;i++){
			if(tParam.length() != 0){
				tParam.append('&');
			}
			
			tParam.append(_paramsName[i]).append('=').append(_paramsValue);
		}
		
		return new String(requestPOSTHTTP(_url,tParam.toString().getBytes("UTF-8"),false),"UTF-8");
		
	}
	
	/**
	 * post the http request directly by content
	 * @param _url
	 * @param _content
	 * @param _gzip
	 * @return
	 * @throws Exception
	 */
	private static byte[] requestPOSTHTTP(String _url,byte[] _content,boolean _gzip)throws Exception{
		
		byte[] tParamByte = _content;
		
		// Attempt to gzip the data
		if(_gzip){
			ByteArrayOutputStream zos = new ByteArrayOutputStream();
			try{
				GZIPOutputStream zo = new GZIPOutputStream(zos,6);
				try{
					zo.write(tParamByte);
				}finally{
					zo.close();
					zo = null;
				}
				
				byte[] tZipByte = zos.toByteArray();
				if(tZipByte.length < tParamByte.length){
					tParamByte = tZipByte;
				}else{
					_gzip = false;
				}
			}finally{
				zos.close();
				zos = null;
			}
		}
		
		HttpConnection conn = (HttpConnection)ConnectorHelper.open(_url,Connector.READ_WRITE,30000);
		try{
			
			conn.setRequestMethod(HttpConnection.POST);
			conn.setRequestProperty(HttpProtocolConstants.HEADER_CONTENT_LENGTH,String.valueOf(tParamByte.length));
//			
//			conn.setRequestProperty("Content-Type","application/x-www-form-urlencoded");			
//			conn.setRequestProperty("User-Agent","Profile/MIDP-2.0 Configuration/CLDC-1.0");
//			conn.setRequestProperty("Keep-Alive","60000");
//			conn.setRequestProperty("Connection","keep-alive");
						
			if(_gzip){
				conn.setRequestProperty("Content-Encoding","gzip");
			}
			
			OutputStream out = conn.openOutputStream();
			try{
				out.write(tParamByte);
				out.flush();
			}finally{
				out.close();
				out = null;
			}
			
			int rc = conn.getResponseCode();
		    if(rc != HttpConnection.HTTP_OK){
		    	throw new IOException("HTTP response code: " + rc);
		    }
		    
		    InputStream in = conn.openInputStream();
		    try{
		    	int length = (int)conn.getLength();
		    	int ch;
		    	byte[] result;
		    	
		    	if (length != -1){
		    		
		    		result = new byte[length];
		    		in.read(result);
		    		
		    	}else{
		    		
		    		ByteArrayOutputStream os = new ByteArrayOutputStream();
		    		try{

				        while ((ch = in.read()) != -1){
				        	os.write(ch);
				        }
				        
				        result = os.toByteArray();
				        
		    		}finally{
		    			os.close();
		    			os = null;
		    		}
			    }
		    	
		    	if(conn.getHeaderField("Content-Encoding") != null){
		    		
		    		ByteArrayInputStream gin = new ByteArrayInputStream(result);
					try{
						GZIPInputStream zi	= new GZIPInputStream(gin);
						try{
							ByteArrayOutputStream os = new ByteArrayOutputStream();
				    		try{

								while((ch = zi.read()) != -1){
									os.write(ch);
								}
								result = os.toByteArray();
								
				    		}finally{
				    			os.close();
				    			os = null;
				    		}
						}finally{
							zi.close();
						}
					}finally{
						gin.close();
					}
		        }
		    	
		    	return result;

		    }finally{
		    	in.close();
		    	in = null;
		    }

		}finally{
			conn.close();
			conn = null;
		}
	}

}