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
import org.dcom.core.services.DictionaryService;
import org.dcom.core.services.FileDictionaryService;
import org.dcom.core.compliancedocument.Table;
import org.dcom.core.compliancedocument.Row;
import org.dcom.core.compliancedocument.Cell;
import org.dcom.core.compliancedocument.TitleCell;
import org.dcom.core.compliancedocument.DataCell;
import org.dcom.ruleengine.core.CompilerUtils;
import org.dcom.ruleengine.core.DRLBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import org.dcom.core.compliancedocument.deserialisers.XMLComplianceDocumentDeserialiser;
import java.util.stream.Collectors;

/**
*This is the main class of the RASEDRL compiler, it reads a compliance document from the server, parses it and then compiles it into DRL.
*
*/
public class RASEDRLCompiler {
	
	
	private static Set<DRLBuilder> ruleSet;
	private static DictionaryService dictionary;
	private static HashSet<String> builtBoxes;
	
	public static void main(String[] args) {
			ComplianceDocument document = null;

				try {
					File documentFile = new File(args[0]);
					String cDocumentString = Files.readString(documentFile.toPath(), StandardCharsets.US_ASCII);
					document = XMLComplianceDocumentDeserialiser.parseComplianceDocument(cDocumentString);
					dictionary = new FileDictionaryService(new File(args[1]));
				} catch (IOException e) {
					e.printStackTrace();
				}
			
			document= RASEDocumentReferenceProcessor.processDocument(document);
			String validationResult = RASEValidator.validateDocument(document);
			if (validationResult != null ) {
				System.err.println("Found mixed boxes with multiple tags"+validationResult);
				System.exit(1);
			}
			List<RASEItem> structure = RASEExtractor.extractStructure(document);
			for (RASEItem i: structure) {
				if (i instanceof RASETag) {
					System.err.println("Found root tag!"+i.getId());
					System.exit(1);
				}
				if (i instanceof RASEBox &&  ((RASEBox)i).getType()!=RASEBox.REQUIREMENT_SECTION) {
					System.err.println("Found no requirement box at root");
					System.exit(1);
				}
			}
			
			builtBoxes = new HashSet<String>();

			ruleSet = new HashSet<DRLBuilder>();
			List<RASEBox> allItems = new ArrayList<RASEBox>();
			for (RASEItem i: structure) {
				compileRASE((RASEBox)i,null,null,"");	
				allItems.add((RASEBox)i);
			}	

			String documentBase =document.getMetaDataString("dcterms:coverage.spatial")+"/"+document.getMetaDataString("dcterms:type")+"/"+document.getMetaDataString("dcterms:title")+"/"+document.getMetaDataString("ckterms:version")+"/"+document.getMetaDataString("dcom:startSectionNumber");
			
			List<RASEBox> tables = RASEExtractor.extractTables(document,documentBase);
			for (RASEBox tBox: tables) compileRASE(tBox,null,null,"");
			allItems.addAll(tables);
			
			
			//meta pass - all that are applicable must also pass
			if (allItems.size() > 0) {
				DRLBuilder rule = new DRLBuilder();
				rule.newRuleSummary(documentBase,"","FINAL_PASS").newLine();
				boolean first=true;
				for (RASEBox i: allItems ) {
					if (first) first = false; else rule.newLine().and().newLine();
					rule.exists().checkResult(i.getDocumentReference()+"/"+i.getId(),"PASS");
				}
				rule.enginePass();
				ruleSet.add(rule);
				
				//meta fail - at least one that is applicable must fail
				rule = new DRLBuilder();
				rule.newRuleSummary(documentBase,"","FINAL_FAIL").newLine();
				first=true;
				for (RASEBox i: allItems ) {
					if (first) first = false; else rule.newLine().or().newLine();
					rule.exists().checkResult(i.getDocumentReference()+"/"+i.getId(),"FAIL");
				}
				rule.engineFail();
				ruleSet.add(rule);
			}
			
			CompilerUtils.outputDRLFile(ruleSet);
			System.exit(0);	
	}	
	
	private static String determineParent(List<RASEItem> tags,String setParent,String boxId,String upperParent) {
			Set<String> parent=new HashSet<String>();
			Set<String> allFound = new HashSet<String>();
			boolean foundObject= false;
			for (RASEItem i: tags) {
				RASETag tag =(RASETag)i;
				if (dictionary.containsObject(tag.getProperty())) {
					parent.add(tag.getProperty().toLowerCase());
					continue;
				}
				List<String> objects = dictionary.getObjectFromProperty(tag.getProperty());
				allFound.addAll(objects);
				if (objects.size()==0) {
					System.err.println("[Error] Item is not in dictionary! "+tag.getProperty());
					System.exit(1);
				}
				if (setParent!=null && !objects.contains(setParent)) {
					System.err.println("[Error] Incosistent Objects Found("+boxId+"):"+objects.get(0)+"->"+setParent);
					System.exit(1);
				}
				if (parent.size()==0) parent.addAll(objects);
				else parent.retainAll(objects);
				boolean found=false;
				for (String o: objects) {
					if (parent.contains(o)){
						found=true;
						break;
					}
				}
				if (!found) {
						System.err.println("[Error] Incosistent Objects Found("+boxId+")("+setParent+")"+parent.size()+":"+tag.getProperty());
				}
			}
			if (parent.size() ==0) {
				System.err.println("[Error] No Viable Parents Found:"+boxId);
				for (String s: allFound) System.err.println(s);
				System.exit(1);
			} else if (parent.size()==1) {
				return parent.iterator().next();
			} else {
				if (parent.contains(upperParent)) return upperParent;
				if (parent.contains(setParent)) return setParent;
				System.err.println("[Error] Too many Viable Parents Found box("+setParent+"):"+boxId);
				for (String s:parent) System.err.println(s);
				System.exit(1);
			}
			System.err.println("Could not determine parent for->"+boxId);
			return null;
	}
	
	public static String compileRASE(RASEBox box,String parent,String parentDocRef,String parentId) {
			if (builtBoxes.contains(box.getId())) {
					System.err.println("DuplicateBox:"+box.getId());
					return parent;
			} else builtBoxes.add(box.getId());
			boolean boxExists = box.getAllSubItems().stream().anyMatch(t -> t.isBox());
			boolean tagExists = box.getAllSubItems().stream().anyMatch(t -> t.isTag());
			if (boxExists && tagExists) box = RASEValidator.refactorBox(box);
			if (boxExists) {
				String newParent=parent;
				List<RASEItem> appliesBoxes = box.getAllSubItems().stream().filter(t -> t.getType() == RASEBox.APPLICATION_SECTION).collect(Collectors.toList());
				HashMap<RASEBox,String> boxParents = new 	HashMap<RASEBox,String>();
				RASEItem toRemove=null;
				for (RASEItem i: appliesBoxes) {
					newParent=compileRASE((RASEBox)i,newParent,box.getDocumentReference(),box.getId());
					boxParents.put((RASEBox)i,newParent);
					if (((RASEBox)i).getAllSubItems().size()==0) {
						box.removeSubItem(i);
						toRemove=i;
					}
				}
				appliesBoxes.remove(toRemove);
				List<RASEItem> selectBoxes = box.getAllSubItems().stream().filter(t -> t.getType() == RASEBox.SELECTION_SECTION).collect(Collectors.toList());
				for (RASEItem i: selectBoxes) {
					String p = compileRASE((RASEBox)i,newParent,box.getDocumentReference(),box.getId());
					boxParents.put((RASEBox)i,p);
				}
				List<RASEItem> exceptionBoxes = box.getAllSubItems().stream().filter(t -> t.getType() == RASEBox.EXCEPTION_SECTION).collect(Collectors.toList());
				for (RASEItem i: exceptionBoxes) {
					String p = compileRASE((RASEBox)i,newParent,box.getDocumentReference(),box.getId());
					boxParents.put((RASEBox)i,p);
				}
				List<RASEItem> requirementBoxes = box.getAllSubItems().stream().filter(t -> t.getType() == RASEBox.REQUIREMENT_SECTION).collect(Collectors.toList());
				for (RASEItem i: requirementBoxes) {
					String p = compileRASE((RASEBox)i,newParent,box.getDocumentReference(),box.getId());
					boxParents.put((RASEBox)i,p);
				}
			
				
				mainRules(box,newParent,parentDocRef+"/"+parentId,appliesBoxes,selectBoxes,exceptionBoxes,requirementBoxes);
				//transfer rules
				parentTransfer(box.getAllSubItems(),newParent,boxParents,box.getDocumentReference(),box.getId());
				generateFinalResultRules(box.getDocumentReference(),box.getId());
				
				return newParent;

			} else if (tagExists) {
				return compileRASETagBox(box,parent,parentDocRef,parentId);
			}
			return null;
	}
	
	private static void parentTransfer(List<RASEItem> boxes,String parent,HashMap<RASEBox,String> boxParents,String docRef,String boxId) {
		if (parent==null) parent="Building";
		for (RASEItem i: boxes) {
			if (i instanceof RASEBox) {
				RASEBox b = (RASEBox)i;
				if (boxParents.get(b)==null || !boxParents.get(b).equals(parent)){
					
						DRLBuilder rule = new DRLBuilder();
						rule.newRule(docRef,boxId,"TRANSFER"+b.getId()+"PASS").newLine();
						rule.forAllComparisonStart().getApplicable(b.getDocumentReference()+"/"+b.getId()).forAllComparisonEnd().objectComparison().getPass(b.getDocumentReference()+"/"+b.getId()).endObjectComparison().endForAll().newLine();
						rule.entity();
						if (parent!=null) rule.checkType(parent);
						rule.endEntity().newLine().objectPass(b.getDocumentReference()+"/"+b.getId());
						ruleSet.add(rule);
						
						//of if none applicable we pass!
						rule = new DRLBuilder();
						rule.newRule(docRef,boxId,"TRANSFER"+b.getId()+"PASS2").newLine();
						rule.notExist().existsObjectComparison().getApplicable(b.getDocumentReference()+"/"+b.getId()).endExistsObjectComparison().newLine();
						rule.entity();
						if (parent!=null) rule.checkType(parent);
						rule.endEntity().newLine().objectPass(b.getDocumentReference()+"/"+b.getId());
						ruleSet.add(rule);

						
						rule = new DRLBuilder();
						rule.newRule(docRef,boxId,"TRANSFER"+b.getId()+"FAIL").newLine();
						rule.exists().existsObjectComparison().getFail(b.getDocumentReference()+"/"+b.getId()).endObjectComparison().newLine();
						rule.entity();
						if (parent!=null) rule.checkType(parent);
						rule.endEntity().newLine().objectFail(b.getDocumentReference()+"/"+b.getId());
						ruleSet.add(rule);
				}
			}
		}
	}
	
	private static void mainRules(RASEBox box,String parent,String parentRef,List<RASEItem> applies,List<RASEItem> selected,List<RASEItem> excepted,List<RASEItem> requirements) {
		if (parent==null) parent="Building";

		//na
		DRLBuilder rule = new DRLBuilder();
		if (applies.size()>0 || selected.size()>0 || excepted.size()>0) {
			rule.newRule(box.getDocumentReference(),box.getId(),"NA").newLine().entity();
			if (!parentRef.startsWith("null") && box.getType()==RASEBox.REQUIREMENT_SECTION) rule.getApplicable(parentRef).and();
			if (!parentRef.startsWith("null")) rule.notGetFail(parentRef).and().notGetPass(parentRef).and();
			rule.notGetNotApplicable().and().notGetApplicable().and().checkType(parent);
			if (selected.size()+applies.size()+excepted.size()>0) {
				rule.and().oB();
				boolean first=true;
				for (RASEItem i: selected) {
					if (first) {
						first=false;
						rule.oB();
					} else rule.and();
					if (i instanceof RASETag) {
						RASETag t = (RASETag)i;
						rule.not().get(t.getSanitisedProperty(),t.getComparator(),t.getValue(),t.getUnit());
					} else if (i instanceof RASEBox)  {
						RASEBox b = (RASEBox)i;
						String id=b.getDocumentReference()+"/"+b.getId();
						rule.getFail(id);	
					}
				}
				if (!first) rule.cB();
	
				for (RASEItem i: applies) {
					if (!first) rule.or();
					else first=false;
					if (i instanceof RASETag) {
						RASETag t = (RASETag)i;
						rule.not().get(t.getSanitisedProperty(),t.getComparator(),t.getValue(),t.getUnit());
					} else if (i instanceof RASEBox)  {
						RASEBox b = (RASEBox)i;
						String id=b.getDocumentReference()+"/"+b.getId();
						rule.getFail(id);	
					}
				}
				for (RASEItem i: excepted) {
					if (!first) rule.or(); 
					else first=false;
					if (i instanceof RASETag) {
						RASETag t = (RASETag)i;
						rule.get(t.getSanitisedProperty(),t.getComparator(),t.getValue(),t.getUnit());
					} else if (i instanceof RASEBox)  {
						RASEBox b = (RASEBox)i;
						String id=b.getDocumentReference()+"/"+b.getId();
						rule.getPass(id);	
					}
				}			
				rule.cB();
			}
			rule.endEntity().newLine().objectNotApplicable();
			ruleSet.add(rule);
		}
		
		//a
		rule = new DRLBuilder();
		rule.newRule(box.getDocumentReference(),box.getId(),"A").newLine().entity();
		if (!parentRef.startsWith("null") && box.getType()==RASEBox.REQUIREMENT_SECTION) rule.getApplicable(parentRef).and();
		if (!parentRef.startsWith("null")) rule.notGetFail(parentRef).and().notGetPass(parentRef).and();
		rule.notGetApplicable().and().notGetNotApplicable().and().checkType(parent);
		for (RASEItem i: applies) {
			rule.and();
			if (i instanceof RASETag) {
				RASETag t = (RASETag)i;
				rule.get(t.getSanitisedProperty(),t.getComparator(),t.getValue(),t.getUnit());
			} else if (i instanceof RASEBox)  {
				RASEBox b = (RASEBox)i;
				String id=b.getDocumentReference()+"/"+b.getId();
				rule.getPass(id);	
			}
		}
		for (RASEItem i: excepted) {
			rule.and();
			if (i instanceof RASETag) {
				RASETag t = (RASETag)i;
				rule.not().get(t.getSanitisedProperty(),t.getComparator(),t.getValue(),t.getUnit());
			} else if (i instanceof RASEBox)  {
				RASEBox b = (RASEBox)i;
				String id=b.getDocumentReference()+"/"+b.getId();
				rule.getFail(id);	
			}
		}
		if (selected.size() > 0) {
			rule.and().oB();
			boolean first = true;
			for (RASEItem i: selected) {
				if (first) first = false; else rule.or();
				if (i instanceof RASETag) {
					RASETag t = (RASETag)i;
					rule.get(t.getSanitisedProperty(),t.getComparator(),t.getValue(),t.getUnit());
				} else if (i instanceof RASEBox)  {
					RASEBox b = (RASEBox)i;
					String id=b.getDocumentReference()+"/"+b.getId();
					rule.getPass(id);	
				}
			}
			rule.cB();
		}
		rule.endEntity().newLine().objectApplicable();
		ruleSet.add(rule);
		
		//pass
		rule = new DRLBuilder();
		rule.newRule(box.getDocumentReference(),box.getId(),"PASS").newLine().entity();
		if (!parentRef.startsWith("null") && box.getType()==RASEBox.REQUIREMENT_SECTION) rule.getApplicable(parentRef).and();
		if (!parentRef.startsWith("null")) rule.notGetFail(parentRef).and().notGetPass(parentRef).and();
		rule.notGetFail().and().notGetPass().and().getApplicable();
		for (RASEItem i: requirements) {
			rule.and();
			if (i instanceof RASETag) {
				RASETag t = (RASETag)i;
				rule.get(t.getSanitisedProperty(),t.getComparator(),t.getValue(),t.getUnit());
			} else if (i instanceof RASEBox)  {
				RASEBox b = (RASEBox)i;
				String id=b.getDocumentReference()+"/"+b.getId();
				rule.getPass(id);	
			}
		}
		rule.endEntity().newLine().objectPass();
		ruleSet.add(rule);
		
		//fail
		if (requirements.size() > 0) {
			rule = new DRLBuilder();
			rule.newRule(box.getDocumentReference(),box.getId(),"FAIL").newLine().entity();
			if (!parentRef.startsWith("null") && box.getType()==RASEBox.REQUIREMENT_SECTION) rule.getApplicable(parentRef).and();
			if (!parentRef.startsWith("null")) rule.notGetFail(parentRef).and().notGetPass(parentRef).and();
			rule.notGetFail().and().notGetPass().and().getApplicable().and().oB();
			boolean first = true;
			for (RASEItem i: requirements) {
				if (first) first = false; else rule.or();
				if (i instanceof RASETag) {
					RASETag t = (RASETag)i;
					rule.not().get(t.getSanitisedProperty(),t.getComparator(),t.getValue(),t.getUnit());
				} else if (i instanceof RASEBox)  {
					RASEBox b = (RASEBox)i;
					String id=b.getDocumentReference()+"/"+b.getId();
					rule.getFail(id);	
				}
			}
			rule.cB().endEntity().newLine().objectFail();
			ruleSet.add(rule);
		}
	}
	
	private static String compileRASETagBox(RASEBox box,String parent,String parentDocRef,String parentBoxId) {
		String newParent=null;
		// applies
		if (box.getDocumentReference()==null) {
			System.err.println("[Error] Found a box with no document reference:"+box.getId());
		}
		List<RASEItem> applies = box.getAllSubItems().stream().filter(t -> t.getType() == RASETag.APPLICATION).collect(Collectors.toList());
		List<RASEItem> selected = box.getAllSubItems().stream().filter(t -> t.getType() == RASETag.SELECTION).collect(Collectors.toList());
		List<RASEItem> excepted = box.getAllSubItems().stream().filter(t -> t.getType() == RASETag.EXCEPTION).collect(Collectors.toList());
		List<RASEItem> requirements = box.getAllSubItems().stream().filter(t -> t.getType() == RASETag.REQUIREMENT).collect(Collectors.toList());
		RASETag objectTag = null;
		for (RASEItem i: applies) {
			RASETag tag = (RASETag) i;
			//detect a context change and change parent
			if (dictionary.containsObject(tag.getProperty())) {
				newParent = tag.getProperty().toLowerCase();
				objectTag = tag;
			}
		}
		
		newParent = determineParent(box.getAllSubItems(),newParent,box.getId(),parent);
		if (objectTag!=null) {
			applies.remove(objectTag);
			box.removeSubItem(objectTag);
		}
		if (box.getAllSubItems().size()>0) {
			mainRules(box,newParent,parentDocRef+"/"+parentBoxId,applies,selected,excepted,requirements);
		
			generateFinalResultRules(box.getDocumentReference(),box.getId());
		}
			
		return newParent;
	}
	
	private static void generateFinalResultRules(String docRef,String id) {
		DRLBuilder rule = new DRLBuilder();
		//meta pass - all must pass
		rule.newRuleSummary(docRef,id,"META_PASS").newLine();
		rule.forAllComparisonStart().getApplicable(docRef+"/"+id).forAllComparisonEnd().objectComparison().getPass(docRef+"/"+id).endObjectComparison().endForAll();
		rule.enginePass();
		ruleSet.add(rule);
		
		//of if none applicable we pass!
		rule = new DRLBuilder();
		rule.newRuleSummary(docRef,id,"META_PASS2").newLine();
		rule.notExist().existsObjectComparison().getApplicable(docRef+"/"+id).endExistsObjectComparison().newLine();
		rule.enginePass();
		ruleSet.add(rule);
		
		//meta fail - at least one that is applicable must fail
		rule = new DRLBuilder();
		rule.newRuleSummary(docRef,id,"META_FAIL").newLine();
		rule.exists().existsObjectComparison().getFail(docRef+"/"+id);
		rule.endExistsObjectComparison().newLine().engineFail();
		ruleSet.add(rule);
	}
}