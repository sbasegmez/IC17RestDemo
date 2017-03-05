package com.developi.demo.box;

import java.io.Serializable;
import java.util.Date;
import java.util.Vector;

import lotus.domino.Database;
import lotus.domino.DateTime;
import lotus.domino.Document;
import lotus.domino.NotesException;

import org.apache.commons.lang3.StringUtils;

import com.developi.demo.Utils;
import com.ibm.xsp.extlib.util.ExtLibUtil;

public class Token implements Serializable {

	private static final long serialVersionUID = 1L;

	private String noteId;

	private String owner;
	private String accessToken;
	private long expireTime;
	private String restrictedTo;
	private String tokenType;
	private String refreshToken;

	public String getNoteId() {
		return noteId;
	}

	public void setNoteId(String noteId) {
		this.noteId = noteId;
	}

	public String getOwner() {
		return owner;
	}

	public void setOwner(String owner) {
		this.owner = owner;
	}

	public String getAccessToken() {
		return accessToken;
	}

	public void setAccessToken(String accessToken) {
		this.accessToken = accessToken;
	}

	public long getExpireTime() {
		return expireTime;
	}

	public void setExpireTime(long expireTime) {
		this.expireTime = expireTime;
	}

	public String getRestrictedTo() {
		return restrictedTo;
	}

	public void setRestrictedTo(String restrictedTo) {
		this.restrictedTo = restrictedTo;
	}

	public String getTokenType() {
		return tokenType;
	}

	public void setTokenType(String tokenType) {
		this.tokenType = tokenType;
	}

	public String getRefreshToken() {
		return refreshToken;
	}

	public void setRefreshToken(String refreshToken) {
		this.refreshToken = refreshToken;
	}

	public boolean isExpired() {
		return System.currentTimeMillis()>expireTime;
	}

	public static Token readToken(Document tokenDoc) throws NotesException {
		Token token = new Token();
		
		token.setNoteId(tokenDoc.getNoteID());
		token.setOwner(tokenDoc.getItemValueString("Owner"));
		token.setAccessToken(tokenDoc.getItemValueString("AccessToken"));
		token.setRestrictedTo(tokenDoc.getItemValueString("RestrictedTo"));
		token.setTokenType(tokenDoc.getItemValueString("TokenType"));
		token.setRefreshToken(tokenDoc.getItemValueString("RefreshToken"));

		Vector<?> dtList = tokenDoc.getItemValueDateTimeArray("ExpireDateTime");
		if(! dtList.isEmpty() && dtList.get(0) instanceof DateTime) {
			DateTime expireDateTime = (DateTime) dtList.get(0);
			token.setExpireTime(expireDateTime.toJavaDate().getTime());
			Utils.recycleObjects(expireDateTime);
		} else {
			token.setExpireTime(0);
		}
						
		return token;
		
	}

	public void save() {
		Database dbCurrent = ExtLibUtil.getCurrentDatabase();
		Document doc = null;
		DateTime dt = null;
		
		try {
			if(StringUtils.isEmpty(noteId)) {
				// New Document
				doc = dbCurrent.createDocument();
				doc.replaceItemValue("Form", "token");
				noteId = doc.getNoteID();
				owner = dbCurrent.getParent().getEffectiveUserName();
			} else {
				doc = dbCurrent.getDocumentByID(noteId);
			}
			
			doc.replaceItemValue("Owner", getOwner());
			doc.replaceItemValue("AccessToken", getAccessToken());
			doc.replaceItemValue("RestrictedTo", getRestrictedTo());
			doc.replaceItemValue("TokenType", getTokenType());
			doc.replaceItemValue("RefreshToken", getRefreshToken());
			
			dt = dbCurrent.getParent().createDateTime(new Date(getExpireTime()));
			doc.replaceItemValue("ExpireDateTime", dt);			
			
			doc.save();
			
		} catch (NotesException e) {
			e.printStackTrace();
		} finally {
			Utils.recycleObjects(doc, dt);
		}
	}

}
