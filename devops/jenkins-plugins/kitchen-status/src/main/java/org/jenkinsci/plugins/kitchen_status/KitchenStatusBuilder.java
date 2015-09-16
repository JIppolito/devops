package org.jenkinsci.plugins.kitchen_status;
import hudson.Launcher;
import hudson.Extension;
import hudson.util.FormValidation;
import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.EnvironmentContributingAction;
import hudson.model.ParameterValue;
import hudson.model.AbstractProject;
import hudson.model.ParametersAction;
import hudson.model.Run;
import hudson.model.StringParameterValue;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import hudson.EnvVars;
//import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.jelly.ThisTagLibrary;
import hudson.model.Result;

import javax.servlet.ServletException;
import java.io.*;
import java.net.* ;
import java.util.*;

import org.apache.http.Consts;  
import org.apache.http.HttpEntity;  
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;  
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;  
import org.apache.http.client.methods.CloseableHttpResponse;  
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;  
import org.apache.http.client.utils.URIBuilder;
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
 * 0.1.0:
 * Ensure EnvParams are being written to correctly (ip_address, kitchen_request_id)
 * Add wait time for a build into UI as an option
 * Migrate sendKitchenBuildRequest code to use httpPost method
 * Implement Custom HttpClient Code for httpGet and httpPost methods
 * 
 * 0.1.1:
 * Develop Full Stack Option(s)
 * 
 */


/**
 * Sample {@link Builder}.
 *
 * <p>
 * When the user configures the project and enables this builder,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked
 * and a new {@link KitchenStatusBuilder} is created. The created
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
public class KitchenStatusBuilder extends Builder {
	
	private final String FAILED = "failed";
	private final String QUEUED = "queued";
	private final String COMPLETE = "complete";
	//TODO:  Add wait time for a build into UI as an option
	private final long MAXTIME = 1800000;
	
    private final String kitchenURL;
    private final String role;
    private final String branch;
    private final String ami;
    private final String bootstrap;
    private final String subnet;
    private final String type;
    private final String security;
    private final String kitchenEnvironment;
    private final String cloudTag;
    private final boolean us_east_1a;
    private final boolean us_east_1b;
    private final boolean us_east_1c;
    private final boolean us_east_1d;
    
    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public KitchenStatusBuilder(String kitchenURL, String role, String branch, String ami, String bootstrap, String subnet, String type, String security, String kitchenEnvironment, String cloudTag, boolean us_east_1a, boolean us_east_1b, boolean us_east_1c, boolean us_east_1d) {
    	this.kitchenURL = kitchenURL;
        this.role=role;
        this.branch=branch;
        this.ami=ami;
        this.bootstrap=bootstrap;
        this.subnet=subnet;
        this.type=type;
        this.security=security;
        this.kitchenEnvironment=kitchenEnvironment;       
        this.us_east_1a = us_east_1a;
        this.us_east_1b = us_east_1b;
        this.us_east_1c = us_east_1c;
        this.us_east_1d = us_east_1d;
        this.cloudTag = cloudTag;
    }

    /**
     * We'll use this from the <tt>config.jelly</tt>.
     */
	public String getKitchenURL(){ return kitchenURL; }   
    public String getRole() { return role; }
	public String getBranch() { return branch; }
	public String getAmi() { return ami; }
	public String getBootstrap() { return bootstrap; }
	public String getSubnet() { return subnet; }
	public String getType() { return type; }
	public String getSecurity() { return security; }
	public String getKitchenEnvironment() { return kitchenEnvironment; }
	public String getCloudTag() { return cloudTag; }
	public boolean getUs_east_1a() { return us_east_1a; }
	public boolean getUs_east_1b() { return us_east_1b; }
	public boolean getUs_east_1c() { return us_east_1c; }
	public boolean getUs_east_1d() { return us_east_1d; }
		
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
			kitchenid=this.sendKitchenBuildRequest(listener);
			
			//DEBUG
            listener.getLogger().println("kitchen_request_id: " + kitchenid);
                 
            List<ParameterValue> params = new ArrayList<ParameterValue>();
            params.add(new StringParameterValue("kitchen_request_id", kitchenid)); 
            build.addAction(new ParametersAction(params));
           

            
		} catch (IOException e) {
            listener.getLogger().println(e.toString());
            return false;
		} 
		
		if (kitchenid == null) {
	        listener.getLogger().println("ERROR: Failed to get an ID from Kitchen");
	        build.setResult(Result.FAILURE);
	        return false;
			
		}

		//parse response: {"request_type":"group","request_id":"5391e8b409228d3e7e000468","status":"queued"}
		//TODO type should be a parameter as stack will be another option
		String type="group";
		String response = null;
		long end_time =  System.currentTimeMillis() + this.MAXTIME;
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

        	listener.getLogger().println("ERROR: Build failed to build correctly or did not get a response from Kitchen: " + response);      
        	
			// Attempt to get IP_ADDRESS as sometimes kitchen fails but still builds the instance 
			// in which case Jenkins should still try to kill the instance
        	try {
				ip_address=this.sendKitchenIpRequest(kitchenid, type, listener);
	        	listener.getLogger().println("INFO: Attempt to get IP_ADDRESS as sometimes kitchen fails but still builds the instance: " + ip_address);   
	        	//Does Work
				List<ParameterValue> params1 = new ArrayList<ParameterValue>();
	            params1.add(new StringParameterValue("ip_address", ip_address));
	            build.addAction(new ParametersAction(params1));
	            build.setResult(Result.UNSTABLE);
	            
			} catch (IOException  e) {
				build.setResult(Result.FAILURE);
				listener.getLogger().println("ERROR: Failed to get IP_ADDRESS from Kitchen: " + e.toString());
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
				listener.getLogger().println("ERROR: Failed to get IP_ADDRESS from Kitchen: " + e.toString());
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
        	listener.getLogger().println("ERROR: Unsure why build did not complete correctly.  Kitchen Response: " + response);
	        return true;

		}
		
		//DEBUG
        EnvVars envVars;
		try {
			envVars = build.getEnvironment(listener);
	        for (Map.Entry<String, String> entry: envVars.entrySet()) { 
	        	listener.getLogger().println(entry.getKey() + ", " + entry.getValue()); 
	        }
		} catch (IOException  e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		return true;
	
    }
    
	public String sendKitchenBuildRequest(BuildListener listener) throws IOException {
	//role=ids-api&environment_single=stg&branch=master&ami=ami-a9f5bfc0&bootstrap=ubuntu-12.04&zone=us-east-1a&subnet=subnet-22296e0a&type=c1.medium&security=sg-24ea2b41&qty=1&cloud_tag_single=master_jenkins_164
		
		URL url;
		try {
			url = new URL(this.kitchenURL + "/node_deploy_post");
		} catch (MalformedURLException e) {
            listener.getLogger().println(e.toString());
			return null;
		}
		
		//Implement this instead of url as url conneciton returns a webpage not json
		
		// Request parameters and other properties.
		List<NameValuePair> params = new ArrayList<NameValuePair>();  
		params.add(new BasicNameValuePair("role", this.role));
        params.add(new BasicNameValuePair("environment_single", this.kitchenEnvironment));
        params.add(new BasicNameValuePair("branch", this.branch));
        params.add(new BasicNameValuePair("ami", this.ami));
        params.add(new BasicNameValuePair("bootstrap", this.bootstrap));
        params.add(new BasicNameValuePair("zone", this.getZone()));
        params.add(new BasicNameValuePair("subnet", this.subnet));
        params.add(new BasicNameValuePair("type", this.type));
        params.add(new BasicNameValuePair("security", this.security));
        params.add(new BasicNameValuePair("qty", "1"));
        params.add(new BasicNameValuePair("cloud_tag_single", this.cloudTag));
        
        //DEBUG
        //listener.getLogger().println("HTTP Post Data: " + params.toString());
        
        //TODO: send to httpGet private method and get Json Object back to parse
    	// httpPost(params, url)
        
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
		        listener.getLogger().println("HTTP Post Response Content: " + content);

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
	}
	
    
    //Method for ip_addresses: curl -H Accept:application/json http://kitchen.devops.enernoc.net/ip_addresses?id=5392211609228d4d9500000b&type=group
    public String sendKitchenIpRequest(String kitchenId, String type, BuildListener listener) throws IOException {
    	JSONObject o;
        String it = new String();
    	String ip = "";
    	
    	URL url = new URL(this.kitchenURL + "/ip_addresses");
    	List<NameValuePair> params = new ArrayList<NameValuePair>();  
		params.add(new BasicNameValuePair("type", type));
		params.add(new BasicNameValuePair("id", kitchenId));
    	o=this.httpGet(params, url, listener);   	
        listener.getLogger().println(o.toString());
        
    	JSONArray jsonArry = (JSONArray) o.get("ip_addresses");
    	Iterator iterator = jsonArry.iterator();
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
    	List<NameValuePair> params = new ArrayList<NameValuePair>();  
		params.add(new BasicNameValuePair("type", type));
		params.add(new BasicNameValuePair("id", kitchenId));
    	o=this.httpGet(params, url, listener);
    	id = (String) o.get("status");
    	return id;
    }
          
   public String getZone() {
	   if (this.us_east_1b) { return "us-east-1b"; }
	   if (this.us_east_1c) { return "us-east-1c"; }
	   if (this.us_east_1d) { return "us-east-1d"; }
	   return "us-east-1a";	   
   }
   

   
   //TODO: Test to ensure it works then clean up code.
   private JSONObject httpGet(List<NameValuePair> params, URL url, BuildListener listener) {
	   
	   JSONObject o = null;
       CloseableHttpClient httpclient = HttpClients.createDefault(); 
       
		/*TODO:  Set the timeout by calling HttpClients.custom()
		 .setSocketTimeout(5000)
		 .setConnectTimeout(5000)
		 .setConnectionRequestTimeout(5000)
		 .setStaleConnectionCheckEnabled(true)
		 .build();
		 */
       
	   String paramsString = URLEncodedUtils.format(params, "UTF-8");  
	   HttpGet httpGet = new HttpGet(url.toString() + "?" + paramsString);
	   
	   try {
		   CloseableHttpResponse response = httpclient.execute(httpGet);
		   HttpEntity respEntity = response.getEntity();
		   if (respEntity != null) {
		        // EntityUtils to get the response content
		        String content =  EntityUtils.toString(respEntity);
		        //listener.getLogger().println("respEntity String: " + content);
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
   
   //TODO: Test to ensure it works then clean up code.
   private JSONObject httpPost(List<NameValuePair> params, URL url, BuildListener listener) {
	   JSONObject o = null;
		try {
			CloseableHttpClient httpclient = HttpClients.createDefault();  
			
			/*TODO:  Set the timeout by calling HttpClients.custom()
			 .setSocketTimeout(5000)
			 .setConnectTimeout(5000)
			 .setConnectionRequestTimeout(5000)
			 .setStaleConnectionCheckEnabled(true)
			 .build();
			 */
			
			HttpPost httpPost = new HttpPost(url.toString());
			httpPost.setEntity(new UrlEncodedFormEntity(params, Consts.UTF_8));  
	        CloseableHttpResponse response = httpclient.execute(httpPost);  
		    HttpEntity respEntity = response.getEntity();

		    if (respEntity != null) {
		        // EntityUtils to get the response content
		        String content =  EntityUtils.toString(respEntity);
		        JSONParser j = new JSONParser();
			    o = (JSONObject)j.parse(content);
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
        /**
         * To persist global configuration information,
         * simply store it in a field and call save().
         *
         * <p>
         * If you don't want fields to be persisted, use <tt>transient</tt>.
         */
        //private boolean useFrench;

        /**
         * In order to load the persisted global configuration, you have to 
         * call load() in the constructor.
         */
        public DescriptorImpl() {
            load();
        }

        /**
         * Performs on-the-fly validation of the form field 'name'.
         *
         * @param value
         *      This parameter receives the value that the user has typed.
         * @return
         *      Indicates the outcome of the validation. This is sent to the browser.
         *      <p>
         *      Note that returning {@link FormValidation#error(String)} does not
         *      prevent the form from being saved. It just means that a message
         *      will be displayed to the user. 
         */
        public FormValidation doCheckName(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set a name");
            if (value.length() < 4)
                return FormValidation.warning("Isn't the name too short?");
            return FormValidation.ok();
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types 
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Kitchen Send Single Build Instance Request";
        }

        /**
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            // To persist global configuration information,
            // set that to properties and call save().
            useFrench = ((Object) formData).getBoolean("useFrench");
            // ^Can also use req.bindJSON(this, formData);
            //  (easier when there are many fields; need set* methods for this, like setUseFrench)
            save();
            return super.configure(req,formData);
        }


         * This method returns true if the global configuration says we should speak French.
         *
         * The method name is bit awkward because global.jelly calls this method to determine
         * the initial state of the checkbox by the naming convention.
       
        public boolean getUseFrench() {
            return useFrench;
        }    
        */       
        
    }
}

