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


package org.dcom.ruleengine.core;

import org.dcom.core.services.ServiceLookup;
import org.dcom.core.services.ComplianceDocumentService;
import org.dcom.core.services.ComplianceDocumentIndex;
import org.dcom.core.compliancedocument.serialisers.XMLComplianceDocumentSerialiser;
import org.dcom.core.compliancedocument.ComplianceDocument;
import org.dcom.core.DCOM;
import java.util.Set;
import java.util.List;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;


/**
*This queries all compliance document services and produces a list of all currently available compliance documents for compilation
*
*/
public class DocumentListGenerator {
	
	public static void main(String[] args) {
			ServiceLookup lookupService=DCOM.getServiceLookup();
			Set<ComplianceDocumentService> documentServices=lookupService.getComplianceDocumentServices();
			for (ComplianceDocumentService service: documentServices) {
				ComplianceDocumentIndex documentIndex=service.getComplianceDocumentIndex();
				List<String> documentsOnService=documentIndex.getDocumentShortNames();
				for (String document: documentsOnService){
						if (!document.equals("BB100") && !document.equals("AD_PartL2A") && !document.equals("AD_PartM2")) continue;
						List<String> documentVersions=documentIndex.getDocumentVersionList(document);
						for (String version: documentVersions) {
							ComplianceDocument doc = null;
							try {
								doc = service.getComplianceDocument(documentIndex.getDocumentJurisdiction(document),documentIndex.getDocumentType(document),document,version);
							} catch (Exception e) {
									e.printStackTrace();
							}
							if (doc==null) continue;
							String sS = doc.getMetaDataString("dcom:startSectionNumber");
							if (sS==null) continue;
							int startSection = Integer.parseInt(sS);
							new File("./"+document+"_"+version).mkdirs();
							for (int i=0; i < doc.getNoSections();i++) {
								ComplianceDocument docA = null;
								try {
									docA = service.getComplianceDocument(documentIndex.getDocumentJurisdiction(document),documentIndex.getDocumentType(document),document,version,""+startSection);
								} catch (Exception e) {
									e.printStackTrace();
								}
								if (docA==null) continue;
								String content = XMLComplianceDocumentSerialiser.serialise(docA);
								try {
									Files.writeString( Paths.get("./"+document+"_"+version+"/Section"+startSection+".html"), content);
								} catch (Exception e) {
									e.printStackTrace();
								}
								startSection++;
							}
						
						}
	
				}
			}
	}	
}