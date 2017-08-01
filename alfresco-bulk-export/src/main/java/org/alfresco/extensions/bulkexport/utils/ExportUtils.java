package org.alfresco.extensions.bulkexport.utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.alfresco.extensions.bulkexport.controler.Engine;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.search.ResultSet;
import org.alfresco.service.cmr.search.SearchParameters;
import org.alfresco.service.cmr.search.SearchService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class ExportUtils {
	
	public static final int PAGE_SIZE = 500;
    public static Log log = LogFactory.getLog(ExportUtils.class);
	
    public static void logInBatch(int count, String message) {
    	logInBatch(100, count, message);
    }
    
    public static void logInBatch(int batchSize, int count, String message) {
    	if(count % batchSize == 0) {
    		log.info(message+count);
    	}
    }
    
    public static void logInBatch(AtomicInteger nodeCount) {
    	logInBatch(100, nodeCount);
    }
    
    public static void logInBatch(int batchSize, AtomicInteger nodeCount) {
    	if (nodeCount.get() % batchSize == 0) {
    		log.info("Current Node Count: " + nodeCount.get());
    	}
    }
    
	public static List<NodeRef> executeQuery(String query, SearchService searchService, Engine engine) throws IOException {
		return executeQuery(query, -1, searchService, engine);
	}
	
	public static List<NodeRef> executeQuery(String query, int maxItems, SearchService searchService, Engine engine) throws IOException {
		int skip = 0;
		AtomicInteger totalResults = new AtomicInteger();
		boolean keepSearching = true;
		List<NodeRef> nodes = new ArrayList<NodeRef>();
		SearchParameters sp = new SearchParameters();
		sp.setSkipCount(skip);
		sp.setQuery(query);
		sp.setMaxItems(((maxItems == -1) || (maxItems >= PAGE_SIZE)) ? PAGE_SIZE : maxItems);
	    sp.addStore(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE);
		sp.setLanguage(SearchService.LANGUAGE_LUCENE);

		ResultSet rs = searchService.query(sp);
		while ((null != rs) && (rs.length() > 0) && keepSearching && !engine.isCancelExport()){
			try {
				for (int i = 0; i < rs.length(); i++) {
					nodes.add(rs.getRow(i).getNodeRef());
					totalResults.incrementAndGet();
					logInBatch(totalResults);
					if (( maxItems > -1) && (totalResults.get() >= maxItems)) {
						keepSearching = false;
					}
				}
				sp.setSkipCount(sp.getSkipCount() + rs.length());
			} finally {
				if (null != rs) {
					rs.close();
				}
			}
			rs = searchService.query(sp);
		} 
		if (null != rs) {
			rs.close();
		}

		return nodes;
	}
}
