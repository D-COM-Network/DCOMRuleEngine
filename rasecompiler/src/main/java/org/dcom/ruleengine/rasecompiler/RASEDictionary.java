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
import org.dcom.core.compliancedocument.Table;
import org.dcom.core.compliancedocument.Row;
import org.dcom.core.compliancedocument.Cell;
import org.dcom.core.compliancedocument.TitleCell;
import org.dcom.core.compliancedocument.DataCell;
import javax.swing.JOptionPane;
import java.awt.FileDialog;
import java.awt.Frame;
import java.io.File;
import java.util.Set;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.io.IOException;
import org.dcom.core.compliancedocument.deserialisers.XMLComplianceDocumentDeserialiser;
import org.dcom.core.services.FileDictionaryService;
import org.dcom.core.services.DictionaryItem;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.math.BigDecimal;
import java.util.stream.Collectors;

/**
*This is the main class of the RASEDRL preprocessor, it performs various helper activities to get a document ready for rule generation
*
*/
public class RASEDictionary {
	
	private static FileDictionaryService dictionary;
	
	public static void main(String[] args) throws IOException {
			ArrayList<Object> dictionaryData=new ArrayList<Object>();
			int n = JOptionPane.showOptionDialog(null,"Have you already created a dictionary.json?","Dictionary",JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE,null,null,null);
			if (n == JOptionPane.YES_OPTION) {
					FileDialog fc = new FileDialog(new Frame(),"Please Select your Dictionary File",FileDialog.LOAD);
					fc.setFilenameFilter(new DictionaryFileNameFilter());
					fc.setVisible(true);
					File[] files = fc.getFiles();
					if (files.length==0) System.exit(1);
					dictionary = new FileDictionaryService(files[0]);
			} else {
					dictionary = new FileDictionaryService();
					//add default objects
					dictionary.addObject("Site");
					dictionary.addObject("Project");
					dictionary.addObject("Building");
			}

			
			JOptionPane.showMessageDialog(null,"Now Please Select Your Document FIle");
		
			FileDialog fc = new FileDialog(new Frame(),"Please your document file",FileDialog.LOAD);
			fc.setVisible(true);
			if (fc.getFiles().length==0) System.exit(1);
			File documentFile = fc.getFiles()[0];
			//start parsing the files
			String cDocumentString = Files.readString(documentFile.toPath(), StandardCharsets.US_ASCII);
			ComplianceDocument document = XMLComplianceDocumentDeserialiser.parseComplianceDocument(cDocumentString);
			document= RASEDocumentReferenceProcessor.processDocument(document);
			String validationResult = RASEValidator.validateDocument(document);
			if (validationResult != null ) {
				JOptionPane.showMessageDialog(null,"Found mixed boxes with multiple tags at:"+validationResult);
				System.exit(1);
			}
		
			List<RASEItem> structure = RASEExtractor.extractStructure(document);
			System.out.println("Checking...:"+documentFile.getName());
			RASEBox box = new RASEBox("RequirementSection","ROOT");
			box.addAllSubItems(structure);
			parseRase(box,null,0);
		
			List<RASEBox> tables = RASEExtractor.extractTables(document,"");
			for (RASEBox tBox: tables) parseRase(tBox,null,0);
			

			
			saveDictionary();
	
			System.exit(1);
			
	}	
	
	public static String parseRase(RASEBox box,String parent, int indent) {
		boolean boxExists = box.getAllSubItems().stream().anyMatch(t -> t.isBox());
		boolean tagExists = box.getAllSubItems().stream().anyMatch(t -> t.isTag());
		if (boxExists && tagExists) box = RASEValidator.refactorBox(box);
		System.out.println(indent(indent)+box.toStringShort());
		String newParent=parent;
		if (boxExists) {
		
			// applies
			List<RASEBox> appliesBoxes = box.getAllSubItems().stream().map(t -> (RASEBox) t).filter(t -> t.getType() == RASEBox.APPLICATION_SECTION).collect(Collectors.toList());
			for (RASEBox i: appliesBoxes) newParent=parseRase(i,newParent,indent+1);
			List<RASEBox> remainingBoxes = box.getAllSubItems().stream().map(t -> (RASEBox) t).filter(t -> t.getType() != RASEBox.APPLICATION_SECTION).collect(Collectors.toList());
			for (RASEBox i: remainingBoxes) parseRase(i,newParent,indent+1);
			
		} else if (tagExists) {
			List<RASETag> applies = box.getAllSubItems().stream().map(t -> (RASETag) t).filter(t -> t.getType() == RASETag.APPLICATION).collect(Collectors.toList());
			List<RASETag> remainingTags = new ArrayList<RASETag>();
			
			for (RASETag tag: applies) {
				System.out.println(tag);
				if (dictionary.getObjects().contains(tag.getSanitisedProperty())) newParent = tag.getSanitisedProperty();
				else if (!dictionary.isNotObject(tag.getSanitisedProperty()) && tag.getValue().equals("true")){
						Object[] options = { "Yes", "No","Save First!" };
						boolean finished = false;
						while (!finished) {
							int n = JOptionPane.showOptionDialog(null,"We detected a possible new object: "+tag.getProperty()+" Please confirm if this is an object?","New Object",JOptionPane.YES_NO_OPTION, JOptionPane.PLAIN_MESSAGE,null,options,null);
							
							if (n == JOptionPane.YES_OPTION) {
								dictionary.addObject(tag.getSanitisedProperty());
								newParent = tag.getSanitisedProperty();
								finished=true;
							} else if (n==JOptionPane.NO_OPTION){
								dictionary.addNotAnObject(tag.getSanitisedProperty());
								remainingTags.add(tag);
								finished=true;
							} else {
								saveDictionary();
							}
						}
				} else remainingTags.add(tag);
			}
			remainingTags.addAll(box.getAllSubItems().stream().map(t -> (RASETag) t).filter(t -> t.getType() != RASETag.APPLICATION).collect(Collectors.toList()));
			if (remainingTags.size()==0) return newParent;
			if (newParent == null || newParent.equals(parent)) {
				String[] choices=null;
				if (newParent==null ) {
						choices = new String[4];
						choices[0]= "Building";
						choices[1]= "Project";
						choices[2]= "Site";
						choices[3]= "Save First!";
				} else {
						choices = new String[5];
						choices[0]= parent;
						choices[1]= "Building";
						choices[2]= "Project";
						choices[3]= "Site";
						choices[4]= "Save First!";
				}
				//one of the generic objects
				String str = formmatedTagText(box.getId(),remainingTags,"Please choose what object to allocate to",newParent,indent);
				boolean finished=false;
				while (!finished) {
					String input = (String) JOptionPane.showInputDialog(null,str,"Object Allocation", JOptionPane.QUESTION_MESSAGE, null,choices,choices[0]); 
					if (input.equals("Save First!")) {
						saveDictionary();
					} else {
						finished=true;
						for (RASETag tag: remainingTags) addDictionaryItem(input,tag);
					}
				}
			
			} else {
				String str = formmatedTagText(box.getId(),remainingTags,"Will be allocated to <b>"+newParent+"</b> object",newParent,indent);
				//must be the declared object
				JOptionPane.showMessageDialog(null, str,"Object Allocation", JOptionPane.INFORMATION_MESSAGE);
				for (RASETag tag: remainingTags) addDictionaryItem(newParent,tag);
			}
			
		}
		return newParent;
	}
	
	public static void addDictionaryItem(String object,RASETag tag) {
		Set<DictionaryItem> items = dictionary.getProperties(object);
		for (DictionaryItem item: items) {
		 	if (item.getPropertyName().equals(tag.getSanitisedProperty())) {
					item.addComplianceDocumentReference(tag.getDocumentReference());
					item.setDataType(determineDataType(tag,item.getDataType()));
					return;
			}
		}
		// we didn't find add new
		DictionaryItem newItem = new DictionaryItem(tag.getSanitisedProperty());
		newItem.addComplianceDocumentReference(tag.getDocumentReference());
		newItem.setDataType(determineDataType(tag,null));
		dictionary.addProperty(object,newItem);
	
	}

	public static String formmatedTagText(String boxId,List<RASETag> tags,String message,String parent,int indent) {
		StringBuilder str=new StringBuilder();
		str.append("<html><body><b>Box ID:"+boxId+"</b><ul>");
		for (RASETag tag: tags) {
			System.out.println(indent(indent+1)+tag.toStringShort()+"-->Object:"+parent);
			str.append("<li>").append(tag.toStringShort()).append("</li>");
		}
		str.append("</ul>"+message+"</body></html>");
		return str.toString();
	}
	
	public static String determineDataType(RASETag tag,String currentType) {
			if (currentType != null && currentType.equals("string")) return "string";
			if (currentType != null && !currentType.equals("number")) {
				if (tag.getValue().equalsIgnoreCase("true") || tag.getValue().equalsIgnoreCase("false")) {
					return "boolean";
				}
			}
			try {
				BigDecimal number = new BigDecimal(tag.getValue());
				return "number";
			} catch (NumberFormatException e) {
				if (tag.getValue().equalsIgnoreCase("true") || tag.getValue().equalsIgnoreCase("false")) {
					return "boolean";
				}
				return "string";
			}
	}
	
	public static String indent(int i) {
		StringBuilder str= new StringBuilder();
		for (int x=0; x<i;x++) str.append("-");
		return str.toString();
	}
	
	public static void saveDictionary() {
		try {
			JOptionPane.showMessageDialog(null,"Please Select where to save your dictionary file");
			FileDialog fc = new FileDialog(new Frame(),"Please Select your Dictionary File",FileDialog.SAVE);
			fc.setFilenameFilter(new DictionaryFileNameFilter());
			fc.setVisible(true);
			File[] files = fc.getFiles();
			if (files.length==0) System.exit(1);
			dictionary.saveDictionary(files[0]);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}