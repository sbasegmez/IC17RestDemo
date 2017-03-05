package test;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import com.ibm.commons.util.io.json.JsonJavaFactory;
import com.ibm.commons.util.io.json.JsonJavaObject;
import com.ibm.commons.util.io.json.JsonParser;

public class ExchangeRate {
	
	public static Double receiveEurUsdRate() throws Exception {
		CloseableHttpClient httpclient = HttpClients.createDefault();
		HttpGet httpGet = new HttpGet("http://api.fixer.io/latest?base=USD");
		CloseableHttpResponse response = httpclient.execute(httpGet);
	
		try {
		    HttpEntity entity = response.getEntity();
		    
		    // This is the worst practice ever! 
		    // We don't check anything! Everything can go wrong... 
		    JsonJavaObject ratesMap = (JsonJavaObject) JsonParser.fromJson(JsonJavaFactory.instanceEx, EntityUtils.toString(entity));
		    JsonJavaObject rates = ratesMap.getAsObject("rates");
		    
		    // We can write values into a NotesDocument
		    return rates.getAsDouble("EUR");
		    
		} finally {
		    response.close();
		}
	
	}
}
