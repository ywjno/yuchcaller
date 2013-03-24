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
package com.yuchs.yuchcaller.sync.svr.contact;

import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.Vector;

import com.google.gdata.client.Query;
import com.google.gdata.client.contacts.ContactsService;
import com.google.gdata.data.contacts.ContactEntry;
import com.google.gdata.data.contacts.ContactFeed;
import com.yuchs.yuchcaller.sync.svr.GoogleAPISync;
import com.yuchs.yuchcaller.sync.svr.GoogleAPISyncData;
import com.yuchs.yuchcaller.sync.svr.Logger;

public class ContactSync extends GoogleAPISync {
	
	private ContactsService myService = new ContactsService("YuchCaller");
	
	public ContactSync(InputStream in, Logger logger) throws Exception {
		super(in, logger);

		// set the credentials
		myService.setOAuth2Credentials(mGoogleCredential);
	}
	
	/**
	 * get the URL by google id
	 * @param gid
	 * @return
	 */
	private URL getContactURL(String gid)throws Exception{
		return new URL("https://www.google.com/m8/feeds/contacts/default/full/" + gid);
	}


	/**
	 * read the contact data
	 */
	@Override
	public void readSvrGoogleData() throws Exception {
		
		if(fetchFormerEvent()){
			return ;
		}
		
		if(mSvrSyncDataList == null){
			mSvrSyncDataList = new Vector<Object>();
		}
		
		URL feedUrl = getContactURL("");
		Query myQuery = new Query(feedUrl);
		myQuery.setMaxResults(999999);
		myQuery.setStringCustomParameter("orderby","lastmodified");
		ContactFeed resultFeed;
		
		try{
			resultFeed = myService.query(myQuery, ContactFeed.class);
		}catch(NullPointerException e){
			if(e.getMessage().startsWith("No authentication header information")){
				mGoogleCredential.refreshToken();
				resultFeed = myService.query(myQuery, ContactFeed.class);
			}else{
				throw e;
			}
		}
		
		StringBuffer sb = new StringBuffer();
		
		if(resultFeed != null && resultFeed.getEntries() != null){
			
			List<ContactEntry> tContactList = resultFeed.getEntries();
			for(int i = tContactList.size() -1 ;i >= 0;i--){
				
				ContactEntry e = tContactList.get(i);
				
				if(e.hasName()){
					// must has name
					//
					mSvrSyncDataList.add(e);
					sb.append(e.getUpdated().getValue());
					
//					System.out.println("retrieve: " + e.getName().getFullName());				
//					if(e.getUpdated().getValue() > 1363744517000L){
//						System.out.println("delete " + e.getName().getFullName());
//						e.delete();						
//					}
				}
			}
		}	
		
		mAllSvrSyncDataMD5 = getMD5(sb.toString());
		storeFormerEvent();
		
		mLogger.LogOut(mYuchAcc + " Load Contact Number:" + mSvrSyncDataList.size());
	}
	
	
	@Override
	protected String getGoogleDataId(Object o) {
		return ContactSyncData.getContactEntryId(o);
	}

	@Override
	protected long getGoogleDataLastMod(Object o) {
		ContactEntry contact = (ContactEntry)o;
		return contact.getUpdated().getValue();
	}

	@Override
	protected boolean isFristSyncSameData(Object o, GoogleAPISyncData g)throws Exception {
		
		ContactSyncData		cmp = new ContactSyncData();
		cmp.importGoogleData((ContactEntry)o,mTimeZoneID);
		
		return cmp.equals(g);
	}	

	@Override
	protected GoogleAPISyncData newSyncData() {
		return new ContactSyncData();
	}

	@Override
	protected void deleteGoogleData(GoogleAPISyncData g) throws Exception {
		
		ContactEntry contact;
		
		// delete contact
		try{
			contact = myService.getEntry(getContactURL(g.getGID()), ContactEntry.class);
		}catch(NullPointerException e){
			if(e.getMessage().startsWith("No authentication header information")){
				
				mGoogleCredential.refreshToken();
				contact = myService.getEntry(getContactURL(g.getGID()), ContactEntry.class);
				
			}else{
				throw e;
			}
		}
		
		if(contact != null){
			
			contact.delete();
			
			// ouput debug info
			String tDebugInfo = mYuchAcc + " deleteContact:" + g.getBBID();
			if(g.getAPIData() != null){
				ContactData cd = (ContactData)g.getAPIData();
				tDebugInfo += " " + cd.names[ContactData.NAME_GIVEN] + cd.names[ContactData.NAME_FAMILY];
			}
			mLogger.LogOut(tDebugInfo);
		}		
	}

	@Override
	protected Object updateGoogleData(Object o,GoogleAPISyncData g) throws Exception {
		ContactEntry contact = (ContactEntry)o;
		if(contact != null){
			
			g.exportGoogleData(contact, mTimeZoneID);
			
			URL editUrl = new URL(contact.getEditLink().getHref());
			try{
				
				contact = myService.update(editUrl, contact);
				
			}catch(NullPointerException e){
				if(e.getMessage().startsWith("No authentication header information")){
					
					mGoogleCredential.refreshToken();
					
					contact = myService.update(editUrl, contact);
					
				}else{
					throw e;
				}
			}
			
			
			g.setGID(ContactSyncData.getContactEntryId(contact));
			g.setLastMod(contact.getUpdated().getValue());
			
			// ouput debug info
			String tDebugInfo = mYuchAcc + " updateContact:" + g.getBBID();
			if(g.getAPIData() != null){
				ContactData cd = (ContactData)g.getAPIData();
				tDebugInfo += " " + cd.names[ContactData.NAME_GIVEN] + cd.names[ContactData.NAME_FAMILY];
			}
			mLogger.LogOut(tDebugInfo);
		}
		
		return contact;
	}

	@Override
	protected Object uploadGoogleData(GoogleAPISyncData g) throws Exception {
		
		ContactEntry contact = new ContactEntry();
		g.exportGoogleData(contact, mTimeZoneID);

		try{
			
			contact = myService.insert(getContactURL(""), contact);
			
		}catch(NullPointerException e){
			if(e.getMessage().startsWith("No authentication header information")){
				
				mGoogleCredential.refreshToken();
				
				contact = myService.insert(getContactURL(""), contact);
				
			}else{
				throw e;
			}
		}
		
		// ouput debug info
		String tDebugInfo = mYuchAcc + " uploadContact:" + g.getBBID();
		if(g.getAPIData() != null){
			ContactData cd = (ContactData)g.getAPIData();
			tDebugInfo += " " + cd.names[ContactData.NAME_GIVEN] + cd.names[ContactData.NAME_FAMILY];
		}
		mLogger.LogOut(tDebugInfo);
		
		return contact;
	}

}
