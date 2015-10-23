package org.jenkinsci.plugins.kitchen_interface;
import hudson.Launcher;
import hudson.Extension;
import hudson.util.FormValidation;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.ParameterValue;
import hudson.model.AbstractProject;
import hudson.model.ParametersAction;
import hudson.model.StringParameterValue;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import hudson.EnvVars;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;
import hudson.model.Result;

import javax.servlet.ServletException;
import java.io.*;
import java.net.* ;
import java.util.*;
import java.lang.reflect.*;
import java.net.URL;

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

import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;


/*
 * TODO:
 * 
 * 0.1.0:
 * RELEASE CANDIDATE
 * Add Help Content
 * 
 * 0.1.1:
 * - DONE: Add checks to ensure if param input begins with # that it can read the variable and that the environment param exists.
 * - DONE: Add functionality in sendKitchenSingleBuildRequest so that if varible begins with # it gets the param else use literal string
 * - DONE: Fix Avail Zones UI. Since Subnet and Avail zones are dependent on each other, the Avail Zones should be
 * a text input that validates us-east-1x naming convention. 
 * - DONE: Add MAX Wait Time for a build into UI as an option
 * - DONE: Migrate sendKitchenBuildRequest code to use httpPost method
 * - DONE: Implement Custom HttpClient Code for httpGet and httpPost methods
 * - DONE: Add Help:
 * 		All inputs must be either literal string OR parameterized input but not a combination
 * 
 *  * 0.2.1
 * - Move httpPost and httpGet into a generic utils class. 
 * - Have variables to set various timeouts for CustomHTTPClient for httpGet and httpPost as 
 * part of Kitchen URL options:
 * 	.setSocketTimeout(30000)
	.setConnectTimeout(30000)
	.setConnectionRequestTimeout(30000)
	.setStaleConnectionCheckEnabled(true)
	
	0.3.0 
	Add functionality to terminate job if cancellation button is pressed.
 * 	
 */

/**
 * Sample {@link Builder}.
 *
 * <p>
 * When the user configures the project and enables this builder,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked
 * and a new {@link KitchenSendSingleInstanceBuilder} is created. The created
 * instance is persisted to the project configuration XML by using
 * XStream, so this allows you to use instance fields (like {@link #name})
 * to remember the configuration.
 *
 * <p>
 * When a build is performed, the {@link #perform(AbstractBuild, Launcher, BuildListener)}
 * method will be invoked. 
 *
 * @author Jeff Ippolito
 */
public class KitchenSendSingleInstanceBuilder extends Builder {
	
	private final String FAILED = "failed";
	private final String QUEUED = "queued";
	private final String COMPLETE = "complete";
	private final String KITCHEN_REQUEST_TYPE = "group";
	
	private final int max_timeout;	
    private final String kitchenURL;
    private final String role;
    private final String branch;
    private final String ami;
    private final String bootstrap;
    private final String subnet;
    private final String type;
    private final String security;
    private final String chef_environment;
    private final String cloud_tag_single;
    private final String avail_Zones;
    
	private final int socketTimeout;
	private final int connectTimeout;
	private final int connectionRequestTimeout;
	private final boolean staleConnectionCheckEnabled;   
    
    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public KitchenSendSingleInstanceBuilder(String kitchenURL, String role, String branch, String ami, String bootstrap, 
    		String subnet, String type, String security, String chef_environment, 
    		String cloud_tag_single, String avail_Zones, String max_timeout, String socketTimeout, String connectTimeout, 
    		String connectionRequestTimeout, boolean staleConnectionCheckEnabled ) {
    	
    	this.kitchenURL = kitchenURL;
        this.role=role;
        this.branch=branch;
        this.ami=ami;
        this.bootstrap=bootstrap;
        this.subnet=subnet;
        this.type=type;
        this.security=security;
        this.chef_environment=chef_environment;      
        this.cloud_tag_single = cloud_tag_single;
        this.avail_Zones = avail_Zones;
        this.max_timeout = Integer.parseInt(max_timeout);
        
		this.connectionRequestTimeout = Integer.valueOf(connectionRequestTimeout);
		this.connectTimeout = Integer.valueOf(connectTimeout);
		this.socketTimeout = Integer.valueOf(socketTimeout);
		this.staleConnectionCheckEnabled = staleConnectionCheckEnabled;
    }

    /**
     * We'll use this from the config.jelly.
     */
	public String getKitchenURL(){ return kitchenURL; }   
    public String getRole() { return role; }
	public String getBranch() { return branch; }
	public String getAmi() { return ami; }
	public String getBootstrap() { return bootstrap; }
	public String getSubnet() { return subnet; }
	public String getType() { return type; }
	public String getSecurity() { return security; }
	public String getChef_environment() { return chef_environment; }
	public String getCloud_tag_single() { return cloud_tag_single; }
	public String getAvail_Zones() { return avail_Zones; }
	public int getMax_timeout() { return this.max_timeout; }
		
	public int getSocketTimeout() { return socketTimeout; }
	public int getConnectTimeout() { return connectTimeout; }
	public int getConnectionRequestTimeout() { return connectionRequestTimeout; }
	public boolean getStaleConnectionCheckEnabled() { return staleConnectionCheckEnabled; }
	
	 /*
     * Set Kitchen URL and Creds
     * Send Kitchen Single Instance POST
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
     *  	mark build stable
     *  elif status is failed or null
     *   	mark build failed
     *  else
     *  	mark build unstable    
     * 
     */
	
	@Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
		//parse response {"request_id":"535836bc09228d2676000186"}
		String kitchenid = "";
		
		try {
			kitchenid=this.sendKitchenSingleBuildRequest(listener, build);
			
			//DEBUG
            listener.getLogger().println("kitchen_request_id: " + kitchenid);
                 
            List<ParameterValue> params = new ArrayList<ParameterValue>();
            params.add(new StringParameterValue("kitchen_request_id", kitchenid)); 
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
	            listener.getLogger().println("Now " + Long.toString(now) + " < " + Long.toString(end_time));
	            
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
		if (response == null || response.equals(this.FAILED)) {
			
        	listener.getLogger().println("ERROR: Build failed to build correctly or did not get a response from Kitchen. Please check Kitchen server for a more detailed error message: " + response);      
        	
			// Attempt to get IP_ADDRESS as sometimes kitchen fails but still builds the instance 
			// in which case Jenkins should still try to kill the instance
        	try {
				ip_address=this.sendKitchenIpRequest(kitchenid, type, listener);
	        	listener.getLogger().println("INFO: Attempt to get IP_ADDRESS as sometimes kitchen fails but still builds the instance. Please check Kitchen server for a more detailed error message: " + ip_address);   
	        	//Does Work
				List<ParameterValue> params1 = new ArrayList<ParameterValue>();
	            params1.add(new StringParameterValue("ip_address", ip_address));
	            build.addAction(new ParametersAction(params1));
	            if (ip_address == null || ip_address.length() == 0) {
		            build.setResult(Result.FAILURE);
	            } else {
		            build.setResult(Result.UNSTABLE);
	            }
	   
			} catch (IOException  e) {
				build.setResult(Result.FAILURE);
				listener.getLogger().println("ERROR: Failed to get IP_ADDRESS from Kitchen. Please check Kitchen server for a more detailed error message: " + e.toString());
				return false;	
			}

		} else if (response.equals(this.COMPLETE)) {
			try {
				ip_address=this.sendKitchenIpRequest(kitchenid, type, listener);
	        	//Does Work
				List<ParameterValue> params2 = new ArrayList<ParameterValue>();
	            params2.add(new StringParameterValue("ip_address", ip_address));
	            build.addAction(new ParametersAction(params2));
	           
			} catch (IOException e) {
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
	        	//Does Work
				List<ParameterValue> params3 = new ArrayList<ParameterValue>();
	            params3.add(new StringParameterValue("ip_address", ip_address));
	            build.addAction(new ParametersAction(params3));
				
			} catch (IOException e) {
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
			listener.getLogger().println("EnvVars: " + envVars.get(name)); 
			
			String value = envVars.get(name);
			return value;
			//This sets the value of the field of the current object which will be saved to the builder
			//and will overwrite the variable ${myvariable}
			//field.set(this, value);
		}	    
	    return field.get(this).toString();
	}
	
    /*
     * Incrementing a field by reflection
     * public int incrementField(String name, Object obj) throws... {
    		Field field = obj.getClass().getDeclaredField(name);
    		int value = field.getInt(obj) + 1;
    		field.setInt(obj, value);
    		return value;
		}
		Replace all instances of object variables that begin with $ with what is in the build environment
		IF the variable is NOT of type Final AND the value is not null
		!!!Problem is that it actually writes the value to the builder and overwrites ${myvar} the next time it runs!!!
     * 

	private void resolveParameterVariables(AbstractBuild build, BuildListener listener) 
			throws IOException, InterruptedException, IllegalArgumentException, IllegalAccessException {
		Field[] fields = this.getClass().getDeclaredFields();
		EnvVars envVars = build.getEnvironment(listener);
		for (Field field : fields) {
        	listener.getLogger().println("Fields: " + field); 
			if (field.getType().equals(String.class) && field.get(this).toString().startsWith("$")) {
				//DEBUG
				listener.getLogger().println("Field: " + field.toString()); 
				listener.getLogger().println("Field Type: " + field.getType()); 
				String name = field.getName();
				//DEBUG
				listener.getLogger().println("EnvVars: " + envVars.get(name)); 
				String value = envVars.get(name);
				field.set(this, value);
			}
		}
	}
	*/
	
	public String sendKitchenSingleBuildRequest(BuildListener listener, AbstractBuild build) throws IOException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException, InterruptedException {
	//role=ids-api&environment_single=stg&branch=master&ami=ami-a9f5bfc0&bootstrap=ubuntu-12.04&zone=us-east-1a&subnet=subnet-22296e0a&type=c1.medium&security=sg-24ea2b41&qty=1&cloud_tag_single=master_jenkins_164
		String url = this.kitchenURL + "/node_deploy_post";

		JSONObject o = new JSONObject();
	    String request_id = "";

		int buildNo = build.getNumber();
		String cloudTagTmp = this.resolveParameterVariable("cloud_tag_single", build, listener);
		String branch = this.resolveParameterVariable("branch", build, listener);
		branch = branch.replace("origin/", "");
		if (branch == null || branch.length() == 0) branch = "none";
		if (cloudTagTmp == null || cloudTagTmp.length() == 0) cloudTagTmp = "Jenkins";
		String cloudTag = branch + "_" + cloudTagTmp + "_" + String.valueOf(buildNo);
		
		// Request parameters and other properties.
		List<NameValuePair> params = new ArrayList<NameValuePair>();  
		params.add(new BasicNameValuePair("role", this.resolveParameterVariable("role", build, listener)));
	    params.add(new BasicNameValuePair("environment_single", this.resolveParameterVariable("chef_environment", build, listener)));
        params.add(new BasicNameValuePair("branch", branch));
        params.add(new BasicNameValuePair("ami", this.resolveParameterVariable("ami", build, listener)));
        params.add(new BasicNameValuePair("bootstrap", this.resolveParameterVariable("bootstrap", build, listener)));
        params.add(new BasicNameValuePair("zone", this.resolveParameterVariable("avail_Zones", build, listener)));
        params.add(new BasicNameValuePair("subnet", this.resolveParameterVariable("subnet", build, listener)));
        params.add(new BasicNameValuePair("type", this.resolveParameterVariable("type", build, listener)));
        params.add(new BasicNameValuePair("security", this.resolveParameterVariable("security", build, listener)));
        params.add(new BasicNameValuePair("qty", "1"));
        //params.add(new BasicNameValuePair("cloud_tag_single", this.resolveParameterVariable("cloud_tag_single", build, listener)));
        params.add(new BasicNameValuePair("cloud_tag_single", cloudTag));

        //DEBUG
        listener.getLogger().println("HTTP Post Data: " + params.toString());
        
    	o=this.httpPost(params, url, listener);
    	if (o != null) {
    		request_id = (String) o.get("request_id");
    		return request_id;
    	}
    	return null;
    	    	
    	/*
        CloseableHttpClient httpclient = HttpClients.createDefault();  
		HttpPost httpPost = new HttpPost(this.kitchenURL + "/node_deploy_post");
		httpPost.setEntity(new UrlEncodedFormEntity(params, Consts.UTF_8));  
		//httpPost.setHeader("Content-Type", "application/json");
		httpPost.setHeader("Accept", "application/json");
        CloseableHttpResponse response = httpclient.execute(httpPost);         
	    HttpEntity respEntity = response.getEntity();
	    String request_id = "";
	    
	    try {
		    if (respEntity != null) {
		        // EntityUtils to get the response content
		        String content =  EntityUtils.toString(respEntity);

		        //DEBUG
		        //listener.getLogger().println("HTTP Post Response Content: " + content);

		        JSONParser j = new JSONParser();
			    JSONObject o = (JSONObject) j.parse(content);
				//parse response {"request_id":"535836bc09228d2676000186"}
			    request_id = (String) o.get("request_id");
			    httpclient.close();
			    return request_id;
		    }
	    } catch (ParseException pe) {
	        listener.getLogger().println("Error: Content could not be parsed: " + EntityUtils.toString(respEntity));
	        listener.getLogger().println(pe.toString());
	        return null;
	    }
		    return null;
		    */
	}
	
	/*
	public String sendKitchenStackBuildRequest(BuildListener listener, AbstractBuild build) throws IOException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException, InterruptedException {
		String url = this.kitchenURL + "/stack_deploy_post";
		JSONObject o = new JSONObject();
	    String request_id = "";

		int buildNo = build.getNumber();
		String cloudTagTmp = this.resolveParameterVariable("cloud_tag_single", build, listener);
		String branch = this.resolveParameterVariable("branch", build, listener);
		branch = branch.replace("origin/", "");
		if (branch == null || branch.length() == 0) branch = "none";
		if (cloudTagTmp == null || cloudTagTmp.length() == 0) cloudTagTmp = "Jenkins";
		String cloudTag = branch + "_" + cloudTagTmp + "_" + String.valueOf(buildNo);
		
		// Request parameters and other properties.
		List<NameValuePair> params = new ArrayList<NameValuePair>();  
		params.add(new BasicNameValuePair("role", this.resolveParameterVariable("role", build, listener)));
	    params.add(new BasicNameValuePair("environment_single", this.resolveParameterVariable("environment_single", build, listener)));
        params.add(new BasicNameValuePair("branch", this.resolveParameterVariable("branch", build, listener)));
        params.add(new BasicNameValuePair("ami", this.resolveParameterVariable("ami", build, listener)));
        params.add(new BasicNameValuePair("bootstrap", this.resolveParameterVariable("bootstrap", build, listener)));
        params.add(new BasicNameValuePair("zone", this.resolveParameterVariable("avail_Zones", build, listener)));
        params.add(new BasicNameValuePair("subnet", this.resolveParameterVariable("subnet", build, listener)));
        params.add(new BasicNameValuePair("type", this.resolveParameterVariable("type", build, listener)));
        params.add(new BasicNameValuePair("security", this.resolveParameterVariable("security", build, listener)));
        params.add(new BasicNameValuePair("qty", "1"));
        //params.add(new BasicNameValuePair("cloud_tag_single", this.resolveParameterVariable("cloud_tag_single", build, listener)));
        params.add(new BasicNameValuePair("cloud_tag_single", cloudTag));

        //DEBUG
        listener.getLogger().println("HTTP Post Data: " + params.toString());
        
    	o=this.httpPost(params, url, listener);
    	if (o != null) {
    		request_id = (String) o.get("request_id");
    		return request_id;
    	}
    	return null;
	}
    */
	
    //Method for ip_addresses: curl -H Accept:application/json http://localhost/ip_addresses?id=5392211609228d4d9500000b&type=group
    public String sendKitchenIpRequest(String kitchenId, String type, BuildListener listener) throws IOException {
    	JSONObject o;
        String it = new String();
    	String ip = "";
    	
    	URL url = new URL(this.kitchenURL + "/ip_addresses");
    	List<NameValuePair> params = new ArrayList<NameValuePair>();  
		params.add(new BasicNameValuePair("type", type));
		params.add(new BasicNameValuePair("id", kitchenId));
    	o=this.httpGet(params, url, listener);   	
    	//DEBUG
        listener.getLogger().println("HTTP GET Response :" + o.toString());
        
    	JSONArray jsonArry = (JSONArray) o.get("ip_addresses");
    	Iterator iterator = jsonArry.iterator();
		while (iterator.hasNext()) {
	        it = (String) iterator.next();	        
			ip=ip.concat(it);
			if (iterator.hasNext()) ip=ip.concat(",");
		}		
		//DEBUG
        //listener.getLogger().println("ip_addresses:" + ip);

    	return ip;
    }
        
    public String sendKitchenStatusRequest(String kitchenId, String type, BuildListener listener) throws IOException {
    	URL url = new URL(this.kitchenURL + "/request_status");
    	JSONObject o;
    	String id = null;
    	List<NameValuePair> params = new ArrayList<NameValuePair>();  
		params.add(new BasicNameValuePair("type", type));
		params.add(new BasicNameValuePair("id", kitchenId));
    	o=this.httpGet(params, url, listener);
    	id = (String) o.get("status");
    	return id;
    }
          
   
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
			} catch (Exception e) {
				return FormValidation.error("Please set a valid URL");
			}
            
            return FormValidation.ok();
        }
        
        public FormValidation doCheckRole(@QueryParameter String value)
                throws IOException, ServletException {
        	if (value == null || value.length() == 0) {
                return FormValidation.error("Cannot be empty");
        	} else if (isParameterValue(value)) {
        		return FormValidation.warning("Ensure that " + this.getParameterizedValue(value) + " is a valid Parameterized String Input Variable for this build");
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
        
        public FormValidation doCheckAmi(@QueryParameter String value)
                throws IOException, ServletException {
        	if (value == null || value.length() == 0) {
                return FormValidation.error("Cannot be empty");
        	} else if (isParameterValue(value)) {
        		return FormValidation.warning("Ensure that " + this.getParameterizedValue(value) + " is a valid Parameterized String Input Variable for this build");
        	} 
            return FormValidation.ok();
        }
        
        public FormValidation doCheckBootstrap(@QueryParameter String value)
                throws IOException, ServletException {
        	if (value == null || value.length() == 0) {
                return FormValidation.error("Cannot be empty");
        	} else if (isParameterValue(value)) {
        		return FormValidation.warning("Ensure that " + this.getParameterizedValue(value) + " is a valid Parameterized String Input Variable for this build");
        	} 
            return FormValidation.ok();
        }
        
        public FormValidation doCheckSubnet(@QueryParameter String value)
                throws IOException, ServletException {
        	if (value == null || value.length() == 0) {
                return FormValidation.error("Cannot be empty");
        	} else if (isParameterValue(value)) {
        		return FormValidation.warning("Ensure that " + this.getParameterizedValue(value) + " is a valid Parameterized String Input Variable for this build");
        	} 
            return FormValidation.ok();
        }
        
        public FormValidation doCheckType(@QueryParameter String value)
                throws IOException, ServletException {
        	if (value == null || value.length() == 0) {
                return FormValidation.error("Cannot be empty");
        	} else if (isParameterValue(value)) {
        		return FormValidation.warning("Ensure that " + this.getParameterizedValue(value) + " is a valid Parameterized String Input Variable for this build");
        	} 
            return FormValidation.ok();
        }
        
        public FormValidation doCheckSecurity(@QueryParameter String value)
                throws IOException, ServletException {
        	if (value == null || value.length() == 0) {
                return FormValidation.error("Cannot be empty");
        	} else if (isParameterValue(value)) {
        		return FormValidation.warning("Ensure that " + this.getParameterizedValue(value) + " is a valid Parameterized String Input Variable for this build");
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
        
        //TODO:  Add help that will state that the instance name will be: role-branch_CloudTag_BUILD_NUMBER

        public FormValidation doCheckCloud_tag_single(@QueryParameter String value)
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
        		return FormValidation.warning("Usage: Ensure that " + this.getParameterizedValue(value) + " is a valid Parameterized String Input Variable for this build that equals { us-east-1a | us-east-1b | us-east-1c | us-east-1d }");
        	} else if (!(value.equals("us-east-1a") || value.equals("us-east-1b") || value.equals("us-east-1c") || value.equals("us-east-1d"))) {
        		return FormValidation.error("Usage: The " + value + " needs to be { us-east-1a | us-east-1b | us-east-1c | us-east-1d }");
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
            return "Kitchen Single Build Instance Request";
        }
                
    
        
    }
}

