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

import org.dcom.core.compliancedocument.ComplianceDocument;
import org.dcom.core.services.ServiceLookup;
import java.util.Set;
import org.dcom.core.services.ComplianceDocumentService;
import org.dcom.core.DCOM;
import java.net.URL;

/**
* The class holds various utility methods common to multiple compilers.
*
*/
public class CompilerUtils {
	
		public static ComplianceDocument fetchComplianceDocument(String url,String jurisdiction,String type, String name, String version) {
			ServiceLookup lookupService=DCOM.getServiceLookup();
			Set<ComplianceDocumentService> documentServices=lookupService.getComplianceDocumentServices();
			for (ComplianceDocumentService service: documentServices) {
				if (service.getURL().equals(url)) {
					ComplianceDocument loadedDocument=service.getComplianceDocument(jurisdiction,type,name,version);
					return loadedDocument;
				}
			}
			return null;
		}
		
		public static void outputDRLFile(Set<DRLBuilder> ruleSet) {
			System.out.println(generateDRLFileHeader());
			System.out.println("");
			for (DRLBuilder rule : ruleSet) {
				System.out.println(rule.toString());
				System.out.println("");
			}
		}

		private static String generateDRLFileHeader() {
			StringBuffer str= new StringBuffer();
			str.append("import org.dcom.ruleengine.core.RuleEngineComplianceObject;\n");
			str.append("import org.dcom.ruleengine.core.RuleEngineResult;\n");
			return str.toString();
		}
		
		static String sanitiseName(String input) {
				try {
					if (input.startsWith("http") || input.startsWith("Http")) input = new URL(input).getPath();
				} catch (Exception e) { } 
				
				String[] data = input.split(" ");
				if (data.length > 1 ) {
					StringBuilder builder = new StringBuilder();
					for (String d: data) {
						String cap = d.trim().substring(0, 1).toUpperCase() + d.substring(1);
						builder.append(cap);
					}
					input =  builder.toString();
				} 
				input = input.replaceAll("[^a-zA-Z0-9 -/_]", "");
				return input;
		}
}