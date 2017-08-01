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
package org.alfresco.extensions.bulkexport.controler;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.alfresco.extensions.bulkexport.dao.AlfrescoExportDao;
import org.alfresco.extensions.bulkexport.dao.NodeRefRevision;
import org.alfresco.extensions.bulkexport.model.FileFolder;
import org.alfresco.extensions.bulkexport.utils.ExportUtils;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.util.Pair;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


/**
 * This classe is a engine of systems
 * 
 * @author Denys G. Santos (gsdenys@gmail.com)
 * @version 1.0.1
 */
public class Engine 
{
    Log log = LogFactory.getLog(Engine.class);

    /** Data Access Object */
    private AlfrescoExportDao dao;
    
    /** File and folder manager */
    private FileFolder fileFolder;

    private boolean exportVersions;

    /** If true the the head revision will be named, eg. if head revision is 1.4 then filename will contain the revision. 
     * This behaviour is not how the bulk importer expects revisions */
    private boolean revisionHead;

    /** if true the look for a cache containing a list of all nodes to export
     * */
    private boolean useNodeCache;

    private AtomicInteger totalNodesToExport = new AtomicInteger();
    private AtomicInteger availableNodesToExport = new AtomicInteger();
    private AtomicInteger previouslyExportedNodes = new AtomicInteger();
    private String cachedFileId = "-default-";
    private boolean cancelExport = false;
    
    /**
     * Engine Default Builder
     * 
     * @param dao Data Access Object
     * @param fileFolder File and Folder magager
     */
    public Engine(AlfrescoExportDao dao, FileFolder fileFolder, boolean exportVersions, boolean revisionHead, boolean useNodeCache) 
    {
        this.dao =  dao;
        this.fileFolder = fileFolder;
        this.exportVersions = exportVersions;
        this.revisionHead = revisionHead;
        this.useNodeCache = useNodeCache;
    }

    /**
     * Recursive method to export alfresco nodes to file system 
     * 
     * @param nodeRef
     */
    public Pair<Integer, Integer> execute(NodeRef nodeRef, String fromDate, String toDate) throws Exception 
    {    
        int totalCount = -1;
        int exportedCount = -1;
        if(null != nodeRef) {
        	if((null == fromDate || fromDate.isEmpty()) && (null == toDate || toDate.isEmpty())) {
		        // case node is folder create a folder and execute recursively 
		        // other else create file 
		        log.debug("executing search with noderef");
		        this.cachedFileId = nodeRef.getId();
		        
		        if(!this.dao.isNodeIgnored(nodeRef.toString()))
		        {    
		            log.info("Find all nodes to export (no history)");
		            List<NodeRef> allNodes = getNodesToExport(nodeRef);
		            totalCount = allNodes.size();
		            log.info("Nodes to export = " + allNodes.size());
		            exportedCount = exportNodes(allNodes);
		            log.info("Exported Node Count = "+exportedCount);
		        }    
		        log.debug("executing search with noderef finished");
		        return new Pair<Integer, Integer>(totalCount, exportedCount);
        	} else {
		        log.debug("executing search with modified date");
		        //this.cachedFileId = modifiedDate;
		        this.cachedFileId = (null != fromDate && !fromDate.isEmpty())?"FROM-"+fromDate:"TO-"+toDate;
	            List<NodeRef> allNodes = getNodesToExport(nodeRef, fromDate, toDate, this.cachedFileId);
	            totalCount = allNodes.size();
	            log.info("Nodes to export = " + allNodes.size());
	            exportedCount = exportNodes(allNodes);
	            log.info("Exported Node Count = "+exportedCount);
		        log.debug("executing search with modified date finished");
		        return new Pair<Integer, Integer>(totalCount, exportedCount);
        	}
        }
        return null;
    }

    private List<NodeRef> getNodesToExport(NodeRef nodeRef, String fromDate, String toDate, String cacheId) throws Exception {
        List<NodeRef> nodes = null;
       	totalNodesToExport.set(0);
        //String cacheId = nodeRef.getId()+"-"+modifiedDate;
        if (useNodeCache)
        {
            nodes = retrieveNodeListFromCache(cacheId);
        }

        if (nodes == null)
        {
            nodes = findAllNodes(nodeRef, fromDate, toDate);
            storeNodeListToCache(cacheId, nodes);
            if (useNodeCache)
            {
                log.info("Generated Cached Node list");
                log.info("Total Number of Nodes to Export: "+nodes.size());
                throw new CacheGeneratedException("Generated Cached Node List Only", nodes.size());
            }
        }
        else
        {
            log.info("Using Cached Node list");
        }

        return nodes;
    	
    }
    
    private List<NodeRef> getNodesToExport(NodeRef rootNode) throws Exception 
    {
        List<NodeRef> nodes = null;
       	totalNodesToExport.set(0);
        if (useNodeCache)
        {
            nodes = retrieveNodeListFromCache(rootNode.getId());
        }

        if (nodes == null)
        {
            nodes = findAllNodes(rootNode);
            storeNodeListToCache(rootNode.getId(), nodes);
            if (useNodeCache)
            {
                log.info("Generated Cached Node list");
                log.info("Total Number of Nodes to Export: "+nodes.size());
                throw new CacheGeneratedException("Generated Cached Node List Only", nodes.size());
            }
        }
        else
        {
            log.info("Using Cached Node list");
        }

        return nodes;
    }

    private String nodeFileName(String id)
    {
        return nodeFile(id).getPath();
    }
    
    private File nodeFile(String id) {
        File fname = new File(fileFolder.basePath(), id + ".cache");
        return fname;
    }

    private void storeNodeListToCache(String id, List<NodeRef> list) throws Exception 
    {
        // get a better name
        FileOutputStream fos= new FileOutputStream(nodeFileName(id));
        ObjectOutputStream oos= new ObjectOutputStream(fos);
        oos.writeObject(list);
        oos.close();
        fos.close();
    }

    private List<NodeRef> retrieveNodeListFromCache(String id) throws Exception 
    {
        List<NodeRef> list = null;
        List<NodeRef> updatedList = null;
        ArrayList<NodeRef> completedList = new ArrayList<NodeRef>();

        FileInputStream fisCompleted = null;
        ObjectInputStream oisCompleted = null;
        FileInputStream fis = null;
        ObjectInputStream ois = null;
        if(!this.nodeFile(id).exists()) {
        	return null;
        }
        try
        {
            fis = new FileInputStream(nodeFileName(id));
            ois = new ObjectInputStream(fis);
            list = (List<NodeRef>) ois.readObject();
       		if(null != list) totalNodesToExport.set(list.size());
        } catch (FileNotFoundException e) {
            // this exception means we have no noelist cache - we just ignore and continue
            log.info("could not open nodelist cache file");
        } finally {
        	if(null != ois) {
        		ois.close();
        	}
        	if(null != fis) {
        		fis.close();
        	}
        }
       	if(!this.completedFile().exists()) {
       		return list;
       	}
       	if(null != list) {
            updatedList = new ArrayList<NodeRef>(list);
	    } else {
	    	updatedList = list;
	    }
        try {
        	fisCompleted = new FileInputStream(this.completedFile().getPath());
        	oisCompleted = new ObjectInputStream(fisCompleted);
            while(true) {
            	try{
            		//completedList.add((NodeRef)oisCompleted.readObject());
            		NodeRef completedNodeRef = (NodeRef)oisCompleted.readObject();
                    completedList.add(completedNodeRef);
                    updatedList.remove(completedNodeRef);
                    previouslyExportedNodes.set(completedList.size());
                    ExportUtils.logInBatch(previouslyExportedNodes.get(), "Previously exported nodes count: ");
            	} catch (Exception e) {
            		log.info("Completed File Read Complete: "+e.getMessage());
            		break;
            	}
            }
        } catch (FileNotFoundException ex) {
        	log.debug("Could not open completed file");
        } finally {
        	if(null != oisCompleted) {
        		oisCompleted.close();
        	}
        	if(null != fisCompleted) {
        		fisCompleted.close();
        	}
        }
        /*
        if(null != list) {
        	list.removeAll(completedList);
        }
        return list;
        */
        if(null != list) {
            log.info("List SIZE: "+ list.size());
        }
        if(null != completedList) {
            log.info("Completed List SIZE: "+completedList == null ? "NULL" : completedList.size());
        }
        if(null != updatedList) {
            log.info("TO PROCESS SIZE: "+updatedList == null ? "NULL" : updatedList.size());
        }
	    log.info("Retrieving Cached list is a sucess");
	    return updatedList;
    }

    private List<NodeRef> findAllNodes(NodeRef nodeRef, String fromDate, String toDate) throws Exception {
    	String path = this.dao.getPrefixPath(nodeRef);
    	String queryStr = null;
    	if((null != fromDate && !fromDate.isEmpty()) && (null == toDate || toDate.isEmpty())) {
    		queryStr = "PATH:\""+path+"//*\" AND +@cm\\:modified:[\""+fromDate+"\" TO MAX]";
    	} else if((null == fromDate || fromDate.isEmpty()) && (null != toDate && !toDate.isEmpty())) {
    		queryStr = "PATH:\""+path+"//*\" AND +@cm\\:modified:[MIN TO \""+toDate+"\"]";
    	} else if((null != fromDate && !fromDate.isEmpty()) && (null != toDate && !toDate.isEmpty())) {
    		queryStr = "PATH:\""+path+"//*\" AND +@cm\\:modified:[\""+fromDate+"\" TO \""+toDate+"\"]";
    	}
    	List<NodeRef> nodes = this.dao.getAllNodesForQuery(queryStr, this);
    	for(NodeRef node : nodes) {
    		if(this.dao.isNodeIgnored(nodeRef.toString())) {
    			nodes.remove(node);
    		}
    	}
        log.info("findAllNodes (nodeRef, fromDate, toDate) finished. Number of nodes: "+nodes.size());
        totalNodesToExport.set(nodes.size());
        return nodes;
    }
    
    /**
     * Recursive find of all item head nodes from a given node ref
     * 
     * @param nodeRef
     */
    private List<NodeRef> findAllNodes(NodeRef nodeRef) throws Exception 
    {    
        List<NodeRef> nodes = new ArrayList<NodeRef>();

        log.debug("findAllNodes (noderef)");
       
        if(!this.dao.isNodeIgnored(nodeRef.toString()) && !isCancelExport())
        {    
            if(this.dao.isFolder(nodeRef))
            {
                nodes.add(nodeRef); // add folder as well
                totalNodesToExport.incrementAndGet();
                ExportUtils.logInBatch(totalNodesToExport);
                List<NodeRef> children= this.dao.getChildren(nodeRef);
                for (NodeRef child : children) 
                {            
                    nodes.addAll(this.findAllNodes(child));
                }
            } 
            else 
            {
                nodes.add(nodeRef);
                totalNodesToExport.incrementAndGet();
                ExportUtils.logInBatch(totalNodesToExport);
            }
        }     

        log.debug("execute (noderef) finished");
        return nodes;
    }

    private void exportHeadRevision(NodeRef nodeRef) throws Exception
    {
        this.createFile(nodeRef);
    }

    private void exportFullRevisionHistory(NodeRef nodeRef) throws Exception
    {
        Map<String,NodeRefRevision> nodes = this.dao.getNodeRefHistory(nodeRef.toString());
        if (nodes != null)
        {
            List sortedKeys=new ArrayList(nodes.keySet());

            Collections.sort(sortedKeys, new VersionNumberComparator());
            if (sortedKeys.size() < 1)
            {
                throw new Exception("no revisions available");
            }

            String headRevision = (String)sortedKeys.get(sortedKeys.size()-1);

            for (String revision : nodes.keySet()) 
            {
                NodeRefRevision nodeRevision = nodes.get(revision);
                this.createFile(nodeRef, nodeRevision.node, revision, headRevision == revision);
                if(headRevision == revision) {
                	//this.createFile(nodeRef);
                }
            }
        }
        else
        {
            // no revision history so lets just create the most recent revision
            log.debug("execute (noderef) no revision history found, dump node as head revision");
            this.createFile(nodeRef, nodeRef, "1.0", true);
        }
    }

    /**
     * Iterate over nodes to export, and do appropriate action
     * 
     * @param nodesToExport
     */
    private int exportNodes(List<NodeRef> nodesToExport) throws Exception 
    {
        //final int NODES_TO_PROCESS = 100;

        //int logCount = nodesToExport.size();
        availableNodesToExport.set(nodesToExport.size());

        for (NodeRef nodeRef : nodesToExport) 
        {
        	if(isCancelExport()) {
        		break;
        	}
            //logCount--;
            availableNodesToExport.decrementAndGet();
            if(this.dao.isFolder(nodeRef))
            {
                this.createFolder(nodeRef);
                this.storeCompletedNodeToCache(nodeRef);
            } 
            else
            {
                if (exportVersions)
                {
                    exportFullRevisionHistory(nodeRef);
                    this.storeCompletedNodeToCache(nodeRef);
                }
                else
                {
                    exportHeadRevision(nodeRef);
                    this.storeCompletedNodeToCache(nodeRef);
                }
            }
            ExportUtils.logInBatch(availableNodesToExport.get(), "Remaining Parent Nodes to process: ");

            /*
            if (logCount % NODES_TO_PROCESS == 0)
            {
                log.info("Remaining Parent Nodes to process " + logCount);
            }
            */
        }
        //return nodesToExport.size() - logCount;
        return nodesToExport.size() - availableNodesToExport.get();
    }

    private File completedFile() {
        File completeFile = new File(fileFolder.basePath(), this.cachedFileId + ".complete");
        return completeFile;
    }
    private void storeCompletedNodeToCache(NodeRef nodeRef) throws Exception {
       	FileOutputStream fos = null;
       	ObjectOutputStream oos = null;
        try {
	        if(!completedFile().exists()) {
	        	//completedFile().createNewFile();
	        	fos = new FileOutputStream(completedFile().getPath(), true);
	        	oos = new ObjectOutputStream(fos);
	        } else {
	        	fos = new FileOutputStream(completedFile().getPath(), true);
        		oos = new AppendingObjectOutputStream(fos);
        	}
        	oos.writeObject(nodeRef);
        } finally {
        	if(null != oos) {
        		oos.close();
        	}
        	if(null != fos) {
        		fos.close();
        	}
        }
    }
    
    private class AppendingObjectOutputStream extends ObjectOutputStream {
    	public AppendingObjectOutputStream(OutputStream out) throws IOException {
    		super(out);
    	}

    	@Override
    	protected void writeStreamHeader() throws IOException {
    		reset();
    	}

    }
    
    
    /**
     * Create file (Document and Bulk XML Meta data)
     * 
     * @param file 
     * @throws Exception
     */
    private void createFile(NodeRef headNode, NodeRef file, String revision, boolean isHeadRevision) throws Exception 
    {
        String path = null;
        if (revision == null)
        {
            log.error("createFile (headNode: "+headNode.toString() + " , filenode: )"+file.toString()+" , revision: " + revision + ")");
            throw new Exception("revision for node was not found");
        }

        //path = this.dao.getPath(headNode) + ".v" + revision;
        path = this.dao.getPath(headNode);

        // if we are exporting using the revisions compatible with alfresco bulk import then we do not number the head(most recent) revisoon
        if(isHeadRevision) {
            /* 
             * Not setting this does not give the head node values accurately.
             * To test this, add/update some aspects/properties to a versioned node and try to export with all versions.
             * You will notice that the newly added aspects are not seen.
             * If we only get the head version, they are seen.
             */
            file = headNode;
	        if (!revisionHead) {
	            revision = null;
	        }
        }

        doCreateFile(file, path, revision);
    }

    private void createFile(NodeRef file) throws Exception 
    {
        String path = null;
        path = this.dao.getPath(file);
        doCreateFile(file, path, null);
    }

    private void doCreateFile(NodeRef file, String path, String revision) throws Exception 
    {
        //get Informations
        log.debug("doCreateFile (noderef)");

        // need these variables out of the try scope for debugging purposes when the exception is thrown
        String type = null;
        List<String> aspects = null;
        Map<String, String> properties = null;
       	String contentPath = path;

        try
        {
        	if(null != revision) {
        		contentPath = path + ".v" + revision;
        	}
            String fname = this.fileFolder.createFullPath(contentPath);
            log.debug("doCreateFile file =" + fname);
            if (this.dao.getContentAndStoreInFile(file, fname) == false)
            {
                log.debug("doCreateFile ignore this file"); 
                return;
            }
            type = this.dao.getType(file);
            aspects = this.dao.getAspectsAsString(file);
            properties = this.dao.getPropertiesAsString(file);
            
            //Create Files
            this.fileFolder.insertFileProperties(file, type, aspects, properties, path, revision);
            type = null;
            properties = null;
            aspects = null;
        }
        catch (Exception e) 
        {
            // for debugging purposes
            log.error("doCreateFile failed for noderef = " + file.toString());
            throw e;
        }
    }
    
    
    /**
     * Create Folder and XML Metadata
     * 
     * @param file
     * @throws Exception
     */
    private void createFolder(NodeRef folder) throws Exception 
    {
        //Get Data
        log.debug("createFolder");
        String path = this.dao.getPath(folder);
        log.debug("createFolder path="+path);
        String type = this.dao.getType(folder);
        log.debug("createFolder type="+type);
        List<String> aspects = this.dao.getAspectsAsString(folder);
        Map<String, String> properties = this.dao.getPropertiesAsString(folder);
        
        //Create Folder and XMl Metadata
        this.fileFolder.createFolder(path);
        this.fileFolder.insertFileProperties(folder, type, aspects, properties, path, null);
    }

	public boolean isCancelExport() {
		return cancelExport;
	}
	public void setCancelExport(boolean cancelExport) {
		this.cancelExport = cancelExport;
	}
	public int getTotalNodesToExport() {
		return this.totalNodesToExport.get();
	}
	public int getAvailableNodesToExport() {
		return this.availableNodesToExport.get();
	}
	public int getPreviouslyExportedNodes() {
		return this.previouslyExportedNodes.get();
	}
}
