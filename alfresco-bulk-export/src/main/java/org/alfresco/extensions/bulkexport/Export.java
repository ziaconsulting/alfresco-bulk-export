/**
 *  This file is part of Alfresco Bulk Export Tool.
 * 
 *  Alfresco Bulk Export Tool is free software: you can redistribute it 
 *  and/or modify it under the terms of the GNU General Public License as
 *  published by the Free Software Foundation, either version 3 of the 
 *  License, or (at your option) any later version.
 *
 *  Alfresco Bulk Export Tool  is distributed in the hope that it will be 
 *  useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General 
 *  Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along 
 *  with Alfresco Bulk Export Tool. If not, see <http://www.gnu.org/licenses/>.
 */
package org.alfresco.extensions.bulkexport;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.alfresco.extensions.bulkexport.controler.CacheGeneratedException;
import org.alfresco.extensions.bulkexport.controler.Engine;
import org.alfresco.extensions.bulkexport.dao.AlfrescoExportDao;
import org.alfresco.extensions.bulkexport.dao.AlfrescoExportDaoImpl;
import org.alfresco.extensions.bulkexport.model.FileFolder;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.util.Pair;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.extensions.webscripts.Cache;
import org.springframework.extensions.webscripts.DeclarativeWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;

/**
 * This class has a function to start the export process data contained in the repository.
 * 
 * @author Denys G. Santos (gsdenys@gmail.com)
 * @version 1.0.1
 */
/**
 * Updated the class to handle injecting aspects, custom properties,
 * export by from and to dates and updating model prefixes.
 * 
 * @author Vijay Prince (vijay.prince@gmail.com)
 */
public class Export extends DeclarativeWebScript
{
    Log log = LogFactory.getLog(Export.class);

    /** Alfresco {@link ServiceRegistry} populated by Spring Framework. */
    protected ServiceRegistry serviceRegistry;
    
    /** Data Access Object to Alfresco Repository. */
    protected AlfrescoExportDao dao;
    
    /** File and folder manager. */
    protected FileFolder fileFolder;
    
    /** Engine of system */
    protected Engine engine;
    
    protected ConcurrentHashMap<String, Engine> runningExports = new ConcurrentHashMap<String, Engine>();
    
    
    /**
     * Method to start program execution. 
     * 
     * @param req  The HTTP request parameter
     * @param res  The HTTP response parameter
     * @throws IOException
     */
    //public Map<String, Object> executeImpl(WebScriptRequest req, WebScriptResponse res) throws IOException 
	protected Map<String, Object> executeImpl(WebScriptRequest req, Status status, Cache cache)
    {
        log.debug("execute");
        
        StopWatch timer = new StopWatch();
        if(req.getParameterNames().length == 0 || (req.getParameterNames().length == 1 && null != req.getParameter("format"))) {
        	return this.getRunningExports();
        }
        String cancel = req.getParameter("cancel");
        if(null != cancel&& !cancel.isEmpty()) {
        	return this.cancelExport(cancel);
        }

        //get URL parameters
        String nodeRef = req.getParameter("nodeRef");
        String aspects = req.getParameter("aspects");
        String properties = req.getParameter("properties");
        String fromDate = req.getParameter("fromDate");
        String toDate = req.getParameter("toDate");
        String base = req.getParameter("base");
        String updateTypesOrAspectsOrProperties = req.getParameter("updateTypesOrAspectsOrProperties");
        String updateModelPrefix = req.getParameter("updateModelPrefix");
        
        boolean scapeExported = false;
        boolean exportVersions = false;
        boolean revisionHead = false;
        boolean useNodeCache = false;
        String jobId = UUID.randomUUID().toString();
        if(null != nodeRef && !nodeRef.isEmpty()) {
        	jobId = nodeRef;
        } else if(null != fromDate && !fromDate.isEmpty()) {
        	jobId = fromDate;
        }
        
        
        Map<String, Object > runningJob = this.isJobRunning(jobId);
        if (  runningJob.get(jobId) != null ) {
        	log.debug ( "Job " + jobId + " is already running");
        	return runningJob;
        }
        	
        if (req.getParameter("ignoreExported") != null)
        {
            if(req.getParameter("ignoreExported").equals("true")) 
            {
                scapeExported = true;
            }
        }

        // if a node has revisions, then export them as well
        if (req.getParameter("exportVersions") != null)
        {
            if(req.getParameter("exportVersions").equals("true")) 
            {
                exportVersions = true;
            }
        }

        // If this option is defined as true then all revisions are numbered
        // otherwise the bulk importer revisions are used (head is not named
        // with a revision)
        if (req.getParameter("revisionHead") != null)
        {
            if(req.getParameter("revisionHead").equals("true")) 
            {
                revisionHead = true;
            }
        }

        // If set to true then read a node.cache in the export directory as opposed to rescanning for nodes to export.
        // 
        if (req.getParameter("useNodeCache") != null)
        {
            if(req.getParameter("useNodeCache").equals("true")) 
            {
                useNodeCache = true;
            }
        }
        
        //init variables
       	StringBuilder sb = new StringBuilder();
        dao = new AlfrescoExportDaoImpl(this.serviceRegistry);
        fileFolder = new FileFolder(base, aspects, properties, updateTypesOrAspectsOrProperties, updateModelPrefix, scapeExported, serviceRegistry, sb);
        engine = new Engine(dao, fileFolder, exportVersions, revisionHead, useNodeCache);
        
        NodeRef nf = null;


        log.info("Bulk Export started");
       	Pair<Integer, Integer> exportCountsPair = null;

        try
        {
        	if(null != nodeRef && !nodeRef.isEmpty()) {
        		nf = dao.getNodeRef(nodeRef);
        	}
            runningExports.put(jobId, engine);
            log.info("Bulk Export started with jobId: "+jobId);
            /*
            ExecutorService pool = Executors.newCachedThreadPool();
            Callable<Boolean> callable = new ExportCallable(engine, nf);
            Future<Boolean> future = pool.submit(callable);
            */
            if(null != nf) {
            	exportCountsPair = engine.execute(nf, fromDate, toDate);
            }
        } 
        catch (CacheGeneratedException e)
        {
            sb.append("*****************************************************************************************************\n");
            sb.append("** No Export performed - Cache file generated only - re-run to use cache file\n");
            sb.append("*****************************************************************************************************\n");
            sb.append("Total nodes cached: "+e.getNodeCount()+"\n");
            sb.append("*****************************************************************************************************\n\n\n");
        }
        catch (Exception e) 
        {
            log.error("Error found during Export (Reason): " + e.toString() + "\n");
            e.printStackTrace();
            sb.append("*****************************************************************************************************\n");
            sb.append("** ERROR occured:\n");
            sb.append("** " + e.toString() + "\n");
            sb.append("*****************************************************************************************************\n\n\n");
        }
        
        if(engine.isCancelExport()) {
        	sb.append("*****************************************************************************************************\n");
        	sb.append("Export Cancelled\n");
        	sb.append("*****************************************************************************************************\n\n\n");
        } else {
        	sb.append("Export finished Successfully\n");
        }
        if(null != exportCountsPair) {
        	sb.append("Total Nodes to Export: "+exportCountsPair.getFirst()+"\n");
        	sb.append("Total Nodes Exported: "+exportCountsPair.getSecond()+"\n");
        } else {
        	sb.append("NO EXPORT DONE\n");
        }
       	sb.append("*****************************************************************************************************\n\n\n");

        //
        // writes will not appear until the script is finished, flush does not help
        //
        sb.append("Performed Export with the following Parameters :\n"); 
        sb.append("   export folder   : " + base + "\n");
        sb.append("   node to export  : " + nodeRef + "\n");
        sb.append("   From Date to export  : " + fromDate + "\n");
        sb.append("   To Date to export  : " + toDate + "\n");
        sb.append("   ignore exported : " + scapeExported + "\n");
        sb.append("   export versions : " + exportVersions + "\n");
        sb.append("   bulk import revision scheme: " + !revisionHead +"\n");
        sb.append("   Use Node Cache : " + useNodeCache + "\n");

        Duration duration = timer.elapsedDuration();
        DateTimeFormatter df = DateTimeFormatter.ISO_LOCAL_TIME;
        LocalTime nanoTime = LocalTime.ofNanoOfDay(duration.toNanos());
        String timeTaken = nanoTime.format(df);
        sb.append("Export elapsed time: " + timeTaken + "\n"); 

        log.info("Bulk Export finished");
		Map<String, Object> model = new HashMap<String, Object>();
		String jsonEscaped = sb.toString().replace("\n", "\\\\n").replace("\r", "\\\\r").replace("\t", "\\\\t");
		model.put("output", jsonEscaped);
		if(null != runningExports && !runningExports.isEmpty()) {
			updateModel(model);
		}
		runningExports.remove(jobId);
	    return model;
    }
	
    public ServiceRegistry getServiceRegistry() 
    {
        return serviceRegistry;
    }


    public void setServiceRegistry(ServiceRegistry serviceRegistry) 
    {
        this.serviceRegistry = serviceRegistry;
    }
    
	public Map<String, Object> getRunningExports() {
		Map<String, Object> model = new HashMap<String, Object>();
		if(null != runningExports && !runningExports.isEmpty()) {
			updateModel(model);
		}
	    return model;
	}
	
	//SA 10/28/22 : Added this method to check if the passed jobId is already in progress.
	//jobId is stored in a map of runningExports object.
	//This module seems to have been written with only one job per export in mind per server
	//This I assume looking at the function updateModel being called from cancelExport written by the original author
	//model.put("jobs", Collections.list(runningExports.keys()).get(0));
	//The line above suggests that "job" key will only store the first job.
	//Trying to keep my changes to the minimum.
	//I check for the job in runningExports and if it is, return the progress using updateModel function
	public Map<String, Object> isJobRunning(String jobId) {
		log.debug("Checking if job with jobId " + jobId + " is running.");
		Map<String, Object> model = new HashMap<String, Object>();
		Engine eng = runningExports.get(jobId);
		if ( eng != null ) {
			log.debug("Job with id " + jobId + " Running");
			model.put(jobId, "Already running");
			updateModel(model);
		}
		else
			log.debug("Job with id " + jobId + " not running");
		
		return model;
	}
	
	public Map<String, Object> cancelExport(String jobId) {
		Engine runningEngine = runningExports.get(jobId);
		Map<String, Object> model = new HashMap<String, Object>();
		if(null != runningEngine) {
			runningEngine.setCancelExport(true);
		}
		model.put("output", jobId+" Cancelled");
		if(null != runningExports && !runningExports.isEmpty()) {
			updateModel(model);
		}
	    return model;
	}
	public Map<String, Object> updateModel(Map<String, Object> model) {
		model.put("jobs", Collections.list(runningExports.keys()).get(0));
		Engine firstEngine = new ArrayList<Engine>(runningExports.values()).get(0);
		model.put("totalNodesToExport", firstEngine.getTotalNodesToExport());
		model.put("availableNodesToExport", firstEngine.getAvailableNodesToExport());
		model.put("previouslyExportedNodes", firstEngine.getPreviouslyExportedNodes());
		/*
		// The below only works on java 1.8 because of stream()
		model.put("totalNodesToExport", runningExports.values().stream().findFirst().get().getTotalNodesToExport());
		model.put("availableNodesToExport", runningExports.values().stream().findFirst().get().getAvailableNodesToExport());
		model.put("previouslyExportedNodes", runningExports.values().stream().findFirst().get().getPreviouslyExportedNodes());
		*/
		return model;
	}
/*	
	public static class ExportCallable implements Callable<Boolean> {
		private NodeRef nodeRef = null;
		private Engine engine = null;
		public ExportCallable(Engine engine, NodeRef nodeRef) {
			this.engine = engine;
			this.nodeRef = nodeRef;
		}
		public Boolean call() throws Exception {
			Boolean status = AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<Boolean>() {
                public Boolean doWork() throws Exception {
                	return engine.execute(nodeRef);
                }
            }, AuthenticationUtil.getSystemUserName());
			return status;
		}
	}
	*/
}
