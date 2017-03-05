package com.developi.demo.box;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.NotesException;
import lotus.domino.View;
import lotus.domino.ViewEntry;
import lotus.domino.ViewEntryCollection;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import com.developi.demo.Utils;
import com.ibm.commons.util.io.json.JsonException;
import com.ibm.commons.util.io.json.JsonJavaArray;
import com.ibm.commons.util.io.json.JsonJavaFactory;
import com.ibm.commons.util.io.json.JsonJavaObject;
import com.ibm.commons.util.io.json.JsonParser;
import com.ibm.domino.services.ResponseCode;
import com.ibm.domino.services.ServiceException;
import com.ibm.domino.services.rest.RestServiceEngine;
import com.ibm.xsp.designer.context.XSPUrl;
import com.ibm.xsp.extlib.component.rest.CustomService;
import com.ibm.xsp.extlib.component.rest.CustomServiceBean;
import com.ibm.xsp.extlib.util.ExtLibUtil;

public class BoxBean extends CustomServiceBean implements Serializable {

	private static final long serialVersionUID = 1L;
	
	public static final String BASEURL = "https://api.box.com/2.0";
	
	public static final String BASEURL_AUTH = "https://account.box.com/api/oauth2/authorize";
	public static final String BASEURL_TOKEN = "https://api.box.com/oauth2/token";
	
	public static final String GRANTTYPE_NEWTOKEN = "authorization_code";
	public static final String GRANTTYPE_REFRESHTOKEN = "refresh_token";
		
	private String redirectUrl;
	private String clientId;
	private String clientSecret;
	
	private String userToNotify;
	
	private boolean iKnowMyself = false;
	private String currentUsername; // username
	private String currentEmailAddress; //email_address
	private String currentAvatarUrl; //avatar_url
	
	private Map<String, Token> tokens;
	
	private String currentFolderId=null;
	private Map<String, List<BoxItem>> boxItems = new HashMap<String, List<BoxItem>>();
	private LinkedList<String> folderHistory = new LinkedList<String>();
	
	public BoxBean() {
		loadConfig();
		loadTokens();
	}
	
	public static final BoxBean getInstance() {
		return (BoxBean) ExtLibUtil.resolveVariable("box");
	}
	
	private void loadConfig() {
		Database dbCurrent = ExtLibUtil.getCurrentDatabase();
		View configView = null;
		Document configDoc = null;
		
		try {
			configView = dbCurrent.getView("config");
			configDoc = configView.getFirstDocument();
			
			this.redirectUrl = configDoc.getItemValueString("BoxRedirectUrl");
			this.clientId = configDoc.getItemValueString("BoxClientId");
			this.clientSecret = configDoc.getItemValueString("BoxClientSecret");
			this.userToNotify = configDoc.getItemValueString("NotifyUser");
			
		} catch (NotesException e) {
			e.printStackTrace();
		} finally {
			Utils.recycleObjects(configView, configDoc);
		}
	}

	private void loadTokens() {
		Database dbCurrent = ExtLibUtil.getCurrentDatabase();
		View tokenView = null;
		ViewEntryCollection entries = null;
		ViewEntry entry = null;
		
		tokens = new HashMap<String, Token>();
				
		try {
			tokenView = dbCurrent.getView("(tokens)");
			
			entries = tokenView.getAllEntries();
			entry = entries.getFirstEntry();
			
			while(entry != null) {
				
				Document tokenDoc = entry.getDocument();
				
				try { 
					Token token = Token.readToken(tokenDoc);
					tokens.put(token.getOwner(), token);
				} finally {
					Utils.recycleObjects(tokenDoc);
				}
				
				ViewEntry tmpEntry = entries.getNextEntry(entry);
				Utils.recycleObjects(entry);
				entry = tmpEntry;
			}
			
		} catch (NotesException e) {
			e.printStackTrace();
		} finally {
			Utils.recycleObjects(tokenView, entries);
		}
	}

	
	@Override
	public void renderService(CustomService service, RestServiceEngine engine) throws ServiceException {

		HttpServletRequest request = engine.getHttpRequest();
		HttpServletResponse response = engine.getHttpResponse();

		try {
			String code = request.getParameter("code");
			
			processCode(code);
			
			response.setStatus(200);
			response.setContentType("text/html");
			PrintWriter out = response.getWriter();
			out.println("<html><body onload=\"window.close();\">SUCCESS!</body><html>");
			out.close();
			out.flush();
		} catch (Exception e) {
			e.printStackTrace();
			throw new ServiceException(e, ResponseCode.INTERNAL_ERROR);
		}

	}

	private void processCode(String code) {
		CloseableHttpClient httpclient = HttpClients.createDefault();
		CloseableHttpResponse response = null;

		try {
			URIBuilder builder = new URIBuilder(BASEURL_TOKEN);
			HttpPost post = new HttpPost(builder.build());

			HttpEntity reqEntity = MultipartEntityBuilder.create()
			.addTextBody("grant_type", GRANTTYPE_NEWTOKEN)
			.addTextBody("code", code)
			.addTextBody("client_id", clientId)
			.addTextBody("client_secret", clientSecret)
	        .build();
			
			post.setEntity(reqEntity);
			response = httpclient.execute(post);

			HttpEntity entity = response.getEntity();
			String data = EntityUtils.toString(entity);
			
			if(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
				Token token = new Token();
				processToken(token, data);
				tokens.put(token.getOwner(), token);
			} else {
				System.out.println("Error returned Creating Token: " + response.getStatusLine().toString());
				System.out.println(data);
			}

		} catch (IOException e) {
			e.printStackTrace();
		} catch (URISyntaxException e) {
			e.printStackTrace();
		} finally {
			try {
				if (response != null) {
					response.close();
				}
			} catch (IOException e) {
			}
		}
		
	}

	public boolean isAuthenticated() {
		Token token = getCurrentUserToken();
		
		if(token==null) {
			return false;
		}
			
		if(token.isExpired()) {
			return refreshToken(token);
		}
		
		return true;
	}

	private boolean refreshToken(Token token) {
		CloseableHttpClient httpclient = HttpClients.createDefault();
		CloseableHttpResponse response = null;

		try {
			URIBuilder builder = new URIBuilder(BASEURL_TOKEN);
			HttpPost post = new HttpPost(builder.build());

			HttpEntity reqEntity = MultipartEntityBuilder.create()
			.addTextBody("grant_type", GRANTTYPE_REFRESHTOKEN)
			.addTextBody("refresh_token", token.getRefreshToken())
			.addTextBody("client_id", clientId)
			.addTextBody("client_secret", clientSecret)
	        .build();
			
			post.setEntity(reqEntity);
			response = httpclient.execute(post);
			HttpEntity entity = response.getEntity();
			String data = EntityUtils.toString(entity);
			
			if(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
				processToken(token, data);
				System.out.println("Token refreshed!");
				return true;
			} else {
				System.out.println("Error returned Refreshing Token: " + response.getStatusLine().toString());
				System.out.println(data);
			}

		} catch (IOException e) {
			e.printStackTrace();
		} catch (URISyntaxException e) {
			e.printStackTrace();
		} finally {
			try {
				if (response != null) {
					response.close();
				}
			} catch (IOException e) {
			}
		}
		
		return false;
	}

	private void processToken(Token token, String jsonString) {
		try {
			JsonJavaObject json = (JsonJavaObject) JsonParser.fromJson(JsonJavaFactory.instanceEx, jsonString);
			
			token.setAccessToken(json.getAsString("access_token"));
			token.setRestrictedTo(json.getAsString("restricted_to"));
			token.setTokenType(json.getAsString("token_type"));
			token.setRefreshToken(json.getAsString("refresh_token"));

			int expiresIn = json.getAsInt("expires_in") - 120; // Define two minutes threshold for safety
			token.setExpireTime(System.currentTimeMillis() + (1000*expiresIn));
						
			token.save();
			
		} catch (JsonException e) {
			e.printStackTrace();
		}

	}

	private Token getCurrentUserToken() {
		try {
			return tokens.get(ExtLibUtil.getCurrentSession().getEffectiveUserName());
		} catch (NotesException e) {
			e.printStackTrace();
		}
		return null;
	}

	public String getOauthLink() {
		XSPUrl xspUrl = new XSPUrl(BASEURL_AUTH);
		xspUrl.setParameter("response_type", "code");
		xspUrl.setParameter("client_id", clientId);
		xspUrl.setParameter("redirect_uri", redirectUrl);
		xspUrl.setParameter("state", "authenticated");
		return xspUrl.toString();
	}

	private void whoAmI() {
		if(! iKnowMyself) {
			CloseableHttpClient httpclient = HttpClients.createDefault();
			CloseableHttpResponse response = null;

			try {
				URIBuilder builder = new URIBuilder(BASEURL + "/users/me");
				HttpGet get = new HttpGet(builder.build());
				
				get.addHeader("Authorization", "Bearer " + getCurrentUserToken().getAccessToken());
				
				response = httpclient.execute(get);
				HttpEntity entity = response.getEntity();
				String data = EntityUtils.toString(entity);
				
				if(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
					JsonJavaObject json = (JsonJavaObject) JsonParser.fromJson(JsonJavaFactory.instanceEx, data);
					this.currentUsername = json.getAsString("name");
					this.currentEmailAddress = json.getAsString("login");
					this.currentAvatarUrl = json.getAsString("avatar_url");
				} else {
					System.out.println("Error returned My Info: " + response.getStatusLine().toString());
					System.out.println(data);
				}

			} catch (IOException e) {
				e.printStackTrace();
			} catch (URISyntaxException e) {
				e.printStackTrace();
			} catch (JsonException e) {
				e.printStackTrace();
			} finally {
				try {
					if (response != null) {
						response.close();
					}
				} catch (IOException e) {
				}
			}

		}
	}
	
	public String getUserToNotify() {
		return userToNotify;
	}
	
	public String getCurrentUsername() {
		whoAmI();
		return currentUsername;
	}

	public String getCurrentEmailAddress() {
		whoAmI();
		return currentEmailAddress;
	}

	public String getCurrentAvatarUrl() {
		whoAmI();
		return currentAvatarUrl;
	}
	
	public List<BoxItem> getCurrentItems() {
		if(StringUtils.isEmpty(currentFolderId)) {
			changeFolder("0"); // Go to the root
		}
		
		return boxItems.get(currentFolderId);
	}
	
	public void backToLastFolder() {
		if("0".equals(currentFolderId)) {
			// do nothing
			return;
		}
		
		folderHistory.removeLast();
		changeFolder(folderHistory.pollLast());
	}
	
	public void changeFolder(String folderId) {

		this.currentFolderId = folderId;
		this.folderHistory.add(folderId);

		System.out.println(folderHistory);
		
		if(boxItems.containsKey(folderId)) {
			return;
		}
		
		List<BoxItem> items=new ArrayList<BoxItem>();

		String serviceUrl="/folders/"+(StringUtils.isEmpty(folderId)?"0":folderId)+"/items";

		CloseableHttpClient httpclient = HttpClients.createDefault();
		CloseableHttpResponse response = null;

		try {
			URIBuilder builder = new URIBuilder(BASEURL + serviceUrl)
			.addParameter("fields", "name");
			
			HttpGet get = new HttpGet(builder.build());
			
			get.addHeader("Authorization", "Bearer " + getCurrentUserToken().getAccessToken());
			
			response = httpclient.execute(get);
			HttpEntity entity = response.getEntity();
			String data = EntityUtils.toString(entity);

			if(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
				JsonJavaObject json = (JsonJavaObject) JsonParser.fromJson(JsonJavaFactory.instanceEx, data);
				
				if(json!=null && json.containsKey("entries")) {
					JsonJavaArray entries=json.getAsArray("entries");
					for(Object item: entries) {
						if(item instanceof JsonJavaObject) {
							items.add(new BoxItem((JsonJavaObject) item));	
						}
					}
				}

				boxItems.put(folderId, items);
				
			} else {
				System.out.println("Error returned My Info: " + response.getStatusLine().toString());
				System.out.println(data);
			}

		} catch (IOException e) {
			e.printStackTrace();
		} catch (URISyntaxException e) {
			e.printStackTrace();
		} catch (JsonException e) {
			e.printStackTrace();
		} finally {
			try {
				if (response != null) {
					response.close();
				}
			} catch (IOException e) {
			}
		}
	}
}
