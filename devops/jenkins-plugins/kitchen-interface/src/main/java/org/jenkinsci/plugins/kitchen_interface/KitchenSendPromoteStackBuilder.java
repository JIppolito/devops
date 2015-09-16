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
import org.kohsuke.stapler.QueryParameter;
import org.springframework.util.StringUtils;

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

import org.jenkinsci.plugins.utils.RESTUtils;
import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/* TODO:
 * 
 * 0.2.0
 * DONE Full Stack Deployment Plugin succeeded in calling a stack type (kitchen)
 * - Test on a Stack Type that will complete
 * - Test DNS Entries method 
 * - If DNS Entries is null should the build be set to UNSTABLE? FAILURE? COMPLETE? 
 * 
 * 0.2.1
 * - Move httpPost and httpGet into a generic utils class. 
 * - Have variables to set various timeouts for CustomHTTPClient for httpGet and httpPost as 
 * part of Kitchen URL options:
 * 	.setSocketTimeout(30000)
	.setConnectTimeout(30000)
	.setConnectionRequestTimeout(30000)
	.setStaleConnectionCheckEnabled(true)
	
 * 0.3.0
 * Builder takes an environment variable and an group knife id as an input
 * 
 * 
 */


public class KitchenSendPromoteStackBuilder extends Builder {

    private final String kitchenURL;
	private final String kitchenid;
	private final String environment;
	
	//TODO: Remove these two variables and place in config file that holds kitchen endpoints and response params
	private String status_code = "status_code";
	private String msg = "msg";
	
	private final int socketTimeout;
	private final int connectTimeout;
	private final int connectionRequestTimeout;
	private final boolean staleConnectionCheckEnabled;
	
    public String getKitchenURL() { return kitchenURL; }
	public String getKitchen_id() { return kitchenid; }
	public String getEnvironment() { return environment; }

	public int getSocketTimeout() { return socketTimeout; }
	public int getConnectTimeout() { return connectTimeout; }
	public int getConnectionRequestTimeout() { return connectionRequestTimeout; }
	public boolean getStaleConnectionCheckEnabled() { return staleConnectionCheckEnabled; }
	
	// Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
	@DataBoundConstructor
    public KitchenSendPromoteStackBuilder(String kitchenURL, String kitchenid,  String environment, String socketTimeout, 
    		String connectTimeout, String connectionRequestTimeout, boolean staleConnectionCheckEnabled) {
		this.kitchenURL = kitchenURL;
		this.kitchenid = kitchenid;
		this.environment = environment;
	
		this.connectionRequestTimeout = Integer.valueOf(connectionRequestTimeout);
		this.connectTimeout = Integer.valueOf(connectTimeout);
		this.socketTimeout = Integer.valueOf(socketTimeout);
		this.staleConnectionCheckEnabled = staleConnectionCheckEnabled;
		
	}
	
	@Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
		try {

            HashMap<String,String> response = new HashMap<String,String>();
			response=this.sendKitchenStackPromoteRequest(listener, build);
			String status_code_str;
			String response_message;
			
			status_code_str = response.get(this.status_code);
			response_message = response.get(this.msg);
			
			//DEBUG
            listener.getLogger().println("Response Status Code: " + status_code_str); 
            listener.getLogger().println("Response Status Message: " + response_message); 
            
			List<ParameterValue> params = new ArrayList<ParameterValue>();
            params.add(new StringParameterValue(this.status_code, status_code_str));
            params.add(new StringParameterValue(this.msg, response_message));
            build.addAction(new ParametersAction(params));
            
			if (status_code_str.equals("200")) {
				build.setResult(Result.SUCCESS);
				return true;
			} else {
				build.setResult(Result.FAILURE);
				return false;
			}
              
           
		} catch (Exception e) {
            listener.getLogger().println(e.toString());
            return false;
		} 
	
	}
	
	public HashMap sendKitchenStackPromoteRequest(BuildListener listener, AbstractBuild build) throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException, IOException, InterruptedException {
		String url = this.kitchenURL + "/promote";
		JSONObject o = new JSONObject();


		List<NameValuePair> params = new ArrayList<NameValuePair>();  
        params.add(new BasicNameValuePair("kitchen_id", this.resolveParameterVariable("kitchen_id", build, listener)));
	    params.add(new BasicNameValuePair("environment", this.resolveParameterVariable("environment_single", build, listener)));
	    
	    //DEBUG
        listener.getLogger().println("HTTP Post Data: " + params.toString());
        
        RESTUtils utils = new RESTUtils(this.getSocketTimeout(), this.getConnectTimeout(), this.getConnectionRequestTimeout(), this.getStaleConnectionCheckEnabled());

    	o=utils.httpPost(params, url, listener);
    	if (o != null) {
    	    String status_code = "";
    	    String msg = "";
            HashMap<String,String> map = new HashMap<String,String>();
    		status_code = (String) o.get("status_code");
    		msg = (String) o.get("msg");
    		map.put("msg", msg);
    		map.put("status_code", status_code);
    		return map;
    	}
    	return null;
        
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
	
	
	   /**
     * Descriptor for {@link KitchenPromoteStackBuilder}. Used as a singleton.
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
         
       public FormValidation doCheckEnvironment(@QueryParameter String value)
    		   throws IOException, ServletException {
    	   if (value == null || value.length() == 0) {
    		   return FormValidation.error("Cannot be empty");
    	   } else if (isParameterValue(value)) {
    		   return FormValidation.warning("Ensure that " + this.getParameterizedValue(value) + " is a valid Parameterized String Input Variable for this build");
    	   } 
           	return FormValidation.ok();
       	}
        
       public FormValidation doCheckKitchenid(@QueryParameter String value)
    		   throws IOException, ServletException {
    	   if (value == null || value.length() == 0) {
    		   return FormValidation.error("Cannot be empty");
    	   } else if (isParameterValue(value)) {
    		   return FormValidation.warning("Ensure that " + this.getParameterizedValue(value) + " is a valid Parameterized String Input Variable for this build");
    	   } 
           	return FormValidation.ok();
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
        
    	public boolean isApplicable(Class<? extends AbstractProject> aClass) {
    		return true;
    	}

    	/**
    	 * This human readable name is used in the configuration screen.
    	 */
    	public String getDisplayName() {
    		return "Kitchen Promote Stack Request";
    	}
    
    }
}
