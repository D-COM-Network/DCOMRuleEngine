package org.dcom.ruleengine.core;

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

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Arrays;
import org.dcom.core.services.DictionaryService;
import java.io.IOException;
import org.dcom.core.services.ComplianceCheckSettings;
import org.dcom.core.services.ComplianceCheckEntityInformation;
import org.dcom.core.services.ComplianceCheckAnswer;
import org.dcom.core.services.ResultService;
import org.dcom.core.services.DataSourceService;
import org.dcom.core.security.ServiceCertificate;
import org.dcom.core.services.ComplianceCheckDataItem;
import org.dcom.core.services.ComplianceCheckFeedbackItem;
import org.dcom.core.services.ComplianceCheckResultItem;
import org.dcom.core.services.ComplianceCheckResultSubmission;
import org.dcom.core.services.ServiceLookup;
import org.dcom.core.DCOM;
import org.dcom.core.security.ServiceCertificate;
import java.io.File;
import java.util.Set;
import java.net.URL;
import com.owlike.genson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Collection;
import java.util.Queue;
import java.util.AbstractMap.SimpleEntry;
import java.util.concurrent.Future;
import java.util.LinkedList;
import java.util.concurrent.ExecutionException;


/**
*This is the programmatic representation of a currently running compliance check. It manages the starting/stopping of DROOLS, in RuleEngineExecutor and provides interfacing with the rule engine.
*
*/

public class RuleEngineComplianceCheck  {
	
		private ComplianceCheckSettings settings;
		private LocalDateTime lastAccessed;
		private transient HashMap<String,RuleEngineExecutor> engines;
		private boolean finished;
		private String id;
		private List<String> conditions;
		private String approval;
		private ArrayList<String> logs;
		private ArrayList<String> messages; 
		private ArrayList<String> rulesExecute;
		private HashMap<String,RuleEngineComplianceObject> entities;
		private HashMap<String,String> globalResultSet;
		private HashMap<String,ComplianceCheckResultSubmission> overrideData;
		private HashMap<String,LocalDateTime> timeData;
		private HashMap<String,String> identityData;
		
		private transient DictionaryService dictionary;
		private transient ServiceCertificate certificate;
		private transient DataSourceService dataSource;
		private transient Set<DataSourceService> dataSourceCache;
		
		private static final Logger LOGGER = LoggerFactory.getLogger( RuleEngineComplianceCheck.class );

    RuleEngineComplianceCheck(ComplianceCheckSettings _settings,String _guid) {
      settings=_settings;
			lastAccessed=LocalDateTime.now();
			id=_guid;
			engines=new HashMap<String,RuleEngineExecutor>();
			rulesExecute=new ArrayList<String>();
			logs=new ArrayList<String>();
			entities=new HashMap<String,RuleEngineComplianceObject>();
			globalResultSet=new HashMap<String,String>();
			overrideData=new HashMap<String,ComplianceCheckResultSubmission>();
			identityData=new HashMap<String,String>();
			timeData=new HashMap<String,LocalDateTime>();
		
			
			// start up the rule engine
			ArrayList<String> ruleNames=settings.getDocumentReference();
			for (int i=0; i < ruleNames.size();i++) {
				try {
					URL documentURL=new URL(ruleNames.get(i));
					String rName=documentURL.getPath().replace("/","_").replace("GB-ENG_3_","");
					if (rName.charAt(0)=='_') rName=rName.substring(1);
					rulesExecute.add(rName);
					logs.add("Starting Compliance Check For: "+rName);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			finished=false;
			conditions=new ArrayList<String>();
			approval="Pending";
			messages=new ArrayList<String>();
		}
		
		
		public RuleEngineComplianceCheck(@JsonProperty("settings") ComplianceCheckSettings _settings,@JsonProperty("lastAccessed") LocalDateTime _lastAccessed,@JsonProperty("finished") boolean _finished,@JsonProperty("id") String _id,@JsonProperty("conditions") List<String> _conditions,@JsonProperty("approval") String _approval,@JsonProperty("logs") ArrayList<String> _logs,@JsonProperty("messages") ArrayList<String> _messages,@JsonProperty("rulesExecute") ArrayList<String> _rulesExecute, @JsonProperty("entities") HashMap<String,RuleEngineComplianceObject> _entities, @JsonProperty("globalResultSet") HashMap<String,String> _globalResultSet,@JsonProperty("overrideData") HashMap<String,ComplianceCheckResultSubmission> _overrideData,@JsonProperty("timeData") HashMap<String,LocalDateTime> _timeData,@JsonProperty("identityData") HashMap<String,String> _identityData) {
			settings=_settings;
			lastAccessed=_lastAccessed;
			finished=_finished;
			id=_id;
			conditions=_conditions;
			approval=_approval;
			logs=_logs;
			messages=_messages;
			rulesExecute=_rulesExecute;
			entities=_entities;
			globalResultSet=_globalResultSet;
			overrideData=_overrideData;
			timeData=_timeData;
			identityData=_identityData;
		}
		
		public void updateGlobalResultSet() {
			if (this.engines==null) return;
			for (String rule: rulesExecute) {
					if (engines.get(rule)==null) continue;
					Collection<RuleEngineResult> results = engines.get(rule).getResults();
					for (RuleEngineResult r: results) {
						if (globalResultSet.get(r.getRuleId()) ==null || !globalResultSet.get(r.getRuleId()).equals(r.getResult())){
								globalResultSet.put(r.getRuleId(),r.getResult());
								logs.add("New Result:"+r.getRuleId()+"("+r.getResult()+")");
								timeData.put(r.getRuleId(),LocalDateTime.now());
						}
					}
			}
		}
		
		// as we do not save engine state we need to check engines are running
		
		private void restartEngines() {
			stopEngines();
			startEngines();
		}
		
		public ComplianceCheckSettings getCheckSettings() {
			return settings;
		}
		
		public void startEngines() {
				try {
						if (engines==null) engines=new HashMap<String,RuleEngineExecutor>();
						for (String rule: rulesExecute) {
								RuleEngineExecutor executor=new RuleEngineExecutor(rule,getEntities());
								engines.put(rule,executor);
						}
						for (RuleEngineExecutor engine : engines.values()) engine.start();
				} catch (Exception e) {
					e.printStackTrace();
				}
		}
		
		private void stopEngines() {
			if (engines==null) return;
			for (RuleEngineExecutor engine : engines.values()) engine.stopEngine();
			updateGlobalResultSet();
		}
			
		
		public void submitIdSet(List<String> idSet) {
			if (createEntities(idSet)) {
				//if there are new entities we need to re-run the engine
				if (!finished) restartEngines();
			}
		}
  
		
		public void submitApproval(String inApproval, List<String> inConditions) {
			approval=inApproval;
			List<ComplianceCheckResultItem> results=getResults(null,null,null);
			conditions.addAll(inConditions);
			if (approval.equals("Approved") || approval.equals("Rejected")) {
				stopEngines();
				finished=true;
				ServiceLookup lookup=DCOM.getServiceLookup();
				ServiceCertificate myCert=null;
				try {
					myCert=new ServiceCertificate(new File(DCOM.getDCOMCertificatePath()),DCOM.getDCOMCertificatePassword());
				} catch (Exception e) {
					e.printStackTrace();
					return;
				}
				Set<ResultService> resultServices=lookup.getResultServices();
				for (ResultService rs: resultServices) {
					rs.sendResults(settings.getUPRN(),id,inConditions,results,myCert.generateBearerToken());
					return;
				}
			}
		}
		
		public String getApproval() {
			return approval;
		}
		
		public List<String> getConditions() {
			return conditions;
		}
		
		public List<String> getLogEntries() {
			return logs;
		}
		
		public List<String> getMessages() {
			return messages;
		}
		
		public boolean isInProgress() {
			for (RuleEngineExecutor e: engines.values()) {
				if (e.isRunning()) return true;
			}
			return false;
		}
		
		public void submitMessage(String message) {
				messages.add(message);
		}
		
		public void initalise() throws InterruptedException,ExecutionException {
			if (dataSource!=null) {
				//fetch the initial set of objects and properties
				List<String> idSet = dataSource.getIDSet().get();
				logs.add("Fetched "+idSet.size()+" objects from BIM");
				HashMap<String,Integer> objectLists = new HashMap<String,Integer>();
				HashSet<String> removeList = new HashSet<String>();
				LOGGER.info("Fetching types");
				createEntities(idSet);
				for (RuleEngineComplianceObject entity: entities.values()) {
						if (entity.getType().size()==0) {
							//get the type of the entity
							Future<List<String>> data = dataSource.getData(entity.getId(),"type", "","");

							String t = data.get().get(0);
							if (t==null || t.equals("")) removeList.add(entity.getId());
							else {
								String[] types = t.split(":");
								LOGGER.info(t+"("+entity.getId()+")");
								for (String type: types) entity.setType(type.toLowerCase());
								if (!objectLists.containsKey(entity.getTypeString())) objectLists.put(entity.getTypeString(),0);
								objectLists.put(entity.getTypeString(),objectLists.get(entity.getTypeString())+1);
								entity.getFriendlyName();
							}

						}
				 }
				for (String r: removeList) entities.remove(r);
				logs.add("Loaded objects:");
				for (String oL:objectLists.keySet()) logs.add(oL+"="+objectLists.get(oL));
			}
			startEngines();
		}
		
		//methods that get called by the java interface
		
		public void setDataSources(DataSourceService _dataSource,ServiceCertificate certificate) {
			dataSource = _dataSource;
			dataSourceCache = DCOM.getServiceLookup().getDataSources();
			for (DataSourceService dS: dataSourceCache) dS.setDefaultCertificate(certificate);
			for (RuleEngineComplianceObject obj: entities.values()) {
				obj.setDataSourceCache(dataSourceCache);
				obj.setDataSource(dataSource);
			}
		}
		
		public Collection<RuleEngineComplianceObject> getEntities() {
			return entities.values();
		}
		
		public void setDictionary(DictionaryService _dictionary) {
			dictionary = _dictionary;
			for (RuleEngineComplianceObject obj: entities.values()) obj.setDictionary(dictionary);
		}
		
		
		public Set<String> getEntityList() {
			return entities.keySet();
		}
		
		public ComplianceCheckEntityInformation getEntity(String id) {
			return (ComplianceCheckEntityInformation)entities.get(id);
		}
		
		private boolean createEntities(List<String> ids) {
			boolean newEntity=false;
			for (String id:ids) {
				if (!entities.containsKey(id)) {
					//add it
					newEntity=true;
					RuleEngineComplianceObject obj=new RuleEngineComplianceObject(id);
					obj.setDataSource(dataSource);
					obj.setDictionary(dictionary);
					obj.setDataSourceCache(dataSourceCache);
					entities.put(id,obj);
				}
			}
			return newEntity;
		}
		
		public Set<String> getPropertiesForEntity(String entityId) {
			return entities.get(entityId).getProperties();
		}
		
		public Set<String> getEntitiesForClause(String clauseId) {
				Set<String> entitiesForClause=new HashSet<String>();
				for (String entity: entities.keySet()) {
						if (entities.get(entity).getRelevantClauses().contains(clauseId)) entitiesForClause.add(entity);
				}
				return entitiesForClause;
		}
		
		
		public List<ComplianceCheckResultItem> getResultsByClause(String clauseId,String entityId) {
				List<ComplianceCheckResultItem> results=new ArrayList<ComplianceCheckResultItem>();
				String attributation="RuleEngine";
				RuleEngineComplianceObject entity=entities.get(entityId);
				if (entityId==null) return results;
				for (String property:entity.getPropertiesForClause(clauseId)) {
					List<String> supportingFileData=entity.getSupportingFileData(property);
					List<String> supportingFileContentType=entity.getSupportingFileContentType(property);
					List<String> reasons=entity.getReasons(property);
					ComplianceCheckResultItem resultItem=new ComplianceCheckResultItem(property,entity.getResultTime(property),reasons,attributation,entity.getResult(property),supportingFileData,supportingFileContentType);
					results.add(resultItem);
					}			
				return results;
		}
		
		
		public List<ComplianceCheckResultItem> getResultsByEntity(String entityId,String propertyId) {
				List<ComplianceCheckResultItem> results=new ArrayList<ComplianceCheckResultItem>();
				String attributation="RuleEngine";
				RuleEngineComplianceObject entity=entities.get(entityId);
				if (entityId==null) return results;
				
				for (String clauseId:entity.getRelevantClauses()) {
					for (String property:entity.getPropertiesForClause(clauseId)) {
						if (!property.equals(propertyId)) continue;
						List<String> supportingFileData=entity.getSupportingFileData(propertyId);
						List<String> supportingFileContentType=entity.getSupportingFileContentType(propertyId);
						List<String> reasons=entity.getReasons(propertyId);
						ComplianceCheckResultItem resultItem=new ComplianceCheckResultItem(clauseId,entity.getResultTime(propertyId),reasons,attributation,entity.getResult(propertyId),supportingFileData,supportingFileContentType);
						results.add(resultItem);
					}
				}			
				return results;
		}
		
		public List<ComplianceCheckResultItem> getResults(LocalDateTime start,LocalDateTime end,String freeText) {
				updateGlobalResultSet();
				List<ComplianceCheckResultItem> results=new ArrayList<ComplianceCheckResultItem>();
				for (String clauseId:globalResultSet.keySet()) {
						boolean keep=true;
						LocalDateTime time=timeData.get(clauseId);
						if (start!=null && start.compareTo(time) > 0) keep=false;
						if (end!=null && end.compareTo(time) < 0) keep=false;
						if (time==null) time=LocalDateTime.now();
						List<String> reasons=null;
						String attributation=null;
						String result=globalResultSet.get(clauseId);
						List<String> supportingFileData=new ArrayList<String>();
						List<String> supportingFileContentType=new ArrayList<String>();
						if (overrideData.containsKey(clauseId)) {
							//use the manually provided data
							ComplianceCheckResultSubmission submittedData=overrideData.get(clauseId);
							reasons=submittedData.getReasons();
							attributation=identityData.get(clauseId);
							supportingFileData.add(submittedData.getSupportingFileData());
							supportingFileContentType.add(submittedData.getSupportingFileContentType());
						} else {
							//use the automatically generated data
							attributation="RuleEngine";
							reasons=new ArrayList<String>();
							//now we need to scan all entities for any results related to this
							for (String currentEntity:entities.keySet()) {
								RuleEngineComplianceObject entity=entities.get(currentEntity);
								for (String property:entity.getPropertiesForClause(clauseId)) {
									List<String> tmpList=entity.getReasons(property);
									if (tmpList!=null) reasons.addAll(tmpList);
									tmpList=entity.getSupportingFileData(property);
									if (tmpList!=null)	supportingFileData.addAll(tmpList);
									tmpList=entity.getSupportingFileContentType(property);
									if (tmpList!=null) supportingFileContentType.addAll(tmpList);
								}
							}
						}
						if (freeText!=null) {
							boolean foundOne=false;
							for (String r: reasons) {
								if (r.contains(freeText)) foundOne=true;
							}
							if (!foundOne) keep=false;
						}		
						ComplianceCheckResultItem resultItem=new ComplianceCheckResultItem(clauseId,time,reasons,attributation,result,supportingFileData,supportingFileContentType);
						if (keep) results.add(resultItem);	
				}
				return results;
		}
		
		public void submitResults(List<ComplianceCheckResultSubmission> results,String attribution) {
			boolean retVal=false;
			for (ComplianceCheckResultSubmission result: results) {
				if (overrideData.containsKey(result.getComplianceDocumentReference())) retVal=true;
				overrideData.put(result.getComplianceDocumentReference(),result);
				globalResultSet.put(result.getComplianceDocumentReference(),result.getResult());
				timeData.put(result.getComplianceDocumentReference(),LocalDateTime.now());
				identityData.put(result.getComplianceDocumentReference(),attribution);
			}
			if (retVal && !finished) {
				resetData();
				restartEngines();
			}
		}
		
		private void resetData() {
			for (RuleEngineComplianceObject entity : entities.values()) entity.resetData();
		}
		
		public void submitAnswers(List<ComplianceCheckAnswer> answers) {
			boolean returnVal = false;
			for (ComplianceCheckAnswer dataItem : answers) {
				 if (entities.containsKey(dataItem.getId())) {
					 RuleEngineComplianceObject entity=entities.get(dataItem.getId());
					 returnVal=entity.setAnswer(dataItem);
				 }
			}
			if (returnVal && !finished) {
				resetData();
				restartEngines();
			}
		}
		
		public void submitData(List<ComplianceCheckDataItem> answers) {
			 boolean returnVal=false;
			 for (ComplianceCheckDataItem dataItem : answers) {
					if (entities.containsKey(dataItem.getId())) {
						RuleEngineComplianceObject entity=entities.get(dataItem.getId());
						returnVal=entity.setDataItem(dataItem);
					}
			 }
			 if (returnVal && !finished) {
 				resetData();
 				restartEngines();
 			}
		}
}