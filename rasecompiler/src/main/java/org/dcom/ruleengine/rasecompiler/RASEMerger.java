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
import org.dcom.core.compliancedocument.serialisers.XMLComplianceDocumentSerialiser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.awt.Frame;


/**
*This is the main class of the RASEDRL metger, it merges seperate document sections into one section
*
*/
public class RASEMerger {
	
	public static void main(String[] args) throws IOException {
			int noSections=Integer.parseInt(JOptionPane.showInputDialog(null,"Please Enter the number of sections"));
			int startNo=Integer.parseInt(JOptionPane.showInputDialog(null,"What Section number does the document start at?"));
			
			ComplianceDocument document = new ComplianceDocument();
			document.setMetaData("dcom:startSectionNumber",""+startNo);
			
			for (int i=0; i < noSections;i++) {
				JOptionPane.showMessageDialog(null,"Now Please Select Section:"+(i+startNo));
				FileDialog fc = new FileDialog(new Frame(),"Please your document files",FileDialog.LOAD);
				fc.setMultipleMode(false);
				fc.setVisible(true);
				if (fc.getFiles().length==0) System.exit(1);
				File file = fc.getFiles()[0];
				String cDocumentString = Files.readString(file.toPath(), StandardCharsets.US_ASCII);
				ComplianceDocument section = XMLComplianceDocumentDeserialiser.parseComplianceDocument(cDocumentString);
				if (i==0) {
					//copy over metadata from first section
					for (String metaData: section.getMetaDataList()) {
						if (section.isListMetadata(metaData)) {
							for (String dataItem: section.getMetaDataList(metaData)) document.setMetaData(metaData,dataItem);
						} else {
							document.setMetaData(metaData,section.getMetaDataString(metaData));
						}
					}
				}
				for (int x=0; x < section.getNoSections();x++) document.addSection(section.getSection(x));
			}
			
			JOptionPane.showMessageDialog(null,"Please Select where to save your new document");
			FileDialog fc = new FileDialog(new Frame(),"Please Select your Document File",FileDialog.SAVE);
			fc.setVisible(true);
			if (fc.getFiles().length==0) System.exit(1);
			File file = fc.getFiles()[0];
			System.out.println("Writing New Document File");
			String data = XMLComplianceDocumentSerialiser.serialise(document);
			Files.writeString(file.toPath(), data,StandardCharsets.US_ASCII);
			System.exit(1);
			
	}	
}