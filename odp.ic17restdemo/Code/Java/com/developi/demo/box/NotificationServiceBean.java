package com.developi.demo.box;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.ParseException;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import lotus.domino.Database;
import lotus.domino.DateTime;
import lotus.domino.Document;
import lotus.domino.NotesException;
import lotus.domino.RichTextItem;
import lotus.domino.Session;
import lotus.domino.View;

import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.time.FastDateFormat;

import com.developi.demo.Utils;
import com.ibm.commons.util.io.json.JsonException;
import com.ibm.commons.util.io.json.JsonJavaArray;
import com.ibm.commons.util.io.json.JsonJavaFactory;
import com.ibm.commons.util.io.json.JsonJavaObject;
import com.ibm.commons.util.io.json.JsonParser;
import com.ibm.domino.services.ResponseCode;
import com.ibm.domino.services.ServiceException;
import com.ibm.domino.services.rest.RestServiceEngine;
import com.ibm.xsp.extlib.component.rest.CustomService;
import com.ibm.xsp.extlib.component.rest.CustomServiceBean;
import com.ibm.xsp.extlib.util.ExtLibUtil;

public class NotificationServiceBean extends CustomServiceBean {

	@SuppressWarnings("unchecked")
	@Override
	public void renderService(CustomService service, RestServiceEngine engine) throws ServiceException {

		HttpServletRequest request = engine.getHttpRequest();
		HttpServletResponse response = engine.getHttpResponse();

		Map<String, String> headers = new HashMap<String, String>();

		Enumeration headerNames = request.getHeaderNames();
		while (headerNames.hasMoreElements()) {
			String key = (String) headerNames.nextElement();
			String value = request.getHeader(key);
			headers.put(key, value);
		}

		StringBuffer sb = new StringBuffer();

		BufferedReader reader;
		try {
			reader = request.getReader();
			String line;
			while ((line = reader.readLine()) != null) {
				sb.append(line);
			}
		} catch (IOException e) {
			e.printStackTrace();
			throw new ServiceException(e, ResponseCode.INTERNAL_ERROR);
		}

		String data = sb.toString();

		try {
			process(headers, data);
			response.setStatus(200);
			response.setContentType("text/plain");
			PrintWriter out = response.getWriter();
			out.println("DONE");
			out.close();
			out.flush();
		} catch (Exception e) {
			e.printStackTrace();
			throw new ServiceException(e, ResponseCode.INTERNAL_ERROR);
		}

	}

	private void process(Map<String, String> headers, String data) throws NotesException, JsonException {
		Session sessionAsSigner = ExtLibUtil.getCurrentSessionAsSigner();
		Database dbCurrent = null;
		Document nDoc = null;

		try {
			dbCurrent = sessionAsSigner.getCurrentDatabase();

			Vector<String> headerValue = new Vector<String>();
			for (Entry<String, String> entry : headers.entrySet()) {
				headerValue.add(entry.getKey() + "=" + entry.getValue());
			}

			nDoc = dbCurrent.createDocument();
			nDoc.replaceItemValue("Form", "notification");
			nDoc.replaceItemValue("Headers", headerValue);
			nDoc.replaceItemValue("Payload", data);

			boolean valid = extractData(dbCurrent, data);

			// Saving for debugging
			nDoc.replaceItemValue("Valid", valid ? "1" : "");
			nDoc.save();
			
		} finally {
			Utils.recycleObjects(nDoc);
		}

	}

	private boolean extractData(Database dbCurrent, String data) throws NotesException, JsonException {
		JsonJavaFactory jsonFactory = JsonJavaFactory.instanceEx;

		if (JsonParser.isJson(data)) {
			JsonJavaObject json = (JsonJavaObject) JsonParser.fromJson(jsonFactory, data);
			JsonJavaObject source = json.getAsObject("source");

			String type = json.getAsString("trigger");

			boolean valid = true;

			if ("FILE.UPLOADED".equals(type)) {
				extractFileUpload(dbCurrent, source);
			} else if ("COMMENT.CREATED".equals(type)) {
				extractComment(dbCurrent, source);
			} else if ("FILE.TRASHED".equals(type)) {
				extractDelete(dbCurrent, source);
			} else {
				valid = false;
			}

			return valid;

		}

		return false;
	}

	private void extractFileUpload(Database dbCurrent, JsonJavaObject source) throws NotesException {
		Document doc = null;

		try {
			doc = dbCurrent.createDocument();
			Session session = dbCurrent.getParent();

			doc.replaceItemValue("Form", "boxfile");
			doc.replaceItemValue("Status", "ACTIVE");

			JsonJavaObject creator = source.getAsObject("created_by");
			doc.replaceItemValue("CreatedByName", creator.getAsString("name"));
			doc.replaceItemValue("CreatedByLogin", creator.getAsString("login"));
			doc.replaceItemValue("CreatedById", creator.getAsString("id"));

			String createdAt = source.getAsString("created_at");
			try {
				Date createdAtDate = DateFormatUtils.ISO_DATETIME_TIME_ZONE_FORMAT.parse(createdAt);
				DateTime createdAtDateTime = session.createDateTime(createdAtDate);
				doc.replaceItemValue("CreatedAt", createdAtDateTime);
			} catch (ParseException e) {
				e.printStackTrace();
			}

			doc.replaceItemValue("FileId", source.getAsString("id"));
			doc.replaceItemValue("FileName", source.getAsString("name"));

			JsonJavaObject parent = source.getAsObject("parent");
			if (parent != null) {
				doc.replaceItemValue("ParentFolderId", parent.getAsString("id"));
				doc.replaceItemValue("ParentFolderName", parent.getAsString("name"));
			}

			JsonJavaArray pathInfo = source.getAsObject("path_collection").getAsArray("entries");
			Vector<String> pathList = new Vector<String>();

			for (int i = 0; i < pathInfo.length(); i++) {
				JsonJavaObject path = pathInfo.getAsObject(i);
				pathList.add(path.getAsString("name"));
			}

			doc.replaceItemValue("ParentFolderPath", pathList);

			doc.save();
			
			notifyForNewFile(dbCurrent);
			
		} finally {
			Utils.recycleObjects(doc);
		}
	}

	private void notifyForNewFile(Database dbCurrent) throws NotesException {
		Document doc = null;
		RichTextItem body = null;
		View configView = null;
		Document configDoc = null;
		
		try {
			configView = dbCurrent.getView("config");
			configDoc = configView.getFirstDocument();
			
			String userToNotify = configDoc.getItemValueString("NotifyUser");

			doc = dbCurrent.createDocument();
			
			doc.replaceItemValue("Form", "Memo");
			doc.replaceItemValue("Subject", "A new file uploaded for your attention!");
			doc.replaceItemValue("SendTo", userToNotify);
			
			body = doc.createRichTextItem("Body");
			
			body.appendText("Hello,");
			body.addNewLine(2);
			body.appendText("There is a new file uploaded to the Box folder for Compliance analysis. Please check the file...");
			body.addNewLine(2);
			body.appendText("This is just a demo notification, don't expect too much :)");
			
			doc.send();
			
		} finally { 
			Utils.recycleObjects(doc, body, configView, configDoc);
		}
		
	}

	@SuppressWarnings("unchecked")
	private void extractComment(Database dbCurrent, JsonJavaObject source) throws NotesException {
		View fileView = null;
		Document fileDoc = null;

		try {
			fileView = dbCurrent.getView("(boxfilesById)");

			JsonJavaObject item = source.getAsObject("item");
			if (item != null) {
				String fileId = item.getAsString("id");

				fileDoc = fileView.getDocumentByKey(fileId, true);

				if (fileDoc != null) {
					Vector<String> comments = fileDoc.getItemValue("Comments");
					String message = source.getAsString("message");
					Date date = null;
					try {
						date = DateFormatUtils.ISO_DATETIME_TIME_ZONE_FORMAT.parse(source.getAsString("created_at"));
					} catch (ParseException e) {
						date = new Date();
					}

					String dateStr = FastDateFormat.getDateTimeInstance(FastDateFormat.SHORT, FastDateFormat.SHORT).format(date);
					String sender = source.getAsObject("created_by").getAsString("name");

					String line = dateStr + "___" + sender + "___" + message;
					comments.add(line);

					fileDoc.replaceItemValue("Comments", comments);
					fileDoc.save();
				}

			}

		} finally {
			Utils.recycleObjects(fileDoc, fileView);
		}
	}

	private void extractDelete(Database dbCurrent, JsonJavaObject source) throws NotesException {
		View fileView = null;
		Document fileDoc = null;

		try {
			fileView = dbCurrent.getView("(boxfilesById)");

			String fileId = source.getAsString("id");

			fileDoc = fileView.getDocumentByKey(fileId, true);

			if (fileDoc != null) {
				fileDoc.replaceItemValue("Status", "DELETED");
				fileDoc.save();
			}

		} finally {
			Utils.recycleObjects(fileDoc, fileView);
		}
	}
}
