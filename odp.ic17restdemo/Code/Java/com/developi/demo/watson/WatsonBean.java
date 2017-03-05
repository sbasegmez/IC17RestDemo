package com.developi.demo.watson;

import java.io.IOException;
import java.io.Serializable;
import java.net.URISyntaxException;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.NotesException;
import lotus.domino.View;

import org.apache.http.HttpEntity;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import com.developi.demo.Utils;
import com.developi.demo.so.Question;
import com.developi.demo.so.StackOverflowBean;
import com.ibm.commons.util.io.json.JsonJavaObject;
import com.ibm.xsp.extlib.util.ExtLibUtil;

public class WatsonBean implements Serializable {
	
	private static final long serialVersionUID = 1L;

	private String nlUrl;
	private String nlUsername;
	private String nlPassword;
	private String nlClassifierId;

	public WatsonBean() {
		loadConfig();
	}
		
	private void loadConfig() {
		Database dbCurrent = ExtLibUtil.getCurrentDatabase();
		View configView = null;
		Document configDoc = null;
		
		try {
			configView = dbCurrent.getView("config");
			configDoc = configView.getFirstDocument();
			
			this.nlUrl = configDoc.getItemValueString("WatsonNLUrl");
			this.nlUsername = configDoc.getItemValueString("WatsonNLUsername");
			this.nlPassword = configDoc.getItemValueString("WatsonNLPassword");
			this.nlClassifierId = configDoc.getItemValueString("WatsonNLClassifierId");
			
		} catch (NotesException e) {
			e.printStackTrace();
		} finally {
			Utils.recycleObjects(configView, configDoc);
		}
	}

	public String ask(long questionId) {
		StackOverflowBean so = StackOverflowBean.getInstance();
				
		return ask(so.getQuestion(questionId));
	}

	private String ask(Question question) {
		JsonJavaObject requestJson = new JsonJavaObject();
		requestJson.put("text", clearText(question));
		
		CloseableHttpClient httpClient = null;
		CloseableHttpResponse response = null;

		try {
			URIBuilder builder = new URIBuilder(nlUrl + "/v1/classifiers/"+nlClassifierId+"/classify");
			
			CredentialsProvider credsProvider = new BasicCredentialsProvider();
	        credsProvider.setCredentials(
	                new AuthScope(builder.getHost(), builder.getPort()),
	                new UsernamePasswordCredentials(nlUsername, nlPassword));
			
			httpClient = HttpClients.custom().setDefaultCredentialsProvider(credsProvider).build();
			
			HttpPost post = new HttpPost(builder.build());
			StringEntity reqEntity = new StringEntity(requestJson.toString(), ContentType.APPLICATION_JSON);
			post.setEntity(reqEntity);
			
			response = httpClient.execute(post);

			HttpEntity entity = response.getEntity();

			return EntityUtils.toString(entity);

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
		
		return "Error!";
	}

	private static String clearText(Question question) {
		String text = question.getTitle() + " " + question.getBody();

		// Yet another bad practice... :)
		text = text
				.replaceAll("[\\s\\n\\r\\t\\\"]", " ")
				.replaceAll("<pre.*<\\/pre>", "")
				.replaceAll("<.*?>", "")
				.replaceAll("&[lg]t;", "")
				.replaceAll("\\s+", " ");
		
		if(text.length()>1000) {
			return text.substring(0, 1000).trim();
		} else {
			return text.trim();
		}
		
	}
	
}
