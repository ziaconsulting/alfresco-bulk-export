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
package org.alfresco.extensions.bulkexport.dao;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.alfresco.extensions.bulkexport.controler.Engine;
import org.alfresco.extensions.bulkexport.utils.ExportUtils;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.action.ActionModel;
//SA 9/22/2022 PublishingModel not found in alfresco 7.2, comments it out.
////It throws A NoClassDefinitionFound at run time when the export module is executed.
//
//import org.alfresco.repo.publishing.PublishingModel;
import org.alfresco.repo.version.VersionModel;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.model.FileFolderService;
import org.alfresco.service.cmr.model.FileInfo;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.ContentIOException;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.Path;
import org.alfresco.service.cmr.security.PermissionService;
import org.alfresco.service.cmr.version.Version;
import org.alfresco.service.cmr.version.VersionHistory;
import org.alfresco.service.cmr.version.VersionService;
import org.alfresco.service.namespace.NamespacePrefixResolver;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.ibm.icu.text.SimpleDateFormat;
import com.google.gson.Gson;
import org.w3c.dom.Node;


/**
 * Implementation of {@link AlfrescoExportDao} interface
 * 
 * @author Denys Santos (gsdenys@gmail.com)
 * @version 1.0.1
 */
public class AlfrescoExportDaoImpl implements AlfrescoExportDao 
{

    Log log = LogFactory.getLog(AlfrescoExportDaoImpl.class);

    /** Alfresco {@link ServiceRegistry} to Data Access Object */ 
    private ServiceRegistry registry;

    private final NodeService nodeService;
    private final FileFolderService service;
    private final NamespacePrefixResolver nsR;
    private final ContentService contentService;
    private final PermissionService permissionService;
    private final VersionService versionService;
        
    private QName ignoreAspectQname[] = 
    {
            //ContentModel.ASPECT_TAGGABLE
    };
    
    private String ignoreAspectPrefix[] = 
    {
            "app"
    };
    
    //SA 10/03/2022 Commented out ContentModel.PROP_NODE_UUID from this list as we need this to make sure we have the correct mapping between a document name and 
    //its uuid. The name might not necessarily be unique but uuid would be.
    //This is only so we can export from ldms to AoDocs
    //SA 01/016/23 Commented out PROP_CONTENT so that the content_url with its various fields get exported.
    //Commented out ASPECT_TAGGABLE
    private QName ignorePropertyQname[] = 
    { 
            ContentModel.PROP_NODE_DBID, 
            //ContentModel.PROP_NODE_UUID, 
            ContentModel.PROP_CATEGORIES,
            //ContentModel.PROP_CONTENT,
            //ContentModel.ASPECT_TAGGABLE,
            ContentModel.PROP_VERSION_LABEL,
            QName.createQName(NamespaceService.CONTENT_MODEL_1_0_URI, VersionModel.PROP_VERSION_TYPE),
            QName.createQName(NamespaceService.CONTENT_MODEL_1_0_URI, "lastThumbnailModification")

    };
    
    private String[] ignorePropertyPrefix = 
    {
            "app",
            "exif"
    };
    
//SA 9/22/2022 PublishingModel not found in alfresco 7.2, comments it out.
//It throws A NoClassDefinitionFound at run time when the export module is executed.
    private QName[] ignoredType = 
    {
            ContentModel.TYPE_SYSTEM_FOLDER,
            ContentModel.TYPE_LINK,
            ContentModel.TYPE_RATING,
            ActionModel.TYPE_ACTION,
            ActionModel.TYPE_COMPOSITE_ACTION
            //,PublishingModel.TYPE_PUBLISHING_QUEUE
    };
    
    private List<QName> ignoredAspects = Collections.unmodifiableList(
    	new ArrayList<QName>() {{
    		add(QName.createQName(NamespaceService.CONTENT_MODEL_1_0_URI, "thumbnailModification"));
    	}});
    
    
    /**
     * Data Access Object Builder
     * 
     * @param registry Alfresco {@link ServiceRegistry} 
     */
    public AlfrescoExportDaoImpl(ServiceRegistry registry) 
    {
        log.debug("Test debug logging. Congratulation your AMP is working");
        this.registry  = registry;

        nodeService    = this.registry.getNodeService();
        service        = this.registry.getFileFolderService();
        nsR            = this.registry.getNamespaceService();
        contentService = this.registry.getContentService();
        permissionService = this.registry.getPermissionService();
        versionService = this.registry.getVersionService();
    }
    
    
    /**
     * @see com.alfresco.bulkexport.dao.AlfrescoExportDao#getProperties(java.lang.String)
     */
    public Map<QName, Serializable> getProperties(NodeRef nodeRef) throws Exception 
    {
        Map<QName, Serializable> properties = nodeService.getProperties(nodeRef);
        return properties;
    }
    
    
    /**
     * @see com.alfresco.bulkexport.dao.AlfrescoExportDao#getMetadataAsString(java.lang.String)
     */
    public Map<String, String> getPropertiesAsString(NodeRef nodeRef) throws Exception 
    {
                
        Map<QName, Serializable> properties = this.getProperties(nodeRef);
        
        Map<String, String> props = new HashMap<String, String>();
        Set<QName> qNameSet = properties.keySet();
        
        for (QName qName : qNameSet) 
        {
            //case the qname is in ignored type do nothing will do.
            if(this.isPropertyIgnored(qName))
            {
                continue;
            }
                    
            Serializable obj = properties.get(qName);
            String name = this.getQnameStringFormat(qName);
            String value = this.formatMetadata(obj);
            //SA 01/18/2023 Tags are returned in an ArrayList of noderefs
            if ( qName.equals(ContentModel.ASPECT_TAGGABLE))
                value = get_tagged_names_as_string((ArrayList<NodeRef>)obj);
            //put key value in the property list as <prefixOfProperty:nameOfProperty, valueOfProperty>
            props.put(name, value);
        }
        
        return props;
    }

    //From an arraylist of noderefs, returns names of tags as string ['foo','bar']
    private String get_tagged_names_as_string(ArrayList<NodeRef> tag_list) {
        if ( tag_list == null )
            return "";

        ArrayList<String> values = new ArrayList<String>();
        for( NodeRef tag : tag_list) {
            String name = (String)nodeService.getProperty(tag, ContentModel.PROP_NAME);
            values.add(name);
        }
        return values.toString();
    }
    /**
     * @see com.alfresco.bulkexport.dao.AlfrescoExportDao#getChildren(java.lang.String)
     */
    public List<NodeRef> getChildren(NodeRef nodeRef) throws Exception 
    {
        List<NodeRef> listChildren = new ArrayList<NodeRef>();
       
        List<ChildAssociationRef> children = nodeService.getChildAssocs(nodeRef);
        
        for (ChildAssociationRef childAssociationRef : children) 
        {
            NodeRef child = childAssociationRef.getChildRef();
                
            if(this.isTypeIgnored(nodeService.getType(child)))
            {
                continue;
            }
            
            listChildren.add(new NodeRef(child.toString())); // deep copy
        }
        
        return listChildren;
    }
    

    /**
     * @see com.alfresco.bulkexport.dao.AlfrescoExportDao#getFolderChildren(java.lang.String)
     */
    public List<NodeRef> getFolderChildren(NodeRef nodeRef) throws Exception 
    {
        
        List<FileInfo> folders = service.listFolders(nodeRef);
        
        List<NodeRef> listChildren = new ArrayList<NodeRef>();
        
        for (FileInfo fileInfo : folders) 
        {
            listChildren.add(fileInfo.getNodeRef());
        }
        
        return listChildren;
    }

    
    /**
     * @see com.alfresco.bulkexport.dao.AlfrescoExportDao#getFileChildren(java.lang.String)
     */
    public List<NodeRef> getFileChildren(NodeRef nodeRef) throws Exception 
    {
        List<FileInfo> files = service.listFiles(nodeRef);
        
        List<NodeRef> listChildren = new ArrayList<NodeRef>();
        
        for (FileInfo fileInfo : files) 
        {
            listChildren.add(fileInfo.getNodeRef());
        }
        
        return listChildren;
    }

    
    /**
     * @see com.alfresco.bulkexport.dao.AlfrescoExportDao#getPath(java.lang.String)
     */
    public String getPath(NodeRef nodeRef) throws Exception 
    {
        //get element Path
        Path path = nodeService.getPath(nodeRef);
        
        //get element name 
        Serializable name = nodeService.getProperty(nodeRef, ContentModel.PROP_NAME);
        
        //get element Path as String
        String basePath = path.toDisplayPath(nodeService, permissionService);
        
        return (basePath + "/" + name);
    }

    
    /**
     * @see com.alfresco.bulkexport.dao.AlfrescoExportDao#getContent(java.lang.String)
     */
    public ByteArrayOutputStream getContent(NodeRef nodeRef) throws Exception 
    {
        ContentReader reader = contentService.getReader(nodeRef, ContentModel.PROP_CONTENT);
        if (reader == null)
        {
            // no data for this node
            return null;
        }

        
        InputStream in = reader.getContentInputStream();
        int size = in.available();
        
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[ (size + 100) ];
        int sizeOut;
        
        while ((sizeOut=in.read(buf)) != -1 ) 
        {
            out.write(buf, 0, sizeOut);
        }
        
        out.flush();
        out.close();
        
        in.close();
        
        
        return out;
    }

    public boolean getContentAndStoreInFile(NodeRef nodeRef, String outputFileName) throws Exception 
    {
        ContentReader reader = contentService.getReader(nodeRef, ContentModel.PROP_CONTENT);
        if (reader == null)
        {
            // no data for this node
            return false;
        }
       
        /*
         * SA 10/30/22, if content is empty, meaning somehow the *.bin is missing. 
         * Catch the exception and log the error and create a 0 byte file 
         */
        File output = new File(outputFileName);
        
        try {
        	log.debug("Before getting content for file " + nodeService.getProperties(nodeRef).get(ContentModel.PROP_CONTENT));
        	reader.getContent(output);
        }
        catch ( ContentIOException ex ) {
        	log.debug(ex);
        	log.error(ex);
        	writeZeroBytes(outputFileName);
        	
        }

        return true;
    }

    //SA 10/30/22 It will happen that some bin files will go missing.
    //If this happens write a 0 bytes file and continue exporting.
    private void writeZeroBytes(String outputFileName) throws Exception {
        try {
        	
        	String emptyString  = "";
        	log.debug("Writing 0 byes file to " + outputFileName);
            Files.write(Paths.get(outputFileName), emptyString.getBytes());
        	log.debug("Successfully wrote 0 byes file to " + outputFileName);
        } catch (IOException e) {
            throw new Exception(e);
        }    	
    }
    
    /**
     * @see com.alfresco.bulkexport.dao.AlfrescoExportDao#getProperty(java.lang.String, java.lang.String)
     */
    public String getProperty(NodeRef nodeRef, QName propertyQName) throws Exception 
    {
        Serializable value = nodeService.getProperty(nodeRef, propertyQName);
        
        return this.formatMetadata(value);
    }

    
    /**
     * @see com.alfresco.bulkexport.dao.AlfrescoExportDao#getType(java.lang.String)
     */
    public String getType(NodeRef nodeRef) throws Exception 
    {
        QName value = nodeService.getType(nodeRef);
        
        String name = this.getQnameStringFormat(value);
        
        return name;
    }
    
    
    /**
     * @see com.alfresco.bulkexport.dao.AlfrescoExportDao#getAspects(java.lang.String)
     */
    public List<QName> getAspects(NodeRef nodeRef) throws Exception 
    {
        Set<QName> aspectSet = nodeService.getAspects(nodeRef);
        aspectSet.removeAll(ignoredAspects);
        List<QName> qn = new ArrayList<QName>(aspectSet);
        
        return qn;
    }
    
    
    /**
     * @see com.alfresco.bulkexport.dao.AlfrescoExportDao#getAspectsAsString(java.lang.String)
     */
    public List<String> getAspectsAsString(NodeRef nodeRef) throws Exception 
    {
        List<QName> qn = this.getAspects(nodeRef);
        List<String> str = new ArrayList<String>();
        
        for (QName qName : qn) 
        {            
            if(this.isAspectIgnored(qName)) 
            {
                continue;
            }
            
            String name = this.getQnameStringFormat(qName);
            str.add(name); 
        }
        
        return str;
    }
    
    
    /**
     * @see com.alfresco.bulkexport.dao.AlfrescoExportDao#isFolder(java.lang.String)
     */
    public boolean isFolder(NodeRef nodeRef) throws Exception 
    {
        log.debug("isFolder");

        FileInfo info = service.getFileInfo(nodeRef);
        log.debug("isFolder got file info getName = " + info.getName());
        log.debug("isFolder got file info isFolder = " + info.isFolder());
        log.debug("isFolder return isFolder");
        
        return info.isFolder();
    }
    
    /**
     * @see com.alfresco.bulkexport.dao.AlfrescoExportDao#getNodeRef(java.lang.String)
     */
    public NodeRef getNodeRef(String nodeRef) 
    {
        try
        {
            NodeRef nr = new NodeRef(nodeRef);
            return nr;
        } 
        catch (Exception e) 
        {
            return null;
        }
    }

    /**
     * @see com.alfresco.bulkexport.dao.AlfrescoExportDao#getNodeRefHistory(java.lang.String)
     */
    public Map<String,NodeRefRevision> getNodeRefHistory(String nodeRef) throws Exception
    {
        log.debug("getNodeRefHistory(nodeRef) nodeRef = " + nodeRef);
        Map<String,NodeRefRevision> nodes = null;

        NodeRef nr = getNodeRef(nodeRef);
        if (nr != null)
        {
            VersionHistory history =  versionService.getVersionHistory(nr);
            if (history == null)
            {
                log.debug("getNodeRefHistory(nodeRef) no history available");
                return nodes;
            }

            Collection<Version> availableVersions = history.getAllVersions();

            if (availableVersions == null)
            {
                log.debug("getNodeRefHistory(nodeRef) no versions found in history");
                return nodes;
            }

            nodes = new HashMap<String,NodeRefRevision>();
            Iterator iterator = availableVersions.iterator();
            while(iterator.hasNext())
            {
                    Object ov = iterator.next();
                    Version v = (Version)ov; // contains storeRef
                    String checkInComment = v.getDescription();    // check in comment
                    String vlabel = v.getVersionLabel(); // version label eg. 1.1
                    NodeRef frozenNodeRef = v.getFrozenStateNodeRef();   // this contains the revisioned versions
                    NodeRef versionedNodeRef = v.getVersionedNodeRef();  // this is allways the latest revision
                    String frozenNodRef = frozenNodeRef.toString();
                    String headNodeRef  = versionedNodeRef.toString();

                    // this contains a list of all attributes for the Item. We may not need it since we dig them out at a store item id level.
                    Map<String,Serializable>  versionProps = v.getVersionProperties(); 
                    NodeRefRevision revision = new NodeRefRevision();
                    revision.comment = checkInComment;
                    revision.node = frozenNodeRef;

                    nodes.put(vlabel, revision);
                    //
                    // we need to get the comment history as well because this is not available when we get content data and properties....
                    log.debug("getNodeRefHistory(nodeRef) v = " + v.toString());
            }
        }
        return nodes;
    }
    
    
    public boolean isNodeIgnored(String nodeRef) 
    {
        log.debug("isNodeIgnored");
        NodeRef nr = getNodeRef(nodeRef);
        
        QName value = nodeService.getType(nr);
        
        log.debug("isNodeIgnored got service type");
        return isTypeIgnored(value);
    }
    
    public List<NodeRef> getAllNodesForQuery(String query, Engine engine) throws IOException {
    	return ExportUtils.executeQuery(query, registry.getSearchService(), engine);
    }
    
    public String getPrefixPath(NodeRef nodeRef) throws Exception {
    	return nodeService.getPath(nodeRef).toPrefixString(registry.getNamespaceService());
    }
    
    // #######################################################################################
    // ####                              PRIVATE METHODS                                   ### 
    // #######################################################################################

    /**
     * Verify if the type qname is ignored 
     * 
     * @param qName
     * @return {@link Boolean}
     */
    private boolean isPropertyIgnored(QName qName) 
    {
        //verify if qname is in ignored
        for (QName qn : this.ignorePropertyQname) 
        {
            if(qn.equals(qName))
            {
                return true;
            }
        }
        
        //verify if qname prefix is in ignored
        //String prefix = qName.getPrefixString();
        String prefix = qName.getPrefixedQName(nsR).getPrefixString();
        for (String str : this.ignorePropertyPrefix) 
        {
            
            //str.equalsIgnoreCase(prefix)
            
            if(prefix.startsWith(str))
            {
                return true;
            }
        }
        
        return false;
    }

    
    /**
     * Verify if the aspect qname is ignored 
     * 
     * @param qName
     * @return {@link Boolean}
     */
    private boolean isAspectIgnored(QName qName) 
    {
        //verify if qname is in ignored
        for (QName qn : this.ignoreAspectQname) 
        {
            if(qn.equals(qName))
            {
                return true;
            }
        }
        
        //verify if qname prefix is in ignored
        //String prefix = qName.getPrefixString();
        String prefix = qName.getPrefixedQName(nsR).getPrefixString();
        for (String str : this.ignoreAspectPrefix) 
        {
            if(prefix.startsWith(str))
            {
                return true;
            }
        }
        
        return false;
    }
    
    
    /**
     * Verify if the tipe qname is ignored 
     * 
     * @param qName
     * @return {@link Boolean}
     */
    private boolean isTypeIgnored(QName qName) 
    {
        //verify if qname is in ignored
        for (QName qn : this.ignoredType) 
        {
            if(qn.equals(qName))
            {
                return true;
            }
        }
        
        return false;
    }
    

    /**
     * Return Qname in String Format
     * 
     * @param qName
     * @return {@link String}
     */
    private String getQnameStringFormat(QName qName) throws Exception
    {
        return qName.getPrefixedQName(nsR).getPrefixString();
    }


    /**
     * Format metadata guided by Bulk-Import format
     * 
     * @param obj
     * @return {@link String}
     */
    private String formatMetadata (Serializable obj)
    {
        String returnValue = "";
        
        if(obj != null) 
        {
            if(obj instanceof Date)
            {
                //SA 06/27/2023 date format was incorrect  ("yyyy-MM-dd'T'hh:mm:ss.SSSZ")
                //This produces date that is 12 hours behind the passed date.
                //Removed SimpleDateFormat with DateTimeFormatter
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);
                Date date = (Date) obj;
                returnValue = formatter.format(date.toInstant());
            }
            //SA 06/27/2023 --export an array list as a string of jsonobject
            //Original code was exporting array values as string values separated by comma
            //This doesn't work when there is a comma in the data
            else if (obj instanceof ArrayList) {
                ArrayList obj_list = (ArrayList)obj;

                returnValue = new Gson().toJson(obj_list);
            }
            else
            {
                
                //
                // TODO: Format data to all bulk-import data format (list as example)
                //
                
                returnValue = obj.toString();
            }
        }
        
        return returnValue;
    }
}
