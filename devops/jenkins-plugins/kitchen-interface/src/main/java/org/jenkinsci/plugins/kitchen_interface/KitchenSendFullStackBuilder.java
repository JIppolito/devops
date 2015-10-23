package org.jenkinsci.plugins.kitchen_interface;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.ParameterValue;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.ParametersAction;
import hudson.model.StringParameterValue;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;

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
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.springframework.util.StringUtils;


import org.jenkinsci.plugins.utils.*;

/*   
 * 0.3.0
 * DONE Full Stack Deployment Plugin succeeded in calling a stack type (kitchen)
 * DONE Test on a Stack Type that will complete
 * 
 * 0.3.1
 * - Take a comma delimited list of zones
 * - Have variables to set various timeouts for CustomHTTPClient for httpGet and httpPost as 
 * part of Kitchen URL options:
 * 	.setSocketTimeout(30000)
	.setConnectTimeout(30000)
	.setConnectionRequestTimeout(30000)
	.setStaleConnectionCheckEnabled(true)
   - Dynamically create DNS_Entries key/value Jenkins Parameters
   - Visually display Kitchen ID and Environment (Prod, Preprod, Specific DevBranch) so that 
   the Promote for each 
	
 * 
 */

public class KitchenSendFullStackBuilder extends Builder {

	private final String FAILED = "failed";
	private final String QUEUED = "queued";
	private final String COMPLETE = "complete";
	private final String KITCHEN_REQUEST_TYPE = "group";
	private final String DEFAULT_ZONE = "us-east-1a";
	
	private final int max_timeout;	
    private final String kitchenURL;
    private final String branch;
    private final String stack_type;
    private final String chef_environment;
    private final String cloud_tag;
    private final String chefAttributes;
    private final String customOptions;
    private final String avail_Zones;

    
	private final int socketTimeout;
	private final int connectTimeout;
	private final int connectionRequestTimeout;
	private final boolean staleConnectionCheckEnabled;
    
    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public KitchenSendFullStackBuilder(String kitchenURL, String branch,  String stack_type, String chef_environment, 
    		String cloud_tag, String avail_Zones,
    		String chefAttributes, String customOptions, String max_timeout, String socketTimeout, String connectTimeout, 
    		String connectionRequestTimeout, boolean staleConnectionCheckEnabled  ) {
    	
    	this.kitchenURL = kitchenURL;      
        this.branch=branch;       
        this.stack_type=stack_type;
        this.chef_environment=chef_environment;      
        this.cloud_tag = cloud_tag;
        this.max_timeout = Integer.parseInt(max_timeout);
        this.chefAttributes = chefAttributes;      
        this.customOptions=customOptions;
        this.avail_Zones = avail_Zones;
        
		this.connectionRequestTimeout = Integer.valueOf(connectionRequestTimeout);
		this.connectTimeout = Integer.valueOf(connectTimeout);
		this.socketTimeout = Integer.valueOf(socketTimeout);
		this.staleConnectionCheckEnabled = staleConnectionCheckEnabled;
        
    }

    /**
     * We'll use this from the config.jelly.
     */
	public String getKitchenURL(){ return this.kitchenURL; }   
	public String getBranch() { return this.branch; }
	public String getStack_type() { return this.stack_type; }
	public String getChef_environment() { return this.chef_environment; }
	public String getCloud_tag() { return this.cloud_tag; }
	public int getMax_timeout() { return this.max_timeout; }
	public String getChefAttributes() { return this.chefAttributes; }
	public String getCustomOptions() { return this.customOptions; }
	public String getAvail_Zones() { return this.avail_Zones; }
	
	public int getSocketTimeout() { return socketTimeout; }
	public int getConnectTimeout() { return connectTimeout; }
	public int getConnectionRequestTimeout() { return connectionRequestTimeout; }
	public boolean getStaleConnectionCheckEnabled() { return staleConnectionCheckEnabled; }

	 /*
     * Set Kitchen URL and Creds
     * Send Kitchen Stack Instance POST
     * if response is 200 continue and get the KitchenId 
     * else fail the build
     * 
     * Get Current time
     * 
     * Poll Logic:
     * while true
     * Poll Kitchen Status URL
     * if status not Failed AND current time not greater than start time + maxtime   
     * 	sleep 10 seconds
     * else
     * 	break
     * 
     * 
     * Build Status Logic:
     * if status is complete
     *      get ip addresses
     *      get dns entries
     *  	mark build stable
     *  elif status is failed or null
     *  	try to get ip and dns
     *  	if fail to get ip or dns
     *   	mark build failed
     *  else
     *  	mark build unstable    
     * 
     */
	
	@Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
		//parse response {"request_id":"535836bc09228d2676000186"}
		String kitchenid = "";
		String kitchenString = "kitchen_request_id_";
		try {
			kitchenid=this.sendKitchenStackBuildRequest(listener, build);
			
			//DEBUG
            listener.getLogger().println("kitchen_request_id: " + kitchenid);
           

            String stack_type = this.resolveParameterVariable("stack_type", build, listener);
            stack_type = stack_type.replaceAll("[^A-Za-z0-9]", "");
            String kitchen_request_id =  kitchenString.concat(stack_type);
            
            //DEBUG
            //listener.getLogger().println("stack_type: " + stack_type);
            listener.getLogger().println("kitchen_request_id: " + kitchen_request_id);
            
            List<ParameterValue> params = new ArrayList<ParameterValue>();
            params.add(new StringParameterValue(kitchen_request_id, kitchenid)); 
            build.addAction(new ParametersAction(params));          
            
		} catch (Exception e) {
            listener.getLogger().println(e.toString());
            return false;
		} 
		
		if (kitchenid == null) {
	        listener.getLogger().println("ERROR: Failed to get an ID from Kitchen");
	        build.setResult(Result.FAILURE);
	        return false;
		}

		//parse response: {"request_type":"group","request_id":"5391e8b409228d3e7e000468","status":"queued"}
		String type=this.KITCHEN_REQUEST_TYPE;
		String response = null;
		long end_time =  System.currentTimeMillis() + (this.max_timeout * 60000);
		long now = System.currentTimeMillis();
		while (true) {
			try {
				response=this.sendKitchenStatusRequest(kitchenid, type, listener);
				
				//DEBUG
	            listener.getLogger().println("Response: " + response);
	            listener.getLogger().println("Now: " + Long.toString(now) + " < Max Timeout: " + Long.toString(end_time));
	            
				if (response == null || response.equals(this.FAILED) || response.equals(this.COMPLETE) || now > end_time) {
					break;
				}
			} catch (IOException e) {
				e.printStackTrace();
				listener.getLogger().println(e.toString());
		        build.setResult(Result.FAILURE);
		        return false;
			}
			
            try {
            	// thread to sleep for 10000 milliseconds
            	Thread.sleep(10000);
            } catch (Exception e) {
            	 System.out.println(e);
            }              
            now = System.currentTimeMillis();
		}
		
		String ip_address;
		HashMap<?, ?> dns_entries;
		if (response == null || response.equals(this.FAILED)) {
			
        	listener.getLogger().println("ERROR: Build failed to build correctly or did not get a response from Kitchen. Please check Kitchen server for a more detailed error message: " + response);      
        	
			// Attempt to get IP_ADDRESS as sometimes kitchen fails but still builds the instance 
			// in which case Jenkins should still try to kill the instance
        	try {
				ip_address=this.sendKitchenIpRequest(kitchenid, type, listener);
				dns_entries=this.sendKitchenDNSEntriesRequest(kitchenid, type, listener);
	        	listener.getLogger().println("INFO: Attempt to get IP_ADDRESS and DNS Entries as sometimes kitchen fails but still builds the instance. Please check Kitchen server for a more detailed error message: " + ip_address + ", DNS Entry:" + dns_entries.toString());   
	        	//Does Work
				List<ParameterValue> params1 = new ArrayList<ParameterValue>();
	            params1.add(new StringParameterValue("ip_address", ip_address));
	            Iterator<?> it = dns_entries.entrySet().iterator();
	            while(it.hasNext()) {
	            	Map.Entry pairs = (Map.Entry)it.next();
	            	params1.add(new StringParameterValue(pairs.getKey().toString(), pairs.getValue().toString()));
	            }
	            params1.add(new StringParameterValue("dns_entries", dns_entries.toString()));
	            build.addAction(new ParametersAction(params1));

	            if (ip_address == null || ip_address.length() == 0 || dns_entries == null || dns_entries.size() == 0) {
		            build.setResult(Result.FAILURE);
	            } else {
		            build.setResult(Result.UNSTABLE);
	            }
	   
			} catch (Exception e) {
				build.setResult(Result.FAILURE);
				listener.getLogger().println("ERROR: Failed to get IP_ADDRESS from Kitchen. Please check Kitchen server for a more detailed error message: " + e.toString());
				return false;	
			}

		} else if (response.equals(this.COMPLETE)) {
			try {
				ip_address=this.sendKitchenIpRequest(kitchenid, type, listener);
				dns_entries=this.sendKitchenDNSEntriesRequest(kitchenid, type, listener);

				List<ParameterValue> params2 = new ArrayList<ParameterValue>();
	            params2.add(new StringParameterValue("ip_address", ip_address));
	            Iterator<?> it = dns_entries.entrySet().iterator();
	            
	            //TODO Add iterator tag to ensure that if there are multiple dns entries that they are unique
	            while(it.hasNext()) {
	            	Map.Entry pairs = (Map.Entry)it.next();
	            	params2.add(new StringParameterValue(pairs.getKey().toString(), pairs.getValue().toString()));
	            }
	            params2.add(new StringParameterValue("dns_entries", dns_entries.toString()));
	            build.addAction(new ParametersAction(params2));
	           
			} catch (Exception e) {
				listener.getLogger().println("ERROR: Failed to get IP_ADDRESS from Kitchen. Please check Kitchen server for a more detailed error message: " + e.toString());
		        build.setResult(Result.FAILURE);
		        return false;
			} 
			build.setResult(Result.SUCCESS);

		} else {
			// Attempt to get IP_ADDRESS as sometimes kitchen fails but still builds the instance 
			// in which case Jenkins should still try to kill the instance
			try {
				ip_address=this.sendKitchenIpRequest(kitchenid, type, listener);
				dns_entries=this.sendKitchenDNSEntriesRequest(kitchenid, type, listener);
	        	//Does Work
				List<ParameterValue> params3 = new ArrayList<ParameterValue>();
	            params3.add(new StringParameterValue("ip_address", ip_address));
	            Iterator<?> it = dns_entries.entrySet().iterator();
	            while(it.hasNext()) {
	            	Map.Entry pairs = (Map.Entry)it.next();
	            	params3.add(new StringParameterValue(pairs.getKey().toString(), pairs.getValue().toString()));
	            }
	            params3.add(new StringParameterValue("dns_entries", dns_entries.toString()));
	            build.addAction(new ParametersAction(params3));
				
			} catch (Exception e) {
				build.setResult(Result.FAILURE);
				listener.getLogger().println(e.toString());
				return false;		
			}
           
			build.setResult(Result.UNSTABLE);
        	listener.getLogger().println("ERROR: Unsure why build did not complete correctly. Please check Kitchen server for a more detailed error message.  Kitchen Response: " + response);
	        return true;

		}
		
		return true;
    }
	
	/*
	 * Use reflection to check if value of a field is parameterized and return the String else return the fields 
	 * default value
	 */
	private String resolveParameterVariable(String fieldname, AbstractBuild build,  BuildListener listener) 
			throws NoSuchFieldException, SecurityException, IOException, InterruptedException, IllegalArgumentException, IllegalAccessException {
		
		Field field = this.getClass().getDeclaredField(fieldname);
		EnvVars envVars = build.getEnvironment(listener);
       	//listener.getLogger().println("Field: " + field); 
       	//listener.getLogger().println("Field Value: " + field.get(this).toString()); 

		if (field.getType().equals(String.class) && field.get(this).toString().startsWith("$")) {
			//DEBUG
			//listener.getLogger().println("Field: " + field.toString()); 
			String name = field.getName();
			
			//DEBUG
			//listener.getLogger().println("EnvVars: " + envVars.get(name)); 
			
			String value = envVars.get(name);
			return value;
			//This sets the value of the field of the current object which will be saved to the builder
			//and will overwrite the variable ${myvariable}
			//field.set(this, value);
		}	    
	    return field.get(this).toString();
	}
  	
	public String sendKitchenStackBuildRequest(BuildListener listener, AbstractBuild build) throws IOException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException, InterruptedException {
	//stack_type=demandmgt-int, avail_zones=us-east-1d,, environment=stg, branch_default=master, cloud_tag=--US13878-DM-Opp-Persist-Savings, chef_attributes={"dmgt_service":{"version":"1.0.2-US13878-DM-Opp-Persist-Savings-SNAPSHOT"}}]
		String url = this.kitchenURL + "/stack_deploy_post";
		JSONObject o = new JSONObject();
	    String request_id = "";

		int buildNo = build.getNumber();
		String cloudTagTmp = this.resolveParameterVariable("cloud_tag", build, listener);
		String branch = this.resolveParameterVariable("branch", build, listener);
		branch = branch.replace("origin/", "");
		if (branch == null || branch.length() == 0) branch = "none";
		if (cloudTagTmp == null || cloudTagTmp.length() == 0) cloudTagTmp = "Jenkins";
		String cloudTag = branch + "_" + cloudTagTmp + "_" + String.valueOf(buildNo);
		
		// Request parameters and other properties.
		List<NameValuePair> params = new ArrayList<NameValuePair>();  
        params.add(new BasicNameValuePair("stack_type", this.resolveParameterVariable("stack_type", build, listener)));
        params.add(new BasicNameValuePair("avail_zones", this.resolveParameterVariable("avail_Zones", build, listener)));
	    params.add(new BasicNameValuePair("environment", this.resolveParameterVariable("chef_environment", build, listener)));
        params.add(new BasicNameValuePair("branch_default", branch));
        params.add(new BasicNameValuePair("cloud_tag", cloudTag));

        //optional params
        if (this.chefAttributes !=null && this.chefAttributes.length() > 0) {
        	params.add(new BasicNameValuePair("chef_attributes", this.resolveParameterVariable("chefAttributes", build, listener)));
        }
        if (this.customOptions !=null && this.customOptions.length() > 0) {
        	params.add(new BasicNameValuePair("custom_options", this.resolveParameterVariable("customOptions", build, listener)));
        }       
        
        //DEBUG
        listener.getLogger().println("HTTP Post Data: " + params.toString());
        
    	//String test_id=this.httpPostMOCK(params, url, listener);
    	//return test_id;
    	
        RESTUtils utils = new RESTUtils(this.getSocketTimeout(), this.getConnectTimeout(), this.getConnectionRequestTimeout(), this.getStaleConnectionCheckEnabled());

    	o=utils.httpPost(params, url, listener);
    	if (o != null) {
    		request_id = (String) o.get("request_id");
    		return request_id;
    	}
    	return null;
    	
	}
  
    //Method for DNS Entries: curl -H Accept:application/json http://localhost/dns_entries?id=53b428cc09228dcf65000008&type=group
    public HashMap<String, Object> sendKitchenDNSEntriesRequest(String kitchenId, String type, BuildListener listener) throws Exception {
    	//{"request_type":"group","request_id":"53b15ef42b8ff4db1b000125","dns_entries":{"api":"internal-api-stg-3Q336O6RR-453620033.us-east-1.elb.amazonaws.com","dmgt-service":"internal-dmgt-service-stg-V4DAUP4F1-133830845.us-east-1.elb.amazonaws.com"}}
    	JSONObject o;
    	
    	URL url = new URL(this.kitchenURL + "/dns_entries");
    	List<NameValuePair> params = new ArrayList<NameValuePair>();  
		params.add(new BasicNameValuePair("type", type));
		params.add(new BasicNameValuePair("id", kitchenId));
		
        RESTUtils utils = new RESTUtils(this.getSocketTimeout(), this.getConnectTimeout(), this.getConnectionRequestTimeout(), this.getStaleConnectionCheckEnabled());
    	o=utils.httpGet(params, url, listener);   	    	
    	//DEBUG
        listener.getLogger().println("HTTP GET Response :" + o.toString());
        
        //TODO:  
        //This is NOT an Array but a list.  This code is wrong!!  Try this: Object obj = parser.parse(json);  LinkedHashMap map = (LinkedHashMap)obj;
        JSONObject DNSEntries = (JSONObject) o.get("dns_entries");
		HashMap<String, Object> map = (HashMap<String, Object>) RESTUtils.jsonToMap(DNSEntries);
        listener.getLogger().println("HTTP GET Response DNS Entries" + map.toString());

    	return map;
    }	
	
    //Method for ip_addresses: curl -H Accept:application/json http://localhost/ip_addresses?id=53b428cc09228dcf65000008&type=group
    public String sendKitchenIpRequest(String kitchenId, String type, BuildListener listener) throws IOException {
    	JSONObject o;
        String it = new String();
    	String ip = "";
    	//Debug
    	//listener.getLogger().println("sendKitchenIpRequest: " + kitchenId);
    	
        RESTUtils utils = new RESTUtils(this.getSocketTimeout(), this.getConnectTimeout(), this.getConnectionRequestTimeout(), this.getStaleConnectionCheckEnabled());

        URL url = new URL(this.kitchenURL + "/ip_addresses");
    	List<NameValuePair> params = new ArrayList<NameValuePair>();  
		params.add(new BasicNameValuePair("type", type));
		params.add(new BasicNameValuePair("id", kitchenId));
		//DEBUG
        //listener.getLogger().println("HTTP GET Params :" + params.toString());
        
    	o=utils.httpGet(params, url, listener);   	
    	//DEBUG
        //listener.getLogger().println("HTTP GET Response :" + o.toString());
        
        //TODO:  When this array is empty this will throw an exception.
        //Should be handled cleaner
    	if ( o == null) {
    		return "0.0.0.0";
    	}
    	
    	JSONArray jsonArry = (JSONArray) o.get("ip_addresses");
    	Iterator<?> iterator = jsonArry.iterator();
		while (iterator.hasNext()) {
	        it = (String) iterator.next();	        
			ip=ip.concat(it);
			if (iterator.hasNext()) ip=ip.concat(",");
		}		
		//DEBUG
        listener.getLogger().println("ip_addresses:" + ip);

    	return ip;
    }
        
    public String sendKitchenStatusRequest(String kitchenId, String type, BuildListener listener) throws IOException {
    	URL url = new URL(this.kitchenURL + "/request_status");
    	JSONObject o;
    	String id = null;
        RESTUtils utils = new RESTUtils(this.getSocketTimeout(), this.getConnectTimeout(), this.getConnectionRequestTimeout(), this.getStaleConnectionCheckEnabled());

    	List<NameValuePair> params = new ArrayList<NameValuePair>();  
		params.add(new BasicNameValuePair("type", type));
		params.add(new BasicNameValuePair("id", kitchenId));
		
    	o=utils.httpGet(params, url, listener);
    	
        //TODO:  When this array is empty this will throw an exception.
        //Should be handled cleaner
    	id = (String) o.get("status");
    	return id;
    }
          
   
    /*
   private JSONObject httpGet(List<NameValuePair> params, URL url, BuildListener listener) {
	   
	   JSONObject o = null;
       //CloseableHttpClient httpclient = HttpClients.createDefault(); 
	   RequestConfig defaultRequestConfig = RequestConfig.custom()
			    .setSocketTimeout(30000)
			    .setConnectTimeout(30000)
			    .setConnectionRequestTimeout(30000)
			    .setStaleConnectionCheckEnabled(true)
			    .build();
	   
	   CloseableHttpClient httpclient = HttpClients.custom()
			    .setDefaultRequestConfig(defaultRequestConfig)
			    .build();

	   String paramsString = URLEncodedUtils.format(params, "UTF-8");  
	   HttpGet httpGet = new HttpGet(url.toString() + "?" + paramsString);
	   
	   try {
		   CloseableHttpResponse response = httpclient.execute(httpGet);
		   HttpEntity respEntity = response.getEntity();
		   if (respEntity != null) {
		        // EntityUtils to get the response content
		        String content =  EntityUtils.toString(respEntity);
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
 
   
   private JSONObject httpPost(List<NameValuePair> params, String url, BuildListener listener) {
	   JSONObject o = null;
		try {
			//CloseableHttpClient httpclient = HttpClients.createDefault();  
			   RequestConfig defaultRequestConfig = RequestConfig.custom()
					    .setSocketTimeout(30000)
					    .setConnectTimeout(30000)
					    .setConnectionRequestTimeout(30000)
					    .setStaleConnectionCheckEnabled(true)
					    .build();
			   
			   CloseableHttpClient httpclient = HttpClients.custom()
					    .setDefaultRequestConfig(defaultRequestConfig)
					    .build();
		
			HttpPost httpPost = new HttpPost(url);
			httpPost.setEntity(new UrlEncodedFormEntity(params, Consts.UTF_8));  
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
   */
   
	
    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    /**
     * Descriptor for {@link KitchenStatusBuilder}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     * <p>
     * See <tt>src/main/resources/hudson/plugins/hello_world/HelloWorldBuilder/*.jelly</tt>
     * for the actual HTML fragment for the configuration screen.
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        public DescriptorImpl() {
            load();
        }
        
        // If value begins with $ then it is a parameterized input
        private boolean isParameterValue(String value) {
        	if (value.startsWith("$")) return true;
        	return false;
        }
        
        private boolean isInteger(String value) {
      	  try  
      	   {  
      	      Integer.parseInt( value );  
      	      return true;  
      	   }  
      	   catch( Exception e)  
      	   {  
      	      return false;  
      	   }  
      }
        
        
        
        // return value of $myvalue OR ${myvalue} 
        // assumes value starts with $
        private String getParameterizedValue(String value) {
        	String param = "";
        	if (value.startsWith("$") && value.endsWith("}") && value.startsWith("{", 1)) {
        		param=value.substring(2, value.length()-1);
        		return param;
        	} else if (value.startsWith("$")) {
        		param=value.substring(1);
        		return param;
        	} else {
        		return value;
        	}       	
        	
        }
        
        public FormValidation doCheckKitchenURL(@QueryParameter String value)
                throws IOException, ServletException {
            if (value == null || value.length() == 0)
                return FormValidation.error("Please set a name");

            try {
            	URL url = new URL(value);
           	 	url.toURI();
			} catch (URISyntaxException e) {
				return FormValidation.error("Please set a valid URL");
			}
            
            return FormValidation.ok();
        }               
        
        public FormValidation doCheckBranch(@QueryParameter String value)
                throws IOException, ServletException {
        	if (value == null || value.length() == 0) {
                return FormValidation.error("Cannot be empty");
        	} else if (isParameterValue(value)) {
        		return FormValidation.warning("Ensure that " + this.getParameterizedValue(value) + " is a valid Parameterized String Input Variable for this build");
        	} 
            return FormValidation.ok();
        }
        
        public FormValidation doCheckStack_type(@QueryParameter String value)
                throws IOException, ServletException {
        	if (value == null || value.length() == 0) {
                return FormValidation.error("Cannot be empty");
        	} else if (isParameterValue(value)) {
        		return FormValidation.warning("Ensure that " + this.getParameterizedValue(value) + " is a valid Parameterized String Input Variable for this build");
        	} 
            return FormValidation.ok();
        }
       
        public FormValidation doCheckAvail_Zones(@QueryParameter String value)
                throws IOException, ServletException {
        	 	
        	if (value == null || value.length() == 0) {
                return FormValidation.error("Usage: Cannot be empty: { us-east-1a | us-east-1b | us-east-1c | us-east-1d }");
        	} else if (isParameterValue(value)) {
        		return FormValidation.warning("Usage: Ensure that " + this.getParameterizedValue(value) + " is a valid Parameterized String Input Variable for this build that equals { us-east-1a | us-east-1b | us-east-1c | us-east-1d } or a comma-delimited list");
        	} else if (value.contains(",")) {
        		String[] zones = value.split(",");
        		
        		for (String zone : zones) {       		
        			if (!(zone.equals("us-east-1a") || zone.equals("us-east-1b") || zone.equals("us-east-1c") || zone.equals("us-east-1d"))) {
        				return FormValidation.error("Usage: The " + zone + " needs to be a comma-separted list made up of { us-east-1a | us-east-1b | us-east-1c | us-east-1d }");
        			}
        		}
        	} else if (!(value.equals("us-east-1a") || value.equals("us-east-1b") || value.equals("us-east-1c") || value.equals("us-east-1d"))) {
    				return FormValidation.error("Usage: The " + value + " needs to be { us-east-1a | us-east-1b | us-east-1c | us-east-1d }");    			
        	}
            return FormValidation.ok();
        }
        
        public FormValidation doCheckChefAttributes(@QueryParameter String value)
                throws IOException, ServletException {
        	if (value != null && value.length() > 0 ) {
        		if (isParameterValue(value)) {
            		return FormValidation.warning("Ensure that " + value + " is enclosed in { }");
        		}
        		
        		if (value.startsWith("{") && value.endsWith("}")) {
                    return FormValidation.ok();
        		}  else {
            		return FormValidation.warning("Custom Options " + value + " needs to be enclosed in { }");
        		}        		
        	} 
        	
            return FormValidation.ok();
        }       
        
        public FormValidation doCheckCustomOptions(@QueryParameter String value)
                throws IOException, ServletException {
        	if (value != null && value.length() > 0 ) {
        		if (isParameterValue(value)) {
            		return FormValidation.warning("Ensure that " + value + " is enclosed in { }");
        		}
        		
        		if (value.startsWith("{") && value.endsWith("}")) {
                    return FormValidation.ok();
        		}  else {
            		return FormValidation.warning("Custom Options " + value + " needs to be enclosed in { }");
        		}        		
        	} 
        	
            return FormValidation.ok();
        }       
        
        public FormValidation doCheckChef_environment(@QueryParameter String value)
                throws IOException, ServletException {
        	if (value == null || value.length() == 0) {
                return FormValidation.error("Cannot be empty");
        	} else if (isParameterValue(value)) {
        		return FormValidation.warning("Ensure that " + this.getParameterizedValue(value) + " is a valid Parameterized String Input Variable for this build");
        	} 
            return FormValidation.ok();
        }
                
        public FormValidation doCheckCloud_tag(@QueryParameter String value)
                throws IOException, ServletException {
        	if (value == null || value.length() == 0) {
                return FormValidation.error("Cannot be empty");
        	} else if (isParameterValue(value)) {
        		return FormValidation.warning("Ensure that " + this.getParameterizedValue(value) + " is a valid Parameterized String Input Variable for this build");
        	} 
            return FormValidation.ok();
        }
                      
        
        public FormValidation doCheckMax_timeout(@QueryParameter String value)
                throws IOException, ServletException {
        	if (value == null || value.length() == 0) {
                return FormValidation.error("Usage: Needs to be a number greater than 0");
        	} else if ( this.isInteger(value) == false) {
                return FormValidation.error("Usage: Needs to be an Integer ");
        	} else if ( Integer.valueOf(value) <= 0 ) {
        		return FormValidation.error("Usage: Value " + this.getParameterizedValue(value) + " needs to be greater than 0 minutes");
        	} else if ( Integer.valueOf(value) >= 120 ) {
        		return FormValidation.error("Usage: Value " + this.getParameterizedValue(value) + " needs to be less than 120 minutes");
        	}
            return FormValidation.ok();
        }
        
        public FormValidation doSocketTimeout(@QueryParameter String value)
                throws IOException, ServletException {
        	if (value == null || value.length() == 0) {
                return FormValidation.error("Usage: Needs to be a number greater than 0 milliseconds");
        	} else if ( this.isInteger(value) == false) {
                return FormValidation.error("Usage: Needs to be an Integer ");
        	} else if ( Integer.valueOf(value) <= 0 ) {
        		return FormValidation.error("Usage: Value " + this.getParameterizedValue(value) + " needs to be greater than 0 milliseconds");
        	} 
            return FormValidation.ok();
        }
        
        public FormValidation doConnectTimeout(@QueryParameter String value)
                throws IOException, ServletException {
        	if (value == null || value.length() == 0) {
                return FormValidation.error("Usage: Needs to be a number greater than 0 milliseconds");
        	} else if ( this.isInteger(value) == false) {
                return FormValidation.error("Usage: Needs to be an Integer ");
        	} else if ( Integer.valueOf(value) <= 0 ) {
        		return FormValidation.error("Usage: Value " + this.getParameterizedValue(value) + " needs to be greater than 0 milliseconds");
        	} 
            return FormValidation.ok();
        }
        
        public FormValidation doConnectionRequestTimeout(@QueryParameter String value)
                throws IOException, ServletException {
        	if (value == null || value.length() == 0) {
                return FormValidation.error("Usage: Needs to be a number greater than 0 milliseconds");
        	} else if ( this.isInteger(value) == false) {
                return FormValidation.error("Usage: Needs to be an Integer ");
        	} else if ( Integer.valueOf(value) <= 0 ) {
        		return FormValidation.error("Usage: Value " + this.getParameterizedValue(value) + " needs to be greater than 0 milliseconds");
        	} 
            return FormValidation.ok();
        }
               
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Kitchen Full Stack Build Request";
        }
        
        
        
        
    
    }
}
