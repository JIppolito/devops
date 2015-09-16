package org.jenkinsci.plugins.utils;

import hudson.model.BuildListener;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.http.Consts;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.json.simple.JSONArray;

public class RESTUtils {

	private int socketTimeout;
	private int connectTimeout;
	private int connectionRequestTimeout;
	private boolean staleConnectionCheckEnabled;
	private Charset urlEncode;
	
	public RESTUtils() {
		this.setConnectionRequestTimeout(30000);
		this.setConnectTimeout(30000);
		this.setSocketTimeout(30000);
		this.setStaleConnectionCheckEnabled(true);
		this.setUrlEncode(Consts.UTF_8);	
	}
	
	public RESTUtils(int socketTimeout, int connectTimeout, int connectionRequestTimeout, boolean staleConnectionCheckEnabled) {
		this.setConnectionRequestTimeout(connectionRequestTimeout);
		this.setConnectTimeout(connectTimeout);
		this.setSocketTimeout(socketTimeout);
		this.setStaleConnectionCheckEnabled(staleConnectionCheckEnabled);
	}
	
	public int getSocketTimeout() {
		return socketTimeout;
	}

	public void setSocketTimeout(int socketTimeout) {
		this.socketTimeout = socketTimeout;
	}

	public int getConnectTimeout() {
		return connectTimeout;
	}

	public void setConnectTimeout(int connectTimeout) {
		this.connectTimeout = connectTimeout;
	}

	public int getConnectionRequestTimeout() {
		return connectionRequestTimeout;
	}

	public void setConnectionRequestTimeout(int connectionRequestTimeout) {
		this.connectionRequestTimeout = connectionRequestTimeout;
	}

	public boolean isStaleConnectionCheckEnabled() {
		return staleConnectionCheckEnabled;
	}

	public void setStaleConnectionCheckEnabled(boolean staleConnectionCheckEnabled) {
		this.staleConnectionCheckEnabled = staleConnectionCheckEnabled;
	}

	public Charset getUrlEncode() {
		return urlEncode;
	}

	public void setUrlEncode(Charset urlEncode) {
		this.urlEncode = urlEncode;
	}
	
	public JSONObject httpGet(List<NameValuePair> params, URL url, BuildListener listener) {
		   
	   JSONObject o = null;
       //CloseableHttpClient httpclient = HttpClients.createDefault(); 
	   RequestConfig defaultRequestConfig = RequestConfig.custom()
			    .setSocketTimeout(this.getSocketTimeout())
			    .setConnectTimeout(this.getConnectTimeout())
			    .setConnectionRequestTimeout(this.getConnectionRequestTimeout())
			    .setStaleConnectionCheckEnabled(this.isStaleConnectionCheckEnabled())
			    .build();
	   
	   CloseableHttpClient httpclient = HttpClients.custom()
			    .setDefaultRequestConfig(defaultRequestConfig)
			    .build();

	   String paramsString = URLEncodedUtils.format(params, "UTF-8");  
	   HttpGet httpGet = new HttpGet(url.toString() + "?" + paramsString);
	   //Debug
	   //listener.getLogger().println("httpGet Params: " + paramsString);
	   try {
		   CloseableHttpResponse response = httpclient.execute(httpGet);
		   HttpEntity respEntity = response.getEntity();
		   if (respEntity != null) {

		        // EntityUtils to get the response content
		        String content =  EntityUtils.toString(respEntity);
		        //Debug
				//listener.getLogger().println("HttpGet Response Content: " + content);

		        JSONParser j = new JSONParser();
			    o = (JSONObject)j.parse(content);			    
			    return o;
		    }
	   } catch (ClientProtocolException e) {
		   listener.getLogger().println(e.toString());
	   } catch (IOException e) {
		   listener.getLogger().println(e.toString());
	   } catch (ParseException pe) {
		   listener.getLogger().println(pe.toString());
	   }    
	   return o;
   }
	 
	   
	public JSONObject httpPost(List<NameValuePair> params, String url, BuildListener listener) {
		JSONObject o = null;
		try {
			//CloseableHttpClient httpclient = HttpClients.createDefault();  
			   RequestConfig defaultRequestConfig = RequestConfig.custom()
					    .setSocketTimeout(this.getSocketTimeout())
					    .setConnectTimeout(this.getConnectTimeout())
					    .setConnectionRequestTimeout(this.getConnectionRequestTimeout())
					    .setStaleConnectionCheckEnabled(this.isStaleConnectionCheckEnabled())
					    .build();
			   
			   CloseableHttpClient httpclient = HttpClients.custom()
					    .setDefaultRequestConfig(defaultRequestConfig)
					    .build();
		
			HttpPost httpPost = new HttpPost(url);
			httpPost.setEntity(new UrlEncodedFormEntity(params, this.getUrlEncode()));  
			httpPost.setHeader("Accept", "application/json");
			
			//DEBUG
	        //listener.getLogger().println("HTTP POST Reqeust ULR: " + url);
	        //listener.getLogger().println("HTTP POST Reqeust Header: " + httpPost.getFirstHeader("Accept"));	        
	       
	        CloseableHttpResponse response = httpclient.execute(httpPost);  
		    HttpEntity respEntity = response.getEntity();

		    if (respEntity != null) {
		        // EntityUtils to get the response content
		        String content =  EntityUtils.toString(respEntity);
		        JSONParser j = new JSONParser();
			    o = (JSONObject)j.parse(content);
			    httpclient.close();

			    return o;
		    }
		  } catch (IOException e) {
			  listener.getLogger().println(e.toString());
		  } catch (ParseException pe) {
			  listener.getLogger().println(pe.toString());
		  }
		return o;
   }
	
	
	public static Map<String, Object> jsonToMap(JSONObject json) throws Exception
    {
        Map<String, Object> retMap = new HashMap<String, Object>();

        if(json != null) {
            retMap = toMap(json);
        }
        return retMap;
    }

    public static Map<String, Object> toMap(JSONObject object) throws Exception
    {
        Map<String, Object> map = new HashMap<String, Object>();

        Iterator<String> keysItr = object.keySet().iterator();
        while(keysItr.hasNext()) 
        {
            String key = keysItr.next();
            Object value = object.get(key);

            if(value instanceof JSONArray) {
                value = toList((JSONArray) value);
            }

            else if(value instanceof JSONObject) {
                value = toMap((JSONObject) value);
            }
            map.put(key, value);
        }
        return map;
    }

    public static List<Object> toList(JSONArray array) throws Exception
    {
        List<Object> list = new ArrayList<Object>();
        for(int i = 0; i < array.size(); i++)
        {
            Object value = array.get(i);
            if(value instanceof JSONArray) {
                value = toList((JSONArray) value);
            }

            else if(value instanceof JSONObject) {
                value = toMap((JSONObject) value);
            }
            list.add(value);
        }
        return list;
    }


	
}
