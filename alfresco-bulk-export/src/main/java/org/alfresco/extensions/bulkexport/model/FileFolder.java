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
package org.alfresco.extensions.bulkexport.model;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.dictionary.PropertyDefinition;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.datatype.DefaultTypeConverter;
import org.alfresco.service.namespace.QName;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;



/**
 * This class manage the files and folders creation
 * 
 * @author Denys G. Santos (gsdenys@gmail.com)
 * @version 1.0.1
 */
public class FileFolder 
{
	// XML 1.0
	// #x9 | #xA | #xD | [#x20-#xD7FF] | [#xE000-#xFFFD] | [#x10000-#x10FFFF]
	public static String XML10PATTERN = "[^"
            + "\u0009\r\n"
            + "\u0020-\uD7FF"
            + "\uE000-\uFFFD"
            + "\ud800\udc00-\udbff\udfff"
            + "]";
	
    Log log = LogFactory.getLog(FileFolder.class);
 
    /** {@link String} path to export data location in Alfresco
     *  server
     */
    private String basePath;
    
    /** {@link Boolean} value to avaliate if ovewrite content 
     * exported or no 
     */
    private boolean scapeExported;
    private ServiceRegistry serviceRegistry;
    private Set<String> invalidEmptyProps = new HashSet<String>();
    private Set<String> processedTypesAndAspects = new HashSet<String>();
    private StringBuilder parentLogger = null;
    private List<String> customAspects = null;
    private Map<String, String> customProperties = null;
    private Map<String, String> updateTypesOrAspectsOrProperties = null;
    private Map<String, String> updateModelPrefix = null;
    
    /**
     * File Folder default builder
     * 
     * @param basePath
     */
    public FileFolder(String basePath, String aspects, String properties, String updateTypesOrAspectsOrProperties, String updateModelPrefix, boolean scapeExported, ServiceRegistry serviceRegistry, StringBuilder parentLogger) 
    {
        log.debug("debug enabled for FileFolder");
        this.basePath = basePath;
        this.customAspects = getAspectsList(aspects);
        this.customProperties = getMapFromString(properties);
        this.updateTypesOrAspectsOrProperties = getMapFromString(updateTypesOrAspectsOrProperties);
        this.updateModelPrefix = getMapFromString(updateModelPrefix);
        this.scapeExported = scapeExported;
        this.serviceRegistry = serviceRegistry;
        this.parentLogger = parentLogger;
    }        
    
    public String basePath()
    {
        return this.basePath;
    }
    
    public List<String> getAspectsList(String aspects) {
    	if(null != aspects && !aspects.isEmpty()) {
	    	List<String> items = Arrays.asList(aspects.split("\\s*,\\s*"));
	    	return new ArrayList<String>(items);
    	}
    	return null;
    }
    
    public Map<String, String> getMapFromString(String properties) {
    	if(null != properties && !properties.isEmpty()) {
	    	List<String> items = Arrays.asList(properties.split("\\s*,\\s*"));
	    	Map<String, String> props = new HashMap<String, String>();
	    	for(String item : items) {
	    		String[] propTokens = item.split("\\s*=\\s*");
	    		props.put(propTokens[0], propTokens[1]);
	    	}
	    	return props;
    	}
    	return null;
    }
    
    /**
     * Create a new Folder in a {@link String} path
     * 
     * @param path Path of Alfresco folder
     */
    public void createFolder(String path) throws Exception 
    {
        path = this.basePath + path;
        log.debug("createFolder path to create : " + path);
       
        try
        {
            File dir = new File(path);
            if (!dir.exists())
            {
                if (!dir.mkdirs())
                {
                    log.error("createFolder failed to create path : " + path);
                }
                else
                {
                    log.debug("createFolder path : " + path);
                }
            }
        }
        catch (Exception e) 
        {
            e.printStackTrace();
            throw e;
        }
    }
    
    
    /**
     * Create a new file in the {@link String} path 
     * 
     * @param filePath Path of file
     * @throws IOException
     */
    private void createFile (String filePath) throws Exception 
    {
        log.debug("createFile = " + filePath);

        File f=new File(filePath);
        
        try 
        {  
            if(!f.exists())
            {
              if (!f.getParentFile().exists())
              {
                  if (!f.getParentFile().mkdirs())
                  {
                      log.error("failed to create folder : " + f.getParentFile().getPath());
                  }
                  else
                  {
                      log.debug("created folder : " + f.getParentFile().getPath());
                  }
              }
              f.createNewFile();
            }
        } 
        catch (Exception e) 
        {
            e.printStackTrace();
            throw e;
        }
        log.debug("createFile filepath done");
    }
    
    
    /**
     * Create XML File
     * 
     * @param filePath Path of file
     * @return {@link String} Name of file
     * @throws Exception
     */
    private String createXmlFile(String filePath, String revision) throws Exception 
    {
        String postfix = (null != revision && !revision.isEmpty())?".v"+revision:"";
        String fp = filePath + ".metadata.properties.xml"+postfix;
        
        this.createFile(fp);
        
        return fp;
    }
    
    
    /**
     * create content file
     * 
     * @param content
     * @param filePath
     * @throws IOException
     */
    public void insertFileContent (ByteArrayOutputStream out, String filePath) throws Exception 
    {
        log.debug("insertFileContent");
        filePath = this.basePath + filePath;
        
        log.debug("insertFileContent filepath = " + filePath);
        if(this.isFileExist(filePath) && this.scapeExported)
        {
            log.debug("insertFileContent ignore file");
            return;
        }
        
        this.createFile(filePath);
        
        try 
        {
            FileOutputStream output = new FileOutputStream(filePath);
            output.write(out.toByteArray());
            output.flush();
            output.close();
        } 
        catch (Exception e) 
        {
            e.printStackTrace();
        }
    }

    /**
     * construct full file path and make directory if it does not exist
     * 
     * @param filePath
     * @throws IOException
     */
    public String createFullPath(String filePath) throws Exception 
    {
        log.debug("createFullPath");
        filePath = this.basePath + filePath;
        
        log.debug("createFullPath filepath = " + filePath);
        if(this.isFileExist(filePath) && this.scapeExported)
        {
            log.debug("createFullPath ignore file");
            return filePath;
        }
       
        File f=new File(filePath);
        
        try 
        {  
            if(!f.exists())
            {
              if (!f.getParentFile().exists())
              {
                  if (!f.getParentFile().mkdirs())
                  {
                      log.error("failed to create folder : " + f.getParentFile().getPath());
                  }
                  else
                  {
                      log.debug("created folder : " + f.getParentFile().getPath());
                  }
              }
            }
        } 
        catch (Exception e) 
        {
            e.printStackTrace();
            throw e;
        }

        return filePath;
    }
    
    
    /**
     * Insert Content Properties in the XML File
     * 
     * @param type The type of node
     * @param aspects The aspect {@link List} of node in {@link String} format
     * @param properties The properties {@link Map} of node in {@link String} format
     * @param filePath The path of file
     * @throws Exception
     */
    public void insertFileProperties(NodeRef nodeRef, String type, List<String> aspects,Map<String, String> properties, String filePath, String revision) throws Exception
    {
        filePath = this.basePath + filePath;
       
        if(null == revision) {
	        if(this.isFileExist(filePath) && this.isFileExist(filePath + ".metadata.properties.xml") && this.scapeExported)
	        {
	            return;
	        }
        } else {
	        if(this.isFileExist(filePath) && this.isFileExist(filePath + ".metadata.properties.xml"+".v"+revision) && this.scapeExported)
	        {
	            return;
	        }
        }
       
        // ZIA - START
        /* Alfresco 4.x has a bug that gets empty date when getting all versions. 
         * It works as expected when we export only the head version.
         * The method below stores all date properties and checks before writing to xml
         */
        this.setInvalidTypeProperties(nodeRef, type, aspects);
        
        /*
         * We are not going to check the attributes of the custom aspects, 
         * because they might not exist in the current system. We only want to add the
         * aspects and properties to be used in the importing system
         */
        if(null != aspects && (null != this.customAspects && !this.customAspects.isEmpty())) {
        	aspects.addAll(this.customAspects);
        }
        if(null != properties && (null != this.customProperties && !this.customProperties.isEmpty())) {
        	for(Entry<String, String> prop : this.customProperties.entrySet()) {
        		/*
        		 * If we want to get the value from an existing property
        		 * use property like "my:customProperty=cm:name"
        		 */
        		if(prop.getValue().indexOf(":") > -1) {
        			properties.put(prop.getKey(), properties.get(prop.getValue()));
        		} else {
        			properties.put(prop.getKey(), prop.getValue());
        		}
        	}
        }
        /*
         * Update type names, aspect names and property names.
         * The input should be like the following
         * my:aspectName=myapp:aspectName;my:oldProp=myapp:newProp
         */
        if(null != this.updateTypesOrAspectsOrProperties && !this.updateTypesOrAspectsOrProperties.isEmpty()) {
        	if(this.updateTypesOrAspectsOrProperties.containsKey(type)) {
        		type = updateTypesOrAspectsOrProperties.get(type);
        	}
        	aspects = getUpdatedAspects(aspects);
    		properties = getUpdatedProperties(properties);
        }
        /*
         * Update the model prefix for all existing types, aspects and properties
         * Example: myold=mynew;oldprefix=newprefix
         */
        if(null != this.updateModelPrefix && !this.updateModelPrefix.isEmpty()) {
        	for(Entry<String, String> prefixSet : this.updateModelPrefix.entrySet()) {
        		if(type.startsWith(prefixSet.getKey())) {
        			type = type.replaceFirst(prefixSet.getKey(), prefixSet.getValue());
        		}
        		aspects = getUpdatedPrefixAspects(aspects, prefixSet);
        		properties = getUpdatedPrefixProperties(properties, prefixSet);
        	}
        }
        // ZIA - END
        
        String header = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!DOCTYPE properties SYSTEM \"http://java.sun.com/dtd/properties.dtd\">\n<properties>";
        String footer = "\n</properties>";
        
        String tType = "<entry key=\"type\">" + type + "</entry>";
        String tAspect = "<entry key=\"aspects\">" + this.formatAspects(aspects) + "</entry>";
        
        String text = "\n\t" + tType + "\n\t" + tAspect;
        
        Set<String> set = properties.keySet();
        
        for (String string : set) 
        {
            String key = string;
            String value = properties.get(key);
            
            if(isInvalidEmptyType(key, value) || StringUtils.isEmpty(value)) {
            	continue;
            }
           
            value = this.formatProperty(value);
            
            
            text += "\n\t<entry key=\"" + key +"\">" + value + "</entry>";
        }
        
        String validXMLText = text.replaceAll(XML10PATTERN, "");
        if(!text.equals(validXMLText)) {
        	String validateMsg = "VALIDATE: Stripped invalid XML characters....."+filePath;
        	log.error(validateMsg);
        	parentLogger.append(validateMsg);
        	text = validXMLText;
        }
        
        try 
        {
            String fp = this.createXmlFile(filePath, revision);
            File file = new File(fp);
            
//            FileWriter fw = new FileWriter(file.getAbsoluteFile());
            
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF8"));
            
            StringBuilder builder = new StringBuilder();
            builder.append(header);
            builder.append(text);
            builder.append(footer);
            
            bw.write(builder.toString());
            bw.close();
            
            
//            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF8"));
//            out.append(header);
//            out.append(text);
//            out.append(footer);
//            
//            out.flush();
//            out.close();
            
            
            
        }
        catch (Exception e) 
        {
            e.printStackTrace();
        }
        
    }

	private Map<String, String> getUpdatedPrefixProperties(Map<String, String> properties, Entry<String, String> prefixSet) {
		Map<String, String> newProperties = new HashMap<String, String>();
		for(Entry<String, String> property : properties.entrySet()) {
			if(property.getKey().startsWith(prefixSet.getKey())) {
				newProperties.put(property.getKey().replaceFirst(prefixSet.getKey(), prefixSet.getValue()), property.getValue());
			} else {
				newProperties.put(property.getKey(), property.getValue());
			}
		}
		if(null != properties) {
			properties = newProperties;
		}
		return properties;
	}

	private List<String> getUpdatedPrefixAspects(List<String> aspects, Entry<String, String> prefixSet) {
		List<String> newAspects = new ArrayList<String>();
		for(String aspect : aspects) {
			if(aspect.startsWith(prefixSet.getKey())) {
				newAspects.add(aspect.replaceFirst(prefixSet.getKey(), prefixSet.getValue()));
			} else {
				newAspects.add(aspect);
			}
		}
		if(null != aspects) {
			aspects = newAspects;
		}
		return aspects;
	}

	private Map<String, String> getUpdatedProperties(Map<String, String> properties) {
		Map<String, String> newProperties = new HashMap<String, String>();
		for(Entry<String, String> property : properties.entrySet()) {
			if(this.updateTypesOrAspectsOrProperties.containsKey(property.getKey())) {
				newProperties.put(this.updateTypesOrAspectsOrProperties.get(property.getKey()), property.getValue());
			} else {
				newProperties.put(property.getKey(), property.getValue());
			}
		}
		if(null != properties) {
			properties = newProperties;
		}
		return properties;
	}

	private List<String> getUpdatedAspects(List<String> aspects) {
		List<String> newAspects = new ArrayList<String>();
		for(String aspect : aspects) {
			if(this.updateTypesOrAspectsOrProperties.containsKey(aspect)) {
				newAspects.add(this.updateTypesOrAspectsOrProperties.get(aspect));
			} else {
				newAspects.add(aspect);
			}
		}
		if(null != aspects) {
			aspects = newAspects;
		}
		return aspects;
	}
    
    
    /**
     * Format aspects
     * 
     * @param aspects
     * @return
     */
    private String formatAspects(List<String> aspects)
    {
                
        String dado = "";
        
        boolean flag = false;
        for (String string : aspects) 
        {
            if(flag)
            {
                dado += ",";
            }
            
            dado += string;
            flag = true;
        }
        
        return dado;
    }
    
    
    /**
     * Method to replace special character to html code
     * 
     * @param value {@link String} value of field
     * @return {@link String}
     */
    private String formatProperty(String value)
    {        
        
        //format &
        value = value.replaceAll("&", "&amp;");
        //format < and >
        value = value.replaceAll("<", "&lt;").replaceAll(">", "&gt;");
        
        return value;
    }
    
    
    /**
     * Method to see if file already exists
     * 
     * @param path The {@link String} path of file 
     * @return {@link Boolean}
     */
    private boolean isFileExist(String path)
    {
        File f=new File(path);
        
        if(f.exists())
        {
          return true;
        }

        return false;
    }
   
    private boolean isInvalidEmptyType(String key, String value) {
       	if(this.invalidEmptyProps.contains(key) && (null == value || value.isEmpty())) {
        	return true;
        }
       	return false;
    }
    private void setInvalidTypeProperties(NodeRef nodeRef, String type, List<String> aspects) {
    	if(this.processedTypesAndAspects.contains(type) && this.processedTypesAndAspects.containsAll(aspects)) {
    		return;
    	}
        Map<QName, PropertyDefinition> props = new HashMap<QName, PropertyDefinition>();
    	if(null != type && !type.isEmpty() && !this.processedTypesAndAspects.contains(type)) {
        	props.putAll(serviceRegistry.getDictionaryService().getType(getQNameFromPrefixString(type)).getProperties());
        	this.processedTypesAndAspects.add(type);
    	}
    	if(null != aspects && !aspects.isEmpty() && !this.processedTypesAndAspects.containsAll(aspects)) {
    		for(String aspect: aspects) {
	        	if(this.processedTypesAndAspects.contains(aspect)) {
	        		continue;
	        	}
	        	props.putAll(serviceRegistry.getDictionaryService().getAspect(getQNameFromPrefixString(aspect)).getProperties());
	        	this.processedTypesAndAspects.add(aspect);
    		}
    	}
    	for(Entry<QName, PropertyDefinition> entry : props.entrySet()) {
    		/*
    		if(entry.getValue().getDataType().getName().equals(DataTypeDefinition.DATE) ||
    				entry.getValue().getDataType().getName().equals(DataTypeDefinition.DATETIME) ||
    				entry.getValue().getDataType().getName().equals(DataTypeDefinition.INT) ||
    				entry.getValue().getDataType().getName().equals(DataTypeDefinition.FLOAT) ||
    				entry.getValue().getDataType().getName().equals(DataTypeDefinition.LONG) ||
    				entry.getValue().getDataType().getName().equals(DataTypeDefinition.DOUBLE)) {
    			this.invalidEmptyProps.add(getQNameToPrefixString(entry.getKey()));
    		}
    		*/
    		try {
    			DefaultTypeConverter.INSTANCE.convert(entry.getValue().getDataType(), "");
    		} catch (Exception e) {
    			this.invalidEmptyProps.add(getQNameToPrefixString(entry.getKey()));
    		}
    	}
    }
    
    private String getQNameToPrefixString(QName prop) {
    	return prop.toPrefixString(this.serviceRegistry.getNamespaceService());
    }
    private QName getQNameFromPrefixString(String qnameStr) {
    	return QName.createQName(qnameStr, this.serviceRegistry.getNamespaceService());
    }
    
}
