package org.xnap.commons.maven.gettext;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.File;
import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamConsumer;

/**
 * Generates ressource bundles.
 *
 * @goal dist
 * @phase generate-resources
 * @author Tammo van Lessen
 */
public class DistMojo
    extends AbstractGettextMojo {

    /**
     * @description msgcat command.
     * @parameter expression="${msgcatCmd}" default-value="msgcat"
     * @required 
     */
    protected String msgcatCmd;
    
    /**
     * @description msgfmt command.
     * @parameter expression="${msgfmtCmd}" default-value="msgfmt"
     * @required 
     */
    protected String msgfmtCmd;
    
    /**
     * @description target package.
     * @parameter expression="${targetBundle}"
     * @required 
     */
    protected String targetBundle;
    
    /**
     * @description Output format ("class" or "properties")
     * @parameter expression="${outputFormat}" default-value="class"
     * @required 
     */
    protected String outputFormat;
    
    /**
     * Java version.
     * Can be "1" or "2".
     * @parameter expression="${javaVersion}" default-value="2"
     * @required
     */
    protected String javaVersion;

    /**
     * @parameter expression="${sourceLocale}" default-value="en"
     * @required
     */
    protected String sourceLocale;

    public void execute()
        throws MojoExecutionException {
    	
    	CommandlineFactory cf = null;
		if ("class".equals(outputFormat)) {
			cf = new MsgFmtCommandlineFactory();
		} else if ("properties".equals(outputFormat)) {
			cf = new MsgCatCommandlineFactory();
		} else 	
			throw new MojoExecutionException("Unknown output format: " 
					+ outputFormat + ". Should be 'class' or 'properties'.");

		DirectoryScanner ds = new DirectoryScanner();
    	ds.setBasedir(poDirectory);
    	ds.setIncludes(new String[] {"**/*.po"});
    	ds.scan();
    	
    	String[] files = ds.getIncludedFiles();
    	for (int i = 0; i < files.length; i++) {
    		getLog().info("Processing " + files[i]);
    		
    		File inputFile = new File(poDirectory, files[i]);
    		File outputFile = cf.getOutputFile(inputFile);
    		
    		if (!isNewer(inputFile, outputFile)) {
    			getLog().info("Not compiling, target is up-to-date: " + outputFile);
    			continue;
    		}
    		
        	Commandline cl = cf.createCommandline(inputFile);
    		getLog().debug("Executing: " + cl.toString());
    		StreamConsumer out = new LoggerStreamConsumer(getLog(), LoggerStreamConsumer.INFO);
    		StreamConsumer err = new LoggerStreamConsumer(getLog(), LoggerStreamConsumer.WARN);
        	try {
    			CommandLineUtils.executeCommandLine(cl, out, err);
    		} catch (CommandLineException e) {
    			getLog().error("Could not execute " + cl.getExecutable() + ".", e);
    		}
    	}
    	
    	String basepath = targetBundle.replace('.', File.separatorChar);
    	getLog().info("Creating resource bundle for source locale");
    	touch(new File(outputDirectory, basepath + "_" + sourceLocale + ".properties"));
    	getLog().info("Creating default resource bundle");
    	touch(new File(outputDirectory, basepath + ".properties"));
    }
    	
    private boolean isNewer(File inputFile, File outputFile) {
		return inputFile.lastModified() > outputFile.lastModified();
	}

	private void touch(File file) {
    	if (!file.exists()) {
    		File parent = file.getParentFile();
    		if (!parent.exists()) {
    			parent.mkdirs();
    		}
    		try {
				file.createNewFile();
			} catch (IOException e) {
				getLog().warn("Could not touch file: " + file.getName(), e);
			}
    	}
    }
    
    private interface CommandlineFactory {
    	Commandline createCommandline(File file);
    	/**
    	 * @return the output file of this command
    	 */
    	File getOutputFile(File input);
    }
    
    private class MsgFmtCommandlineFactory implements CommandlineFactory {
    	
    	@Override
    	public File getOutputFile(File input) {
    		String locale = getLocale(input);
    		return new File(outputDirectory, targetBundle.replace('.', File.separatorChar) + "_" + locale + ".class"); 
    	}
    	
    	private String getLocale(File file) {
    		String locale = file.getName().substring(0, file.getName().lastIndexOf('.'));
    		return GettextUtils.getJavaLocale(locale);
    	}
    	
        public Commandline createCommandline(File file) {
        	Commandline cl = new Commandline();
        	cl.setExecutable(msgfmtCmd);
        	
        	if ("2".equals(javaVersion)) {
        		cl.createArgument().setValue("--java2");
        	} else {
        		cl.createArgument().setValue("--java");
        	}
        	
        	cl.createArgument().setValue("-d");
        	cl.createArgument().setFile(outputDirectory);
        	cl.createArgument().setValue("-r");
        	cl.createArgument().setValue(targetBundle);
        	cl.createArgument().setValue("-l");
        	cl.createArgument().setValue(getLocale(file));
        	cl.createArgument().setFile(file);
        	getLog().warn(cl.toString());
        	return cl;
        }
    }

    private class MsgCatCommandlineFactory implements CommandlineFactory {
    	
    	@Override
    	public File getOutputFile(File input) {
    		String basepath = targetBundle.replace('.', File.separatorChar);
    		String locale = input.getName().substring(0, input.getName().lastIndexOf('.'));
    		locale = GettextUtils.getJavaLocale(locale);
        	File target = new File(outputDirectory, basepath + "_" + locale + ".properties");
        	return target;
    	}
    	
    	public Commandline createCommandline(File file) {
        	Commandline cl = new Commandline();
        	
        	File outputFile = getOutputFile(file);
        	File parent = outputFile.getParentFile();
        	if (!parent.exists()) {
        		parent.mkdirs();
        	}
       	
        	cl.setExecutable(msgcatCmd);
       	
        	cl.createArgument().setValue("--no-location");
        	cl.createArgument().setValue("-p");
        	cl.createArgument().setFile(file);
        	cl.createArgument().setValue("-o");
        	cl.createArgument().setFile(outputFile);

        	return cl;
        }
     }
    
}
