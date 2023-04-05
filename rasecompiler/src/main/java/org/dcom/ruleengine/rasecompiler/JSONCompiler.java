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
import java.util.HashMap;
import org.dcom.ruleengine.core.CompilerUtils;
import org.dcom.ruleengine.core.DRLBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.io.File;
import com.owlike.genson.Genson;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Set;
import java.io.IOException;


public class JSONCompiler {
	
	public static void main(String[] args) {
			HashMap<String,Object> ruleData =  null;
			try {
				File documentFile = new File(args[0]);
				String cDocumentString = Files.readString(documentFile.toPath(), StandardCharsets.US_ASCII);
				ruleData =  new Genson().deserialize(cDocumentString, HashMap.class);
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);
			}
			Set<DRLBuilder> ruleSet = new HashSet<DRLBuilder>();
			HashSet<String> clausesComplete = new HashSet<String>();
			for (Object o: ruleData.values()) {
				HashMap<String,Object> ruleData2 = (HashMap<String,Object> )o;
				ArrayList<Object> rules = (ArrayList<Object>) ruleData2.get("record");
				String prevCommand=null;
				String prevRule=null;
				String prevStep=null;
				String currentClause=null;
				HashSet<String> rulesComplete = new HashSet<String>();
				for (Object r: rules) {
					HashMap<String,Object> rule = (HashMap<String,Object>) r;
					DRLBuilder builder = new DRLBuilder();
					if (!rule.containsKey("_RCmd")) continue;
					if (currentClause==null) currentClause = (String)rule.get("_Cl_Pncon");
					if (!currentClause.equals((String)rule.get("_Cl_Pncon"))) {
						clausesComplete.add(currentClause);
						
						builder = builder.newLine().newRule("JSON",(currentClause),"Pass").newLine().newTab();
						boolean first=true;
						for (String c: rulesComplete) {
							if (first) first=false;
							else builder=builder.and();
							builder=builder.not().getFail(c);
						}
						builder = builder.newLine().enginePass().newLine();
						
						builder = builder.newLine().newRule("JSON",(currentClause),"Fail").newLine().newTab();
						first=true;
						for (String c: rulesComplete) {
							if (first) first=false;
							else builder=builder.or();
							builder=builder.getFail(c);
						}
						builder = builder.newLine().engineFail().newLine();
						
						currentClause = (String)rule.get("_Cl_Pncon");
						rulesComplete=new HashSet<String>();
					
					}
					String ruleId = ""+((String)rule.get("_Cl_Pncon"))+"_"+rule.get("_Rule_Number");
					rulesComplete.add(ruleId);
					builder = builder.newLine().newRule("JSON",((String)rule.get("_Cl_Pncon")),""+rule.get("_Rule_Number")).newLine();
					if (prevCommand!= null && prevCommand.equals("IS") &&  prevStep!= null && prevStep.equals("NEXT")) builder = builder.newTab().getPass(prevRule).newLine();
					if (rule.get("_RCmd").equals("IS")) {
						builder = builder.newTab().existsObjectComparison().get(((String)rule.get("_RNounStr")),"true",ruleId).newLine();	
					} else if (rule.get("_RCmd").equals("TO")) {
						builder = builder.newTab().getPass((String)rule.get("_RNounStr")).newLine();
					} else if (rule.get("_RCmd").equals("RUN")) {
						builder = builder.newTab().getPass("Rule-Para"+((String)rule.get("_RNounStr"))+"-1");
					} else if (rule.get("_RCmd").equals("XRUN")) {	
						builder = builder.newTab().getPass("Document"+((String)rule.get("_RNounStr")));
					} else System.out.println("Invalid Command");
					
					builder = builder.enginePass().newLine();
					String step=(String)rule.get("_RCmdStep");
					if (step.equals("NEXT")) ;
					else if (step.equals("GOTO")) {
					} else System.out.println("Invalid Step:"+step);
					prevCommand="IS";
					prevRule=ruleId;
					prevStep=step;
					ruleSet.add(builder);
				}
			}
			DRLBuilder builder = new DRLBuilder();
			builder = builder.newLine().newRule("JSON",(args[0]),"Pass").newLine().newTab();
			boolean first=true;
			for (String c: clausesComplete) {
				if (first) first=false;
				else builder=builder.and();
				builder=builder.not().getFail(c);
			}
			builder = builder.newLine().enginePass().newLine();
			
			builder = builder.newLine().newRule("JSON",(args[0]),"Fail").newLine().newTab();
			first=true;
			for (String c: clausesComplete) {
				if (first) first=false;
				else builder=builder.or();
				builder=builder.getFail(c);
			}
			builder = builder.newLine().engineFail().newLine();
			
			ruleSet.add(builder);
			CompilerUtils.outputDRLFile(ruleSet);
			System.exit(0);	
	}	
}