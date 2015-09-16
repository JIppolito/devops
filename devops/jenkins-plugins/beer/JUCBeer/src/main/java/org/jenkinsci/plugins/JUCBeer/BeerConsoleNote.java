package org.jenkinsci.plugins.JUCBeer;

import hudson.console.ConsoleAnnotator;
import hudson.console.ConsoleNote;
import hudson.Launcher;
import hudson.Extension;
import hudson.MarkupText;
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

public class BeerConsoleNote extends ConsoleNote {

	@Override
	public ConsoleAnnotator annotate(Object context, MarkupText text,
			int charPos) {
		
		return null;
	}
	
}
