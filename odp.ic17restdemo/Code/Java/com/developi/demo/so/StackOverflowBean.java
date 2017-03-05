package com.developi.demo.so;

import java.io.IOException;
import java.io.Serializable;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.ParseException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import com.ibm.commons.util.io.json.JsonException;
import com.ibm.commons.util.io.json.JsonJavaArray;
import com.ibm.commons.util.io.json.JsonJavaFactory;
import com.ibm.commons.util.io.json.JsonJavaObject;
import com.ibm.commons.util.io.json.JsonParser;
import com.ibm.xsp.extlib.util.ExtLibUtil;

public class StackOverflowBean implements Serializable {

	private static final long serialVersionUID = 1L;
	private static final String BASEURL = "https://api.stackexchange.com/2.2";
	private static final int PAGESIZE = 10;
	private static final String FILTER = "withbody";

	private int currentPage = -1;

	private List<List<Question>> pageCache;
	private Map<Long, Question> questionCache;

	public StackOverflowBean() {
		initCache();
	}

	public static final StackOverflowBean getInstance() {
		return (StackOverflowBean) ExtLibUtil.resolveVariable("stackoverflow");
	}
	
	private void initCache() {
		currentPage = -1;
		pageCache = new ArrayList<List<Question>>();
		questionCache = new HashMap<Long, Question>();
		getPageList();
	}

	public List<Question> getPageList() {
		if (currentPage < 0) {
			List<Question> pageList = new ArrayList<Question>(PAGESIZE);
			pageCache.add(pageList);
			currentPage = 0;
			pullCurrentPage();
		}

		return pageCache.get(currentPage);
	}

	private void addQuestion(Question question) {
		questionCache.put(question.getQuestionId(), question);
		List<Question> pageList = getPageList();
		pageList.add(question);
	}

	private void pullCurrentPage() {

		CloseableHttpClient httpclient = HttpClients.createDefault();
		CloseableHttpResponse response = null;

		try {
			URIBuilder builder = new URIBuilder(BASEURL + "/questions/no-answers");
			builder.addParameter("order", "desc");
			builder.addParameter("sort", "activity");
			builder.addParameter("tagged", "xpages");
			builder.addParameter("site", "stackoverflow");
			builder.addParameter("filter", FILTER);
			builder.addParameter("page", String.valueOf(currentPage + 1));
			builder.addParameter("pagesize", String.valueOf(PAGESIZE));

			response = httpclient.execute(new HttpGet(builder.build()));

			HttpEntity entity = response.getEntity();
			parseResults(EntityUtils.toString(entity));

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

	private void parseResults(String jsonString) {
		try {
			// This is the worst practice ever!
			// We don't check anything! Everything can go wrong...
			JsonJavaObject result = (JsonJavaObject) JsonParser.fromJson(JsonJavaFactory.instanceEx, jsonString);
			JsonJavaArray items = result.getAsArray("items");

			for (Object itemObj : items) {
				if (itemObj instanceof JsonJavaObject) {
					addQuestion(toQuestion((JsonJavaObject) itemObj));
				} else {
					throw new RuntimeException("Unexpected result returned as item: " + itemObj.getClass().getCanonicalName());
				}
			}
		} catch (JsonException e) {
			e.printStackTrace();
		} catch (ParseException e) {
			e.printStackTrace();
		}
	}

	private Question toQuestion(JsonJavaObject qJson) {
		Question q = new Question();

		for (Object tagObj : qJson.getAsList("tags")) {
			q.addTag(String.valueOf(tagObj));
		}

		q.setOwner(toOwner(qJson.getAsObject("owner")));
		q.setAnswerCount(qJson.getAsLong("answer_count"));
		q.setAnswered(qJson.getAsBoolean("is_answered"));
		q.setBody(qJson.getAsString("body"));
		q.setCreationDate(new Date(qJson.getAsLong("creation_date") * 1000));
		q.setLink(qJson.getAsString("link"));
		q.setQuestionId(qJson.getAsLong("question_id"));
		q.setScore(qJson.getAsLong("score"));
		q.setTitle(qJson.getAsString("title"));
		q.setViewCount(qJson.getAsLong("view_count"));

		return q;
	}

	private Owner toOwner(JsonJavaObject obj) {
		Owner o = new Owner();

		o.setReputation(obj.getAsLong("reputation"));
		o.setUserId(obj.getAsLong("user_id"));
		o.setDisplayName(obj.getAsString("display_name"));
		o.setLink(obj.getAsString("link"));
		o.setProfileImage(obj.getAsString("profile_image"));

		return o;
	}

	public Question getQuestion(long questionId) {
		return questionCache.get(questionId);
	}

	public int getCurrentPageDisplay() {
		return currentPage + 1;
	}
	
	public void nextPage() {
		currentPage++;
		if(pageCache.size()<=currentPage) {
			pageCache.add(new ArrayList<Question>(PAGESIZE));
			pullCurrentPage();
		}
	}
	
	public void prevPage() {
		currentPage--;
	}
}
