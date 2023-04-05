/*
Copyright (C) 2022 Cardiff University

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.

*/

package org.dcom.ruleengine.rasecompiler;
import org.dcom.core.compliancedocument.ComplianceDocument;

import javax.swing.JOptionPane;
import java.awt.FileDialog;
import java.io.File;
import java.io.IOException;
import org.dcom.core.compliancedocument.deserialisers.XMLComplianceDocumentDeserialiser;
import java.nio.file.Files;
import org.dcom.core.compliancedocument.ComplianceItem;
import org.dcom.core.compliancedocument.Table;
import org.dcom.core.compliancedocument.Figure;
import java.nio.charset.StandardCharsets;
import org.dcom.core.services.FileDictionaryService;
import org.dcom.core.services.DictionaryItem;
import java.awt.Frame;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;


/**
*This is the main class of the RASEDRL checker, it merges seperate document sections into one section
*
*/
public class RASEDictionaryCheck {
	
	public static void main(String[] args) throws IOException {
			FileDialog fc = new FileDialog(new Frame(),"Please Select your Dictionary File",FileDialog.LOAD);
			fc.setFilenameFilter(new DictionaryFileNameFilter());
			fc.setVisible(true);
			File[] files = fc.getFiles();
			if (files.length==0) System.exit(1);
			FileDictionaryService dictionary = new FileDictionaryService(files[0]);
			File dictionaryFileName = files[0];
			fc = new FileDialog(new Frame(),"Please your document file",FileDialog.LOAD);
			fc.setMultipleMode(true);
			fc.setVisible(true);
			if (fc.getFiles().length==0) System.exit(1);
			String baseRef = null;
			List<RASETag> raseTags= new ArrayList<RASETag>();
			for (File documentFile : fc.getFiles()) {
				String cDocumentString = Files.readString(documentFile.toPath(), StandardCharsets.US_ASCII);
				ComplianceDocument document = XMLComplianceDocumentDeserialiser.parseComplianceDocument(cDocumentString);
				document= RASEDocumentReferenceProcessor.processDocument(document);
				if (baseRef==null) baseRef=document.getMetaDataString("dcterms:coverage.spatial")+"/"+document.getMetaDataString("dcterms:type")+"/"+document.getMetaDataString("dcterms:title")+"/"+document.getMetaDataString("ckterms:version");
				raseTags.addAll(parseDocument(document));
				
				//now print out the Sections
				System.out.println("This document is:"+documentFile.getName());
				crawlDocument(document);
			}
			System.out.println("Read a total of:"+raseTags.size()+" tags");
			dictionary.clearEmptyObjects();
				
			//remove any properties that have the same names as objects
		
			for (String object: dictionary.getObjects()) {
				ArrayList<String> toRemove = new ArrayList<String>();
				for (DictionaryItem property : dictionary.getProperties(object)) {
					if (dictionary.getObjects().contains(property.getPropertyName())) {
						System.out.println("Removing Property with Same name as Object!:"+property.getPropertyName());
						toRemove.add(property.getPropertyName());
					}
				}
				for (String r: toRemove) dictionary.removeProperty(object,r);
			}
				
			//check for any tags in document not in dictionary
			for (RASETag tag: raseTags) {
				List<String> object = dictionary.getObjectFromProperty(tag.getProperty());
				if ( !(object.size()>0 || dictionary.containsObject(tag.getProperty()))) {
					System.out.println("!! "+tag.getProperty()+" not in dictionary ["+tag.getDocumentReference()+"/"+tag.getId()+"]");
				}
			}
			//remove any in dictionary that are not in document	
			for (String object: dictionary.getObjects()) {
				for (DictionaryItem property : dictionary.getProperties(object)) {
					boolean found = false;
					for (RASETag tag: raseTags) {
						if (tag.getProperty().toLowerCase().trim().equals(property.getPropertyName().toLowerCase().trim())){
							found=true;
						}
					}
					if (!found) {
						System.out.println("Deleting:"+object+":"+property.getPropertyName());
						dictionary.removeProperty(object,property.getPropertyName().toLowerCase().trim());
					}
				}
			}
			for (String object: dictionary.getObjects()) {
				for (DictionaryItem property : dictionary.getProperties(object)) {
						property.clearPossibleValues();
						property.clearComplianceDocmentReferences();
				}
			}
			
			//add / refresh metadata that may have been lost
			for (String object: dictionary.getObjects()) {
				for (DictionaryItem property : dictionary.getProperties(object)) {
					for (RASETag tag: raseTags) {
						if (tag.getProperty().toLowerCase().trim().equals(property.getPropertyName().toLowerCase().trim())){
								
								//sort the unit
								if (!tag.getUnit().equals("") && !property.containsUnit(tag.getUnit())) {
									if (property.getUnit().size()>1) System.out.println("[Notice] Mixed Units!");
									property.addUnit(tag.getUnit());
								}
							
								//sort the document reference 
								if (tag.getDocumentReference()!=null && !tag.getDocumentReference().equals("")) property.addComplianceDocumentReference(tag.getDocumentReference());
							
								//sort the possible values
								if (tag.getComparator().equals("==") || tag.getComparator().equals("excludes") || tag.getComparator().equals("includes") ) property.addPossibleValue(tag.getValue());

								
						}
					}
				}
			}
			
			//check for null document references
			for (String object: dictionary.getObjects()) {
				for (DictionaryItem property : dictionary.getProperties(object)) {
					boolean hasContent = false;
					for (String docRef: property.getComplianceDocumentReferences()) {
						if (docRef!=null) hasContent=true;
					}
					if (!hasContent) {
						System.out.println("No Document Reference For:"+property.getPropertyName());
						property.addComplianceDocumentReference(baseRef);
					}
				}
			}
			
			//now iterate the rase tree and look for any implied parameters we may need to add
			for (File documentFile : fc.getFiles()) {
				String cDocumentString = Files.readString(documentFile.toPath(), StandardCharsets.US_ASCII);
				ComplianceDocument document = XMLComplianceDocumentDeserialiser.parseComplianceDocument(cDocumentString);
				List<RASEItem> structure = RASEExtractor.extractStructure(document);
				System.out.println("Checking...:"+documentFile.getName());
				for (RASEItem i: structure) parseRase(i,dictionary,null);
	
			}
			
			//now populate the metadata
			
			for (String o: dictionary.getObjects()) {
					if (dictionary.getIfcType(o)==null || dictionary.getIfcType(o).equals("")) dictionary.setIfcType(o,"");
					if (dictionary.getIfcSubType(o)==null || dictionary.getIfcSubType(o).equals("")) dictionary.setIfcSubType(o,"");
					if (dictionary.getClassification(o)==null || dictionary.getClassification(o).equals("")) dictionary.setClassification(o,"");
					for (DictionaryItem p: dictionary.getProperties(o)) {
						p.setPropertySetName("DCOM_"+sanitiseName(o));
						p.setIfcDataItem(santiseAndShorten(p.getPropertyName()));
					}
			}
			
			int n = JOptionPane.showOptionDialog(null,"Do you want to save?","Dictionary",JOptionPane.YES_NO_OPTION, JOptionPane.PLAIN_MESSAGE,null,null,null);
			dictionary.clearEmptyObjects();
			if (n == JOptionPane.YES_OPTION) {
				System.out.println("Writing Dictionary File:"+dictionaryFileName.toString());
				dictionary.saveDictionary(dictionaryFileName);
			}
			
		
			
			System.exit(1);
			
	}	
	
	public static String parseRase(RASEItem item,FileDictionaryService dictionary,String parentObject) {
		String object = parentObject;
		if (item instanceof RASEBox) {
			RASEBox box = (RASEBox) item;
			List<RASEItem> items = box.getAllSubItems();
			//first cycle to identify applicabilities
			for (RASEItem i: items) {
				if (i instanceof RASEBox) {
					if (((RASEBox)i).getType() == RASEBox.APPLICATION_SECTION )object = parseRase(i,dictionary,object);
				} else {
					RASETag tag = (RASETag) i;
					if (tag.getType() == RASETag.APPLICATION) {
						if (dictionary.getObjects().contains(tag.getProperty())) object = tag.getProperty();
						if (parentObject !=null && object!=null && !object.equals(parentObject)) {
							if (!object.equals("Building")) System.out.println("Change of context found on "+parentObject+"->"+object+" at "+box.getId());
						}
					}
				}
			}
			//second cycle to process
			for (RASEItem i: items) {
				if (i instanceof RASEBox) parseRase(i,dictionary,object);
			}
		}
		return object;
	}
	
	public static List<RASETag> parseDocument(ComplianceItem item) {
			List<RASETag> tags =  RASEExtractor.extractAllTags(item);
			for (int i=0; i < item.getNoSubItems();i++) tags.addAll(parseDocument(item.getSubItem(i)));
			return tags;
	}
	
	private static String santiseAndShorten(String input) {
			input=sanitiseName(input);
			if (input.length() > 255) {
					System.out.println("Too long:"+input);
			}
			return input;
	}
	
	private static String sanitiseName(String input) {
			String[] data = input.split(" ");
			StringBuilder builder = new StringBuilder();
			for (String d: data) {
				String cap = d.substring(0, 1).toUpperCase() + d.substring(1);
				builder.append(cap);
			}
			input =  builder.toString();
			input = input.replaceAll("[^a-zA-Z]", "");
			return input;
	}
	
	private static void crawlDocument(ComplianceItem d) {
		if (d instanceof Table || d instanceof Figure) return ;		
		if (d.hasMetaData("ckterms:accessLocation")) System.out.println(d.getMetaDataString("ckterms:accessLocation"));
		for (int i=0; i < d.getNoSubItems(); i++) crawlDocument(d.getSubItem(i));
	}
}