package org.jenkinsci.plugins.JUCBeer;

/*
 * 
 * http://github.com/christ66/demo-plugin.git
 */



import hudson.Launcher;
import hudson.Extension;
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

public class JenkinsRootAction {
	public String getIconFileName() {
		return "path-to-some-png";
	}

	public String getDisplayName() {
		return "Jenkins Home Page";
	}

	public String getUrlName() {
		return "http://localhost:8080";
	}

}

