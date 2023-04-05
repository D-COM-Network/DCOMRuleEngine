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
import org.dcom.core.compliancedocument.Paragraph;
import org.dcom.core.compliancedocument.Section;
import org.dcom.core.compliancedocument.Table;
import org.dcom.core.compliancedocument.Figure;

/**
*This is the RASE document reference processes. If there are no document references in the document it adds it
*
*/

public class RASEDocumentReferenceProcessor {

	

	public static ComplianceDocument processDocument(ComplianceDocument document) {
		Counters counters = new Counters();
		if (document.hasMetaData("dcom:startSectionNumber")) counters.sectionCounter = Integer.parseInt(document.getMetaDataString("dcom:startSectionNumber"));
		String documentBase =document.getMetaDataString("dcterms:coverage.spatial")+"/"+document.getMetaDataString("dcterms:type")+"/"+document.getMetaDataString("dcterms:title")+"/"+document.getMetaDataString("ckterms:version");
		if (document.hasMetaData("ckterms:accessLocation")) documentBase=document.getMetaDataString("ckterms:accessLocation");
		processDocument(counters,document,documentBase);
		return document;
	}
	
	private static void filterDown(ComplianceItem item,String docRef) {
		for (int i=0; i < item.getNoSubItems();i++) {
			item.getSubItem(i).setMetaData("ckterms:accessLocation",docRef);
			filterDown(item.getSubItem(i),docRef);
		}
	}
	
	private static void processDocument(Counters counters,ComplianceItem item,String documentReference) {
		boolean setMeta = false;
		if (item instanceof Paragraph) {
				if (item.hasMetaData("numbered") && item.getMetaDataString("numbered").equals("global")) {
					documentReference+="/"+counters.paraCounter;
					counters.paraCounter++;
					setMeta = true;
				}
		} else if (item instanceof Section ) {
			if (item.hasMetaData("numbered") && item.getMetaDataString("numbered").equals("global")) {
				documentReference+="/"+counters.sectionCounter;
				counters.sectionCounter++;
				counters.paraCounter=1;
				setMeta = true;
			}else if (item.hasMetaData("dcterms:title")){
				documentReference+="/"+item.getMetaDataString("dcterms:title");
				setMeta = true;
			}
		} else if (item instanceof Table ) {
			documentReference +="/Table"+counters.tableCounter;
			setMeta = true;
			counters.tableCounter++;
		} else if (item instanceof Figure) {
			documentReference +="/Figure"+counters.figureCounter;
			counters.figureCounter++;
			setMeta = true;
		}
		if (setMeta && !item.hasMetaData("ckterms:accessLocation")) item.setMetaData("ckterms:accessLocation",documentReference);
		
		if (item instanceof Table || item instanceof	Figure) {
			//filter down the Id
			filterDown(item,documentReference);
			return;
		}
		
		if (item instanceof Paragraph && item.hasMetaData("numbered") && item.getMetaDataString("numbered").equals("global")) {
			//filter down the ID
			filterDown(item,documentReference);
			return;
		}
		if (!item.hasMetaData("ckterms:accessLocation")) item.setMetaData("ckterms:accessLocation",documentReference);
		
		for (int i=0; i < item.getNoSubItems();i++) processDocument(counters,item.getSubItem(i),documentReference);
		
	}

}


class Counters {
	
	public int sectionCounter =1;
	public int paraCounter =1;
	public int figureCounter=1;
	public int tableCounter=1;
	
}
