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
import org.dcom.core.compliancedocument.ComplianceItem;
import org.dcom.core.compliancedocument.inline.RASEBox;
import org.dcom.core.compliancedocument.inline.RASETag;
import org.dcom.core.compliancedocument.inline.InlineItem;
import java.util.List;

/**
*This is the RASE document reference processes. If there are no document references in the document it adds it
*
*/

public class RASEValidator {

	
	public static RASEBox refactorBox(RASEBox box) {
		//refactor to solve issue convert single tag into a box - change tag type etc..
		long noTags = box.getAllSubItems().stream().filter(t -> t instanceof RASETag).count();
		if (noTags > 1) {
			System.err.println("Invalid Tags!"+box.getId());
			System.err.println(noTags);
			System.exit(0);
		}
		String newType="";
		RASETag tag = (RASETag) box.getAllSubItems().stream().filter(t -> t instanceof RASETag).findFirst().orElse(null);
		if (tag.getType()==RASETag.REQUIREMENT) newType="RequirementSection" ;
		else if (tag.getType()==RASETag.SELECTION) newType="SelectionSection";
		else if (tag.getType()==RASETag.EXCEPTION) newType="ExceptionSection";
		else if (tag.getType()==RASETag.APPLICATION) newType="ApplicationSection";
	
		RASEBox newBox = new RASEBox(newType,tag.getId());
		newBox.setDocumentReference(tag.getDocumentReference());
		String tagType="Requirement";
		if (tag.getType()==RASETag.APPLICATION) tagType="Application";
		RASETag newTag = new RASETag(tagType,tag.getProperty(),tag.getComparator(),tag.getValue(),tag.getUnit(),tag.getId()+"a","");
		newTag.setDocumentReference(tag.getDocumentReference());
		newBox.addSubItem(newTag);
		box.addSubItem(newBox);
		box.removeSubItem(tag);
		//System.out.println("Creating:"+newBox.toString());
		//System.out.println("Creating:"+newTag.toString());
		return box;
	}

	public static String validateDocument(ComplianceDocument document) {
		try {
			List<InlineItem> InlineItems =  RASEExtractor.extractStructure(document);
			processStructure("ROOT",InlineItems);
		} catch (Exception e) {
			return e.getMessage();
		}
		return null;
	}
	
	private static void processStructure(String id,List<InlineItem> items) throws Exception{
		
		//we can deal with a single mixed tag - but not multiple
		boolean hasBox = false;
		int noTags = 0;
		
		for (InlineItem item: items) {
				if (item instanceof RASEBox) hasBox=true;
				if (item instanceof RASETag) noTags++;
		}
		if (noTags > 1 && hasBox ) {
			throw new Exception(id);
		}
		for (InlineItem item: items)  if (item instanceof RASEBox) processStructure(item.getId(), ((RASEBox)item).getAllSubItems());
		
	}

}