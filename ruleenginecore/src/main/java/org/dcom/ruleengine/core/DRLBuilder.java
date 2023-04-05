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

/**
* The class contains the functionality needed to build DRL rules.
*/
public class DRLBuilder {
		
		private StringBuffer rule;
		private String currentRule;
	
		public DRLBuilder(){
				rule=new StringBuffer();
		}
		
		public DRLBuilder newRuleSummary(String docRef,String id,String appender) {
			currentRule=CompilerUtils.sanitiseName(docRef)+"/"+(id);
			rule.append("rule \"").append(currentRule+"_"+appender).append("\"  agenda-group \"summary\"  when ");
			return this;
		}
		
		public DRLBuilder newRule(String docRef,String id,String appender) {
			currentRule=CompilerUtils.sanitiseName(docRef)+"/"+(id);
			rule.append("rule \"").append(currentRule+"_"+appender).append("\" agenda-group \"compute\" when ");
			return this;
		}
		
		public DRLBuilder newRuleSort(String docRef,String id,String appender) {
			currentRule=CompilerUtils.sanitiseName(docRef)+"/"+(id);
			rule.append("rule \"").append(currentRule+"_"+appender).append("\" agenda-group \"sort\"  when ");
			return this;
		}
		
		public DRLBuilder entity() {
			rule.append("$entity: RuleEngineComplianceObject( ");
			return this;
		}
		
		public DRLBuilder endEntity() {
			rule.append(")");
			return this;
		}
		
		public DRLBuilder newLine() {
			rule.append("\n");
			return this;
		}
		
		public DRLBuilder newTab() {
			rule.append("\t");
			return this;
		}
	
		public DRLBuilder and() {
			rule.append(" && ");
			return this;
		}
		
		public DRLBuilder or() {
			rule.append(" || ");
			return this;
		}
		
		public DRLBuilder notExist() {
			rule.append("not ");
			return this;
		}
		
		public DRLBuilder not() {
			rule.append("!");
			return this;
		}
		
		public DRLBuilder oB() {
			rule.append("(");
			return this;
		}
		
		public DRLBuilder cB() {
			rule.append(")");
			return this;
		}
		
		private void fetchingGeneration(String prop, String operator,String value,String unit) {
			rule.append("\"").append(CompilerUtils.sanitiseName(prop)).append("\",\"").append(operator).append("\",\"").append(value).append("\",\"").append(unit).append("\",\"").append(currentRule).append("\"");
		}
		
		public DRLBuilder get(String prop, String operator, String value,String unit) {
			rule.append("get(");
			fetchingGeneration(prop,operator,value,unit);
			rule.append(")");
			return this;
		}
		
		public DRLBuilder get(String prop, String operator, String value) {
			rule.append("get(");
			fetchingGeneration(prop,operator,value,"");
			rule.append(")");
			return this;
		}
		
		public DRLBuilder get(String prop, String value) {
			rule.append("get(");
			fetchingGeneration(prop,"==",value,"");
			rule.append(")");
			return this;
		}
		
		private DRLBuilder checkTemplate(String var,String target,boolean not){
			rule.append(var);
			if (not) rule.append(" not");
			rule.append(" contains \"").append(target).append("\"");
			return this;
		}
		
		public DRLBuilder checkType(String type){
			return checkTemplate("type",type,false);
		}
		
		public DRLBuilder notCheckType(String type){
			return checkTemplate("type",type,true);
		}
				
		public DRLBuilder getNotApplicable(String rId) {
			return checkTemplate("notApplicable",CompilerUtils.sanitiseName(rId),false);
		}
		
		public DRLBuilder notGetNotApplicable(String rId) {
			return checkTemplate("notApplicable",CompilerUtils.sanitiseName(rId),true);
		}
		
		public DRLBuilder notGetNotApplicable() {
			return checkTemplate("notApplicable",currentRule,true);
		}
	
		public DRLBuilder notGetApplicable() {
			return checkTemplate("applicable",currentRule,true);
		}
		
		public DRLBuilder getApplicable(String rId) {
			return checkTemplate("applicable",CompilerUtils.sanitiseName(rId),false);
		}
		
		public DRLBuilder getNotApplicable() {
			return checkTemplate("notApplicable",currentRule,false);
		}
		
		public DRLBuilder getApplicable() {
			return checkTemplate("applicable",currentRule,false);
		}
		
		public DRLBuilder getFail(String rId) {
			return checkTemplate("fail",CompilerUtils.sanitiseName(rId),false);
		}
		
		public DRLBuilder notGetFail(String rId) {
			return checkTemplate("fail",CompilerUtils.sanitiseName(rId),true);
		}
		
		public DRLBuilder notGetPass(String rId) {
			return checkTemplate("pass",CompilerUtils.sanitiseName(rId),true);
		}
		
		public DRLBuilder notGetFail() {
			return checkTemplate("fail",currentRule,true);
		}
		
		public DRLBuilder notGetPass() {
			return checkTemplate("pass",currentRule,true);
		}
		
		public DRLBuilder getPass(String rId) {
			return checkTemplate("pass",CompilerUtils.sanitiseName(rId),false);
		}
		
		public DRLBuilder forAll() {
			rule.append("forall  ( $entity: RuleEngineComplianceObject() ");
			return this;
		}
		
		public DRLBuilder forAllComparisonStart() {
			rule.append("forall  ( $entity: RuleEngineComplianceObject(");
			return this;
		}
		
		public DRLBuilder forAllComparisonEnd() {
			rule.append(") ");
			return this;
		}
		
		public DRLBuilder endForAll() {
			rule.append(") ");
			return this;
		}
		
		public DRLBuilder objectComparison() {
			rule.append("RuleEngineComplianceObject(this==$entity,");
			return this;
		}
		
		public DRLBuilder endObjectComparison() {
			rule.append(")");
			return this;
		}
		
		public DRLBuilder checkResult(String boxId,String result) {
			rule.append(" RuleEngineResult(ruleId=='").append(CompilerUtils.sanitiseName(boxId)).append("',result=='").append(result).append("') ");
			return this;
		}
		
		public DRLBuilder checkResult(String boxId) {
			rule.append(" RuleEngineResult(ruleId=='").append(CompilerUtils.sanitiseName(boxId)).append("') ");
			return this;
		}
		
		public DRLBuilder exists() {
			rule.append("exists");
			return this;
		}
		
		public DRLBuilder existsObjectComparison() {
			rule.append(" RuleEngineComplianceObject(");
			return this;
		}
	
		public DRLBuilder endExistsObjectComparison() {
			rule.append(")");
			return this;
		}
		
		private DRLBuilder insertTemplate(String type) {
			rule.append(" not RuleEngineResult(ruleId==\"").append(currentRule).append("\",result==\"").append(type).append("\") then insert(new RuleEngineResult(\"").append(currentRule).append("\",\"").append(type).append("\")); end");
			return this;
		}
		public DRLBuilder engineNa() {
			return insertTemplate("NA");
		}
		
		public DRLBuilder enginePass() {
			return insertTemplate("PASS");
		}
		
		public DRLBuilder engineFail() {
			return insertTemplate("FAIL");
		}
		
		public DRLBuilder startObjectTemplate() {
			rule.append("then modify($entity) {");
			return this;
		}
		
		public DRLBuilder endObjectTemplate() {
			rule.append("};end");
			return this;
		}
		
		private DRLBuilder objectTemplate(String type) {
			rule.append("set").append(type).append("(\"").append(currentRule).append("\")");
			return this;
		}
		
		private DRLBuilder objectTemplate(String type,String rID) {
			rule.append("set").append(type).append("(\"").append(rID).append("\")");
			return this;
		}
		
		public DRLBuilder objectFail() {
			startObjectTemplate();
			objectTemplate("Fail");
			endObjectTemplate();
			return this;
		}
		
		public DRLBuilder objectPass() {
			startObjectTemplate();
			objectTemplate("Pass");
			endObjectTemplate();
			return this;
		}
		
		public DRLBuilder objectFail(String rId) {
			rId=CompilerUtils.sanitiseName(rId);
			startObjectTemplate();
			objectTemplate("Fail",rId);
			endObjectTemplate();
			return this;
		}
		
		public DRLBuilder objectPass(String rId) {
			rId=CompilerUtils.sanitiseName(rId);
			startObjectTemplate();
			objectTemplate("Pass",rId);
			endObjectTemplate();
			return this;
		}
		
		public DRLBuilder objectNotApplicable() {
			startObjectTemplate();
			objectTemplate("NotApplicable");
			rule.append(",");
			objectTemplate("Pass");
			endObjectTemplate();
			return this;
		}
		
		public DRLBuilder objectApplicable() {
			startObjectTemplate();
			objectTemplate("Applicable");
			endObjectTemplate();
			return this;
		}
	
		public String toString() {
			return rule.toString();
		}
}