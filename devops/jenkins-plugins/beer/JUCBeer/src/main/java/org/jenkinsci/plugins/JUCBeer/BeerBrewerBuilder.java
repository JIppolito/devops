package org.jenkinsci.plugins.JUCBeer;

import hudson.Launcher;
import hudson.Extension;
import hudson.util.ArgumentListBuilder;
import hudson.util.FormValidation;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.AbstractProject;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.IOException;


@Extension
public class BeerBrewerBuilder extends Builder{
	
	public final String name;
	
    @DataBoundConstructor
	public BeerBrewerBuilder(String name) {
    	this.name=name;
    }
	
	@Override
	public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) 
			throws IOException, InterruptedException  {
		
		listener.getLogger().println("Hello World");
		
		ArgumentListBuilder argListBldr = new ArgumentListBuilder();
		argListBldr.add("sleep", "10");
		int join = launcher.launch().cmds(argListBldr).join();
		
		if (join !=0 ) {
			listener.getLogger().println("Issue with build step. Exited with " + join);
			return false;
		}
		
		return true;
		
	}
	
	@Extension
	public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return true;
		}

		@Override
		public String getDisplayName() {
			return "Beer Brewer Builder";
		}
	    	
	}
}
