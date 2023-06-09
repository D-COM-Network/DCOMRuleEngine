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
import org.dcom.core.compliancedocument.ComplianceItem;
import org.dcom.core.compliancedocument.Table;
import org.dcom.core.compliancedocument.Cell;
import org.dcom.core.compliancedocument.Row;
import org.dcom.core.compliancedocument.TitleCell;
import org.dcom.core.compliancedocument.DataCell;
import org.dcom.core.compliancedocument.Figure;
import org.dcom.core.compliancedocument.inline.*;
import java.util.List;
import java.util.ArrayList;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import org.xml.sax.InputSource;
import java.io.StringReader;
import java.net.URL;


/**
* This class contains the extractor to extract rase tags from paragraphs
*
*/
public class RASEExtractor {
	
		public static List<RASETag> extractAllTags(ComplianceItem item) {
				List<RASETag> tags = new ArrayList<RASETag>();
				if (item.hasMetaData("body")) {
					String bodyText = "<body>"+item.getMetaDataString("body")+"</body>";
					try {
						DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
						DocumentBuilder builder = factory.newDocumentBuilder();
						Document document = builder.parse(new InputSource(new StringReader(bodyText)));
						NodeList spans = document.getElementsByTagName("span");
						for (int i=0; i < spans.getLength();i++) {
								if ( spans.item(i).getNodeType() == Node.ELEMENT_NODE) {
									Element element = (Element)spans.item(i);
									if (element.hasAttribute("data-raseType") && element.hasAttribute("data-raseProperty")) {
											RASETag tag = produceTag(element,getDocumentReference(item));
											if (tag != null) tags.add(tag);
									}
								}
						}
				
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				return tags;
		}
		
		public static List<RASEBox> extractTables(ComplianceItem document,String documentBase) {
				List<Table> tables = extractTablesInternal(document);
				List<RASEBox> boxes = new ArrayList<RASEBox>();
				int tableNo=1;
				for (Table table: tables) {
					for (int i=0; i < table.getNoRows();i++) {
						Row r = table.getRow(i);
						if (r==null) continue;
						for (int j=0; j < r.getNoCells();j++) {
							Cell cell = r.getCell(j);
							if (cell==null) continue;
							if (cell instanceof DataCell) {
								List<Cell> cells = getTitleCellsInRow(table,i);
								cells.addAll(getTitleCellsInColumn(table,j));
								cells.add(cell);
								RASEBox box = new RASEBox("RequirementSection","Table"+tableNo+"_R"+i+"_C_"+j);
								box.setDocumentReference(documentBase+"/Table/"+tableNo);
								for (Cell c: cells) box.addAllSubItems(RASEExtractor.extractStructure(c));
								if (box.getNoSubItems() >0) boxes.add(box);
							}
						}
					}
					tableNo++;
				}
				return boxes;
		}
		
		private static List<Cell> getTitleCellsInColumn(Table t, int c) { 
			List<Cell> tC=new ArrayList<Cell>();
			for (int r=0; r < t.getNoRows();r++) {
					Row row = t.getRow(r);
					if (row.getNoCells()>c) 
						if (row.getCell(c) instanceof TitleCell) tC.add(row.getCell(c));
			}
			return tC;
		}
		
		private static List<Cell> getTitleCellsInRow(Table t, int r) { 
			List<Cell> tC=new ArrayList<Cell>();
			Row row = t.getRow(r);
			for (int i=0; i < row.getNoCells();i++) {
				if (row.getCell(i) instanceof TitleCell) tC.add(row.getCell(i));
			}
			return tC;
		}
		
		private static List<Table> extractTablesInternal(ComplianceItem item) {
			List<Table> items = new ArrayList<Table>();
			if (item instanceof Table) {
				items.add((Table)item);
			}  else {
				for (int i=0; i < item.getNoSubItems();i++) items.addAll(extractTablesInternal(item.getSubItem(i)));
			}
			return items;
		}
		
		public static List<InlineItem> extractStructure(ComplianceItem item) {
			List<InlineItem> items = new ArrayList<InlineItem>();
			if (item instanceof Table || item instanceof Figure) return items;
			RASEBox rootItem = null;
			if (item.hasMetaData("raseType")) {
				// i am a box!
				String type = item.getMetaDataString("raseType");
				if (type.equals("RequirementSection") || type.equals("SelectionSection") || type.equals("ApplicationSection") || type.equals("ExceptionSection")) {
					RASEBox box = new RASEBox(type,item.getMetaDataString("raseId"));
					box.setDocumentReference(getDocumentReference(item));
					items.add(box);
					rootItem = box;
				}
			}
			
			for (int i=0; i < item.getNoSubItems();i++) {
				List<InlineItem> thisItems = extractStructure(item.getSubItem(i));
				if (rootItem!=null) rootItem.addAllSubItems(thisItems);
				else items.addAll(thisItems);
			}
			if (item.hasMetaData("body")) {
					List<InlineItem> thisItems = extractStructure(item.getMetaDataString("body"),item.getMetaDataString("ckterms:accessLocation"));
					if (rootItem!=null) rootItem.addAllSubItems(thisItems);
					else items.addAll(thisItems);
			}
			
			return items;
		}
		
		private static RASETag produceTag(Element element,String ref) {
			//check there are no sub elements here
			NodeList children = element.getChildNodes();
			for (int i=0; i < children.getLength();i++) {
				List<InlineItem> items = crawlStructure(children.item(i),ref);
				if (items.size()>0) {
					 System.err.println("Error found RASE in a tag!["+items.get(0).getDocumentReference()+"/"+items.get(0).getId()+"]");
					 System.exit(0);
				}
			}
			String type = element.getAttribute("data-raseType");
			String property = element.getAttribute("data-raseProperty");
			if (type.equals("") || property.equals("")) {
				System.err.println("Found Empty Rase Tag!");
				return null;
			}
			if (type!=null && property!=null ) {
				RASETag tag = new RASETag(type,property,element.getAttribute("data-raseComparator"),element.getAttribute("data-raseTarget"),element.getAttribute("data-raseUnit"),element.getAttribute("id"),"");
				tag.setDocumentReference(ref);
				return tag;
			}
			return null;
		}
		
		private static List<InlineItem> crawlStructure(Node n, String ref) {
			List<InlineItem> items = new ArrayList<InlineItem>();
			if ( n.getNodeType() == Node.ELEMENT_NODE) {
				Element element = (Element)n;
				if ((element.getTagName().equalsIgnoreCase("span") || element.getTagName().equalsIgnoreCase("div")) && element.hasAttribute("data-raseType")) {
						String type = element.getAttribute("data-raseType");
						if (type!=null) {
							if (type.equals("RequirementSection") || type.equals("SelectionSection") || type.equals("ApplicationSection") || type.equals("ExceptionSection")) {
								RASEBox box = new RASEBox(type,element.getAttribute("id"));
								box.setDocumentReference(ref);
								items.add(box);
								NodeList children = n.getChildNodes();
								for (int i=0; i < children.getLength();i++) box.addAllSubItems(crawlStructure(children.item(i),ref));
							} else if (element.hasAttribute("data-raseType") && element.hasAttribute("data-raseProperty")) {
								RASETag tag = produceTag(element,ref);
								if (tag != null) items.add(tag);
							}
						}
				} else {
					NodeList children = n.getChildNodes();
					for (int i=0; i < children.getLength();i++) items.addAll(crawlStructure(children.item(i),ref));
				}
			}
			return items;
		}
		
		private static String getDocumentReference(ComplianceItem item) {
			String docRef =null;
			do {
				docRef = item.getMetaDataString("ckterms:accessLocation");
				if (docRef==null) {
					item=item.getParent();
					if (item ==null ) break;
				}
			} while (docRef==null);
			//filter out any URL elements
			try {
				if (docRef.startsWith("https") || docRef.startsWith("http")) docRef = new URL(docRef).getPath();
			} catch (Exception e) { } 
			return docRef;
		}
		
		private static List<InlineItem> extractStructure(String body,String ref) {
				List<InlineItem> items = new ArrayList<InlineItem>();
				String bodyText = "<body>"+body+"</body>";
				try {
					DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
					DocumentBuilder builder = factory.newDocumentBuilder();
					Document document = builder.parse(new InputSource(new StringReader(bodyText)));
					NodeList children = document.getChildNodes();
					for (int i=0; i < children.getLength();i++) items.addAll(crawlStructure(children.item(i),ref));
				} catch (Exception e) {
					e.printStackTrace();
				}
				return items;
		}
}