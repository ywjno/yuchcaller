/**
 *  Dear developer:
 *  
 *   If you want to modify this file of project and re-publish this please visit:
 *  
 *     http://code.google.com/p/yuchberry/wiki/Project_files_header
 *     
 *   to check your responsibility and my humble proposal. Thanks!
 *   
 *  -- 
 *  Yuchs' Developer    
 *  
 *  
 *  
 *  
 *  尊敬的开发者：
 *   
 *    如果你想要修改这个项目中的文件，同时重新发布项目程序，请访问一下：
 *    
 *      http://code.google.com/p/yuchberry/wiki/Project_files_header
 *      
 *    了解你的责任，还有我卑微的建议。 谢谢！
 *   
 *  -- 
 *  语盒开发者
 *  
 */
package com.yuchs.yuchcaller.sync;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.TimeZone;
import java.util.Vector;

import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;
import javax.microedition.pim.Contact;
import javax.microedition.pim.Event;
import javax.microedition.pim.EventList;
import javax.microedition.pim.PIM;
import javax.microedition.pim.PIMItem;
import javax.microedition.pim.PIMList;
import javax.microedition.pim.ToDo;

import net.rim.blackberry.api.pdap.BlackBerryPIMList;
import net.rim.blackberry.api.pdap.PIMListListener;

import com.yuchs.yuchcaller.YuchCaller;
import com.yuchs.yuchcaller.YuchCallerProp;
import com.yuchs.yuchcaller.sendReceive;
import com.yuchs.yuchcaller.sync.calendar.CalendarSyncData;

public abstract class AbsSync implements PIMListListener{
	
	public static final String[] fsm_syncTypeString =
	{
		"calendar",
		"contact",
		"task",
	};
	
	/**
	 * the version fo sync file
	 */
	private static final int SyncFileVersion = 0;
	
	/**
	 * sync data list
	 */
	protected Vector mSyncDataList = new Vector();
		
	//! sync main class
	final protected SyncMain	mSyncMain;
	
	/**
	 * whether is modified by programmely PIMListListener callback 
	 */
	private boolean mModifiedProgrammely = false;
	
	public AbsSync(SyncMain _syncMain){
		mSyncMain = _syncMain;
		
		try{

			// add the PIMList listener
			BlackBerryPIMList tEventList = (BlackBerryPIMList)PIM.getInstance().openPIMList(getSyncPIMListType(),PIM.READ_WRITE);
			
			try{
				tEventList.addListener(this);
			}finally{
				tEventList.close();
				tEventList = null;
			}
		}catch(Exception e){
			mSyncMain.m_mainApp.SetErrorString("CSC", e);
		}
		
		// read the calendar/contact/task information in bb database
		readBBSyncData();
		
		// read sync file (gID for bID)
		readWriteSyncFile(true);
	}
	
	/**
	 * find the sync data in list
	 * @param _bbID		bid of event
	 * @return
	 */
	protected AbsSyncData getSyncData(String _bbid){
		
		for(int i = 0;i < mSyncDataList.size();i++){
			AbsSyncData d = (AbsSyncData)mSyncDataList.elementAt(i); 
			if(d.getBBID().equals(_bbid)){
				return d;
			}
		}
		
		return null;
	}
	
	/**
	 * remove the sync data in main list
	 * @param _bbid
	 */
	protected void removeSyncData(String _bbid){
		
		for(int i = 0;i < mSyncDataList.size();i++){
			AbsSyncData d = (AbsSyncData)mSyncDataList.elementAt(i); 
			if(d.getBBID().equals(_bbid)){
				mSyncDataList.removeElementAt(i);
				break;
			}
		}
	}
	
	/**
	 * read the calendar information from bb calendar
	 */
	public void readBBSyncData(){
				
		try{
			
			PIMList tPIMList = (PIMList)PIM.getInstance().openPIMList(getSyncPIMListType(),PIM.READ_ONLY);
			try{

				Enumeration t_allItems = tPIMList.items();
				
				Vector t_eventList = new Vector();
			    if(t_allItems != null){
				    while(t_allItems.hasMoreElements()) {			    	
				    	t_eventList.addElement(t_allItems.nextElement());
				    }
			    }
			    
			    synchronized(mSyncDataList){
			    	mSyncDataList.removeAllElements();
				    for(int i = 0;i < t_eventList.size();i++){
				    	
				    	PIMItem item = (PIMItem)t_eventList.elementAt(i);
				    	
				    	AbsSyncData syncData = newSyncData();
				    	syncData.importData(item);
				    					    	
				    	mSyncDataList.addElement(syncData);
				    }	
			    }
			   		    
			    
			}finally{
				tPIMList.close();
				tPIMList = null;
			}
		    
		}catch(Exception e){
			reportError("Can not read PIMList ",e);
		}
	}

	/**
	 * return the sync main type:
	 * 
	 * PIM.EVENT_LIST
	 * PIM.CONTACT_LIST
	 * PIM.TODO_LIST
	 * 
	 * @return
	 */
	protected abstract int getSyncPIMListType();
	
	/**
	 * create a sync data
	 * @return
	 */
	protected abstract AbsSyncData newSyncData();
		
	/**
	 * add a PIMItem to bb by the abs sync data (CalendarSyncData/ContactSyncData/TaskSyncData)
	 * @param _data
	 */
	protected void addPIMItem(Vector _addList){
		
		if(_addList == null || _addList.size() <= 0){
			return;
		}
		
		try{
						
			EventList tEvents = (EventList)PIM.getInstance().openPIMList(PIM.EVENT_LIST,PIM.READ_WRITE);
			try{
				
				Enumeration t_allEvents = tEvents.items();
				Vector t_eventList =  new Vector();
				
			    if(t_allEvents != null){
				    while(t_allEvents.hasMoreElements()) {			    	
				    	t_eventList.addElement(t_allEvents.nextElement());
				    }
			    }
			    
			    // add the event to bb system
				//
				for(int i = 0;i < _addList.size();i++){
					CalendarSyncData d = (CalendarSyncData)_addList.elementAt(i);
					
					Event e = tEvents.createEvent();
					d.exportData(e);
					
					mModifiedProgrammely = true;
					e.commit();
					mModifiedProgrammely = false;
					
					d.setBBID(AbsSyncData.getStringField(e, Event.UID));
					
					// added to main list
					mSyncDataList.addElement(d);
				}
			    
			}finally{
				tEvents.close();
				tEvents = null;
			}
			
		}catch(Exception e){
			mSyncMain.m_mainApp.SetErrorString("API", e);
		}
		
	}
	
	/**
	 * remove PIMItem from a del list
	 * @param _delList
	 */
	protected void deletePIMItem(Vector _delList){
		
		if(_delList == null || _delList.size() <= 0){
			return;
		}
		
		try{
			EventList tEvents = (EventList)PIM.getInstance().openPIMList(PIM.EVENT_LIST,PIM.READ_WRITE);
			try{
				
				Enumeration t_allEvents = tEvents.items();
				Vector t_eventList =  new Vector();
				
			    if(t_allEvents != null){
				    while(t_allEvents.hasMoreElements()) {			    	
				    	t_eventList.addElement(t_allEvents.nextElement());
				    }
			    }
			    
				for(int i = 0;i < _delList.size();i++){
					String bid = (String)_delList.elementAt(i);
					
					CalendarSyncData d = (CalendarSyncData)getSyncData(bid);
					if(d != null){
						
						if(d.getLastMod() != -1){
							
							// this event is NOT been deleted by client
							//
							for(int idx = 0;idx < t_eventList.size();idx++){
								
								Event e = (Event)t_eventList.elementAt(idx);
																
								if(d.getBBID().equals(AbsSyncData.getStringField(e, Event.UID))){
									
									mModifiedProgrammely = true;
									tEvents.removeEvent(e);
									mModifiedProgrammely = false;
									
									t_eventList.removeElement(e);
									
									break;
								}
							}
						}
						
						removeSyncData(d.getBBID());
					}
				}
			    
			}finally{
				tEvents.close();
				tEvents = null;
			}
			
		}catch(Exception e){
			mSyncMain.m_mainApp.SetErrorString("DPI", e);
		}
		
	}
	
	/**
	 * update the PIMItem from a update list(AbsSyncData list)
	 * @param _updateList
	 */
	protected void updatePIMItem(Vector _updateList){
		
		if(_updateList == null || _updateList.size() <= 0){
			return;
		}
		
		try{
			EventList tEvents = (EventList)PIM.getInstance().openPIMList(PIM.EVENT_LIST,PIM.READ_WRITE);
			try{
				
				Enumeration t_allEvents = tEvents.items();
				Vector t_eventList =  new Vector();
				
			    if(t_allEvents != null){
				    while(t_allEvents.hasMoreElements()) {			    	
				    	t_eventList.addElement(t_allEvents.nextElement());
				    }
			    }
			    
			    for(int i = 0;i < _updateList.size();i++){
					CalendarSyncData update = (CalendarSyncData)_updateList.elementAt(i);
					
					// remove sync data first
					removeSyncData(update.getBBID());
					
					// add data again
					mSyncDataList.addElement(update);
					
					for(int idx = 0;idx < t_eventList.size();idx++){
						Event e = (Event)t_eventList.elementAt(idx);
						
						if(update.getBBID().equals(AbsSyncData.getStringField(e, Event.UID))){
							update.exportData(e);
							
							mModifiedProgrammely = true;
							e.commit();
							mModifiedProgrammely = false;
						}
					}
				}
			    
			}finally{
				tEvents.close();
				tEvents = null;
			}
			
		}catch(Exception e){
			mSyncMain.m_mainApp.SetErrorString("UPI", e);
		}
	}

	
	
	/**
	 * start sync, main proccess function
	 */
	public void startSync(){
						
		// just call sync request
		syncRequest();
	}
	
	/**
	 * write the account information to a OutputStream
	 * @param os
	 * @param type
	 * @param md5		sync data md5
	 * @param diffType 	of sync
	 * @throws Exception
	 */
	private void writeAccountInfo(OutputStream os,String md5,long minSyncTime,int diffType)throws Exception{
		
		YuchCallerProp tProp = mSyncMain.m_mainApp.getProperties();
		
		// write the version
		sendReceive.WriteShort(os,(short)0);
		sendReceive.WriteString(os, fsm_syncTypeString[getReportLabel()]);
		
		// send the min time for sync
		sendReceive.WriteLong(os, minSyncTime);
		
		sendReceive.WriteString(os,tProp.getYuchRefreshToken());
		sendReceive.WriteString(os,tProp.getYuchAccessToken());
		
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
	protected void syncRequest(){
		
		try{

			reportInfo("Request sync...");
			
			ByteArrayOutputStream os = new ByteArrayOutputStream();
			try{
				
				long tSyncMinTime = System.currentTimeMillis() - mSyncMain.m_mainApp.getProperties().getSyncFormerDays() * 24 * 3600000L;
				
				String md5 = prepareSyncMD5(tSyncMinTime);
				
				writeAccountInfo(os,md5,tSyncMinTime,0);
				
				//String url = "http://192.168.10.7:8888" + YuchCaller.getHTTPAppendString();
				//String url = "http://192.168.100.116:8888" + YuchCaller.getHTTPAppendString();
				String url = "http://sync.yuchs.com:6029" + YuchCaller.getHTTPAppendString();
				
				String tResultStr = new String(SyncMain.requestPOSTHTTP(url,os.toByteArray(),true),"UTF-8");
										
				if(tResultStr.equals("succ")){
					
					// successfully!
					reportInfo("sync without any changed successfully!");
					
				}else if(tResultStr.equals("diff")){
					
					// write the diff sign
					InputStream diffIn = new ByteArrayInputStream(SyncMain.requestPOSTHTTP(url, outputDiffList(md5,tSyncMinTime) ,true));
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
				
				reportError(null);
				
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
			
			writeAccountInfo(os,md5,minSyncTime,1);
			
			sendReceive.WriteInt(os, mSyncDataList.size());
			
			for(int idx = 0;idx < mSyncDataList.size();idx++){

				AbsSyncData syncData = (AbsSyncData)mSyncDataList.elementAt(idx);
				syncData.output(os,syncData.getGID() == null || syncData.getGID().length()  == 0);
			}			
			
			return os.toByteArray();
		}finally{
			os.close();
			os = null;
		}
	}

	/**
	 * read or write sync data file
	 * @param _readOrWrite
	 */
	protected void readWriteSyncFile(boolean _readOrWrite){
		
		String tFilename = YuchCallerProp.fsm_rootPath_back + "YuchCaller/" + fsm_syncTypeString[getReportLabel()] + ".data"; 
		
		mSyncMain.m_mainApp.getProperties().preWriteReadIni(_readOrWrite, tFilename);
		
		try{
			FileConnection fc = (FileConnection) Connector.open(tFilename,Connector.READ_WRITE);
			try{
				if(_readOrWrite){
					
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
													
							AbsSyncData syncData = getSyncData(bid);
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
					
					// delete the calendar event which has been removed by client
					//
					for(int idx = 0;idx < mSyncDataList.size();idx++){
						AbsSyncData syncData = (AbsSyncData)mSyncDataList.elementAt(idx);
						if(syncData.getLastMod() == -1){
							mSyncDataList.removeElementAt(idx);
							idx--;
						}
					}
					
					OutputStream os = fc.openOutputStream();
					try{
						os.write(SyncFileVersion);
						sendReceive.WriteInt(os, mSyncDataList.size());
						
						for(int idx = 0;idx < mSyncDataList.size();idx++){

							AbsSyncData syncData = (AbsSyncData)mSyncDataList.elementAt(idx);
							
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
			reportError("Can not read " + tFilename,e);
		}
		
		mSyncMain.m_mainApp.getProperties().postWriteReadIni(tFilename);
	}
		
	/**
	 * prepare the calendar/contact/task and calculate the MD5
	 * @return
	 */
	protected String prepareSyncMD5(long _minTime){
				
		Vector tRemoveList = null;
		Vector tSortList = new Vector();
		
		for(int idx = 0;idx < mSyncDataList.size();idx++){

			AbsSyncData syncData = (AbsSyncData)mSyncDataList.elementAt(idx);
			
			if(!syncData.needCalculateMD5(_minTime)){

				if(tRemoveList == null){
					tRemoveList = new Vector();
				}
				
				tRemoveList.addElement(syncData);
	    		continue;
	    	}
			
			// sort insert the calendar by the start time
			for(int i = 0;i < tSortList.size();i++){
				AbsSyncData d = (AbsSyncData)tSortList.elementAt(i);
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
				AbsSyncData syncData = (AbsSyncData)tRemoveList.elementAt(i);
				removeSyncData(syncData.getBBID());
			}			
		}
		
		StringBuffer sb = new StringBuffer();
		
		// calculate the md5
		for(int i = 0;i < tSortList.size();i++){
			AbsSyncData d = (AbsSyncData)tSortList.elementAt(i);
			sb.append(d.getLastMod());
		}
		
		return SyncMain.md5(sb.toString());
	}
	
	/**
	 * process the different list
	 * @param in
	 * @return need list
	 */
	protected Vector processDiffList(InputStream in)throws Exception{
		
		Vector tAddList		= null;
		Vector tDelList	 	= null;
		Vector tUploadList	 = null;
		Vector tUpdateList	= null;
		Vector tNeedList	= null;
		
		// get the add list
		int num = sendReceive.ReadInt(in);
		for(int i = 0;i < num;i++){
			AbsSyncData e = newSyncData();
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
			AbsSyncData e = newSyncData();
			e.input(in);
			
			if(tUpdateList == null){
				tUpdateList = new Vector();
			}
			tUpdateList.addElement(e);
		}
		
		// get the upload list
		num = sendReceive.ReadInt(in);
		for(int i = 0;i < num;i++){
			AbsSyncData e = newSyncData();
			
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
		
		// add PIMItem to BB system
		addPIMItem(tAddList);
		
		// delete PIMItem from BB system
		deletePIMItem(tDelList);
		
		// update PIMItem in BB system
		updatePIMItem(tUpdateList);
		
		if(tUploadList != null){
			// upload list (refresh current bb calendar's GID)
			//
			for(int i = 0;i < tUploadList.size();i++){
				AbsSyncData uploaded = (AbsSyncData)tUploadList.elementAt(i);
				
				AbsSyncData d = (AbsSyncData)getSyncData(uploaded.getBBID());
				if(d != null){
					d.setGID(uploaded.getGID());
					d.setLastMod(uploaded.getLastMod());
				}
			}
		}
		
		if((tAddList != null || tDelList != null || tUpdateList != null || tUploadList != null) && tNeedList == null){
			readWriteSyncFile(false);
		}
		
		return tNeedList;
	}
	
	/**
	 * send need list and process result
	 * @param needList
	 * @param _url
	 * @throws Exception
	 */
	protected void sendNeedList(Vector needList,String _url,String _md5,long minSyncTime)throws Exception{
		
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		try{
			
			writeAccountInfo(os,_md5,minSyncTime,2);
						
			// write the sync data
			sendReceive.WriteInt(os,needList.size());
			
			for(int i = 0;i < needList.size();i++){
				AbsSyncData d = (AbsSyncData)getSyncData(needList.elementAt(i).toString());
				d.output(os, true);				
			}
			
			InputStream in = new ByteArrayInputStream(SyncMain.requestPOSTHTTP(_url, os.toByteArray(), true));
			try{
				
				int num = sendReceive.ReadInt(in);
				for(int i = 0;i < num;i++){
					String bid		= sendReceive.ReadString(in);
					long modTime	= sendReceive.ReadLong(in);
					
					AbsSyncData d = (AbsSyncData)getSyncData(bid);
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
	
	//{{ PIMListListener
	public void itemAdded(PIMItem item) {
		
		if(mModifiedProgrammely){
			mModifiedProgrammely = false;
			return;
		}
		
		try{
			
			AbsSyncData syncData = newSyncData();
	    	syncData.importData((Event)item);
	    				    	
	    	mSyncDataList.addElement(syncData);
		
		}catch(Exception e){
			mSyncMain.m_mainApp.SetErrorString("CSIA", e);
		}
	}

	public void itemRemoved(PIMItem item) {
		
		if(mModifiedProgrammely){
			mModifiedProgrammely = false;
			return;
		}
		
		if(item != null){
			
			String bid = AbsSyncData.getStringField(item, getUIDId());
			
			synchronized(mSyncDataList){
				for(int i = 0;i < mSyncDataList.size();i++){
					AbsSyncData d = (AbsSyncData)mSyncDataList.elementAt(i);
					if(d.getBBID().equals(bid)){
						
						if(d.getGID() == null){
							// remove directly
							mSyncDataList.removeElementAt(i);
						}else{
							// mark delete to wait sync to delete server's event
							d.setLastMod(-1);
						}
						break;
					}
				}
			}
		}
	}

	public void itemUpdated(PIMItem oldItem, PIMItem newItem) {
		
		if(mModifiedProgrammely){
			mModifiedProgrammely = false;
			return;
		}
		
		if(oldItem != null && newItem != null){
			
			try{
				String bid = AbsSyncData.getStringField(oldItem, getUIDId());
				
				synchronized(mSyncDataList){
					for(int i = 0;i < mSyncDataList.size();i++){
						AbsSyncData d = (AbsSyncData)mSyncDataList.elementAt(i);
						if(d.getBBID().equals(bid)){
							
							d.importData(newItem);
							d.setLastMod(System.currentTimeMillis());
															
							readWriteSyncFile(false);
							
							break;
						}
					}
				}
				
			}catch(Exception e){
				mSyncMain.m_mainApp.SetErrorString("CSIA", e);
			}
		}
	}
	//}}
	
	/**
	 * get the UID Id
	 * @return
	 */
	private int getUIDId(){
		switch(getSyncPIMListType()){
		case PIM.EVENT_LIST:
			return Event.UID;
		case PIM.CONTACT_LIST:
			return Contact.UID;
		default:
			return ToDo.UID;
		}
	}
	
	/**
	 * get the 0-base index of report by SyncPIMListType
	 * @return
	 */
	private final int getReportLabel(){
		switch(getSyncPIMListType()){
		case PIM.EVENT_LIST:
			return 0;
		case PIM.CONTACT_LIST:
			return 1;
		default:
			return 2;
		}
	}
	/**
	 * report error to sync main
	 * @param error
	 */
	protected final void reportError(String error){
		mSyncMain.reportError(error, getReportLabel());
	}
	
	protected final void reportError(String label,Exception e){
		mSyncMain.reportError(label,e,getReportLabel());
	}
	
	/**
	 * report the information
	 * @param info
	 */
	protected final void reportInfo(String info){
		mSyncMain.reportInfo(info,getReportLabel());
	}
	
}