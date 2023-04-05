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



import java.awt.FileDialog;
import java.io.File;
import java.io.IOException;
import org.dcom.core.services.FileDictionaryService;
import org.dcom.core.services.DictionaryItem;
import java.awt.Frame;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.util.StringJoiner;

import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Row;

/**
*This is the main class of the RASEDRL metger, it merges seperate document sections into one section
*
*/
public class RASESpreadsheet {
	
	public static void main(String[] args) throws IOException {
			FileDialog fc = new FileDialog(new Frame(),"Please Select your Dictionary File",FileDialog.LOAD);
			fc.setFilenameFilter(new DictionaryFileNameFilter());
			fc.setVisible(true);
			File[] files = fc.getFiles();
			if (files.length==0) System.exit(1);
			FileDictionaryService dictionary = new FileDictionaryService(files[0]);
			File dictionaryFileName = files[0];
			fc = new FileDialog(new Frame(),"Please select your spreadsheet file",FileDialog.SAVE);
			fc.setFilenameFilter(new SpreadsheetFileFilter());
			fc.setFile("dictionary.xls");
			fc.setVisible(true);
			if (fc.getFiles().length==0) System.exit(1);
			Workbook workbook = WorkbookFactory.create(false); 
			
			
			for (String object: dictionary.getObjects()) {
					Sheet sheet = workbook.createSheet(sanitiseName(object));
					Row row1= sheet.createRow(0);
					row1.createCell(0).setCellValue("Template Category");
					row1.createCell(1).setCellValue(object);
					Row row2= sheet.createRow(1);
					row2.createCell(0).setCellValue("Template Name");
					row2.createCell(1).setCellValue("DCOM "+object);
					Row row3= sheet.createRow(2);
					row3.createCell(0).setCellValue("Uniclass 2015 Code");
					row3.createCell(1).setCellValue(dictionary.getClassification(object));
					Row row4= sheet.createRow(3);
					row4.createCell(0).setCellValue("Template Description");
					row4.createCell(1).setCellValue(object);
					Row row5= sheet.createRow(4);
					row5.createCell(0).setCellValue("IfcEntity");
					row5.createCell(1).setCellValue(dictionary.getIfcType(object));
					Row row6= sheet.createRow(5);
					row6.createCell(0).setCellValue("IfcSubEntity");
					row6.createCell(1).setCellValue(dictionary.getIfcSubType(object));
					Row row7= sheet.createRow(6);
					Row row8= sheet.createRow(7);
					
					String[] names ={"Common Attribute","Property","Property Description","Example Values","Allowed Values","Source","Type or Instance?","IdBsDD","IdBsDDConcept","is Range?","Property Set Name","Property Set Description","IFCDataItem","PropertySetShortDesc","Unit Name","Unit Expression","DataType","Unit Description","Unit Measurement","Application Model","Related Compliance Resource"};
					for (int i=0; i < names.length;i++) 	row8.createCell(i).setCellValue(names[i]);
					
					int i=8;
					for (DictionaryItem property: dictionary.getProperties(object)) {
							Row innerRow= sheet.createRow(i);
							innerRow.createCell(0).setCellValue(property.getPropertyName()); // common name 
							innerRow.createCell(1).setCellValue(sanitiseName(property.getPropertyName()));
							innerRow.createCell(2).setCellValue(property.getPropertyName());
							StringJoiner sj = new StringJoiner(",");
							for (String s: property.getPossibleValues()) sj.add(s);
							if (property.getDataType().equals("boolean")) {
								innerRow.createCell(3).setCellValue("true,false"); //example values
								innerRow.createCell(4).setCellValue("true,false"); //allowed values	
							} else if (property.getDataType().equals("string")) {
								innerRow.createCell(3).setCellValue(sj.toString()); //example values
								innerRow.createCell(4).setCellValue(sj.toString()); //allowed values	
							}
							
							innerRow.createCell(6).setCellValue("Instance");
							innerRow.createCell(9).setCellValue("False");
							innerRow.createCell(10).setCellValue(property.getPropertySetName());
							innerRow.createCell(11).setCellValue("DCOM "+object+" Compliance Checking PSET");
							innerRow.createCell(12).setCellValue(property.getIfcDataItem());
							
							if (property.getDataType().equals("boolean")) {
								innerRow.createCell(14).setCellValue("Boolean");
								innerRow.createCell(15).setCellValue("(TRUE|FALSE)");
							} else 	{
								innerRow.createCell(14).setCellValue("Word-Text");
								innerRow.createCell(15).setCellValue("*");
							}
							
							if (property.getUnit().size()>0) {
									innerRow.createCell(15).setCellValue(property.getUnit().get(0));
							}
							
							innerRow.createCell(16).setCellValue(property.getDataType()); // data type
							if (property.getApplication()==null || property.getApplication().equals("")) innerRow.createCell(19).setCellValue("BIM");
							else innerRow.createCell(19).setCellValue(property.getApplication());
							sj = new StringJoiner(",", "[", "]");
							for (String s: property.getComplianceDocumentReferences()) {
								if (s!=null) sj.add(s);
							}
							innerRow.createCell(20).setCellValue(sj.toString());
							
							
							i++;
					}
					for (int x=0; x < 21;x++) sheet.autoSizeColumn(x);
			}
			
			
			FileOutputStream out = new FileOutputStream(fc.getFiles()[0]);
      workbook.write(out);
      out.close();
      
			System.exit(1);
			
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

}

class SpreadsheetFileFilter implements FilenameFilter {
	
	public boolean accept(File file,String name) {
						if (name.endsWith(".xlsx")) {
							 return true;
						}
						return false;
		 }
}