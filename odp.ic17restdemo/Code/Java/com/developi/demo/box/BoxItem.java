package com.developi.demo.box;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.ibm.commons.util.io.json.JsonGenerator;
import com.ibm.commons.util.io.json.JsonJavaArray;
import com.ibm.commons.util.io.json.JsonJavaObject;

public class BoxItem implements Serializable {

	private static final long serialVersionUID = 1L;

	public static enum ItemType {
		FILE, FOLDER
	};

	public static final Map<String, String> ICONMAP=new HashMap<String, String>() {
		private static final long serialVersionUID = 1L;

		{
			put("text", "ct-txt");
			put("txt", "ct-txt");
			put("pdf", "ct-pdf");
			put("image", "ct-image");
			put("png", "ct-image");
			put("jpg", "ct-image");
			put("jpeg", "ct-image");
			put("doc", "ct-doc");
			put("xls", "ct-xls");
			put("ppt", "ct-ppt");
			put("docx", "ct-doc");
			put("xlsx", "ct-xls");
			put("pptx", "ct-ppt");
			put("odt", "ct-doc");
			put("ods", "ct-xls");
			put("odp", "ct-ppt");
			put("zip", "ct-zip");
		}
	};
	
	private ItemType type;

	private String id;
	private String sequenceId;
	private String etag;
	private String name;
	private String description;
	private long size;
	private Date createdAt; // created_at
	private String url;

	private List<BoxItem> pathCollection;

	public BoxItem() {
	}

	public BoxItem(JsonJavaObject data) {
		this();

		if (data != null) {

			this.type = "folder".equals(data.getAsString("type")) ? ItemType.FOLDER : ItemType.FILE;

			this.id = data.getAsString("id");
			this.sequenceId = data.getAsString("sequence-id");
			this.etag = data.getAsString("etag");
			this.name = data.getAsString("name");
			this.description = data.getAsString("description");
			this.size = data.getAsLong("size");

			try {
				this.createdAt = JsonGenerator.stringToDate(data.getAsString("created-at"));
			} catch (Exception e) {
			}

			pathCollection = new ArrayList<BoxItem>();

			JsonJavaObject pathObj = data.getJsonObject("path_collection");
			if (pathObj != null && pathObj.containsKey("entries")) {
				JsonJavaArray entries = pathObj.getAsArray("entries");
				for (Object path : entries) {
					if (path instanceof JsonJavaObject) {
						pathCollection.add(new BoxItem((JsonJavaObject) path));
					}
				}
			}

			JsonJavaObject sLink = data.getJsonObject("shared_link");
			if (sLink != null)
				this.url = sLink.getAsString("url");
		}

	}

	public String getIcon() {
		if (isFolder()) {
			return "ct-folder";
		} else {
			String ext = StringUtils.substringAfterLast(getName(), ".");
			if (ICONMAP.containsKey(ext))
				return ICONMAP.get(ext);
		}
		return "ct-default";
	}

	@Override
	public String toString() {
		return "#" + id + ": " + name + " (" + type + " - " + size + " bytes)";
	}

	public String getId() {
		return id;
	}

	public String getSequenceId() {
		return sequenceId;
	}

	public String getEtag() {
		return etag;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public long getSize() {
		return size;
	}

	public Date getCreatedAt() {
		return createdAt;
	}

	public String getUrl() {
		return url;
	}

	public List<BoxItem> getPathCollection() {
		return pathCollection;
	}

	public ItemType getType() {
		return type;
	}

	public boolean isFile() {
		return type.equals(ItemType.FILE);
	}

	public boolean isFolder() {
		return type.equals(ItemType.FOLDER);
	}

}
