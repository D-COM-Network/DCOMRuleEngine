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

import org.dcom.core.services.ComplianceCheckEntityInformation;
import org.dcom.core.services.ComplianceCheckFeedbackItem;
import org.dcom.core.services.ComplianceCheckDataItem;
import org.dcom.core.services.ComplianceCheckRequiredDataItem;
import org.dcom.core.services.ComplianceCheckAnswer;
import org.dcom.core.services.DictionaryService;
import org.dcom.core.services.DictionaryItem;
import org.dcom.core.services.DataSourceService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import com.owlike.genson.annotation.JsonProperty;
import java.util.Set;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
* This extends ComplianceCheckEntityInformation (from DCOMCore) with the extra information and functionality needed to use entities within the rule engine.
*
*/
public class RuleEngineComplianceObject extends ComplianceCheckEntityInformation  {

  private HashMap<String,Boolean> propertiesCache;
  private HashMap<String,String> missValues;
  private HashMap<String,List<String>> fileData;
  private HashMap<String,List<String>> fileTypes;
  private HashMap<String,HashSet<String>> engineVariables;
  private HashMap<String,List<String>> engineFeedback;
  private HashMap<String,Set<String>> clauseOccurance;
  private HashMap<String,LocalDateTime> timeData;
  
  private transient DataSourceService dataSource;
  private transient DictionaryService dictionary;
  private transient Set<DataSourceService> dataSourceCache;
  private transient HashSet<String> pass;
  private transient HashSet<String> fail;
  private transient HashSet<String> applicable;
  private transient HashSet<String> notApplicable;
  private transient HashSet<String> rdCache ;
  
  private static final Logger LOGGER = LoggerFactory.getLogger( RuleEngineComplianceObject.class );


  public RuleEngineComplianceObject(String _id) {
      super(_id);
      engineVariables=new HashMap<String,HashSet<String>>();
      engineFeedback=new HashMap<String,List<String>>();
      missValues=new HashMap<String,String>();
      fileData=new HashMap<String,List<String>>();
      fileTypes= new HashMap<String,List<String>>();
      clauseOccurance=new HashMap<String,Set<String>>();
      propertiesCache=new HashMap<String,Boolean>();
      timeData=new HashMap<String,LocalDateTime>();
      pass = new HashSet<String>();
      engineVariables.put("Pass",pass);
      fail=new HashSet<String>();
      engineVariables.put("Fail",fail);
      applicable = new HashSet<String>();
      engineVariables.put("Applicable",applicable);
      notApplicable = new HashSet<String>();
      engineVariables.put("NotApplicable",notApplicable);
      rdCache = new HashSet<String>();
  }
  public RuleEngineComplianceObject(@JsonProperty("id") String _id,@JsonProperty("type") HashSet<String> _type,@JsonProperty("friendlyName") String _friendlyName,@JsonProperty("propertiesCache")  HashMap<String,Boolean> _propertiesCache,@JsonProperty("missValues")  HashMap<String,String> _missValues,@JsonProperty("fileData") HashMap<String,List<String>> _fileData, @JsonProperty("fileTypes") HashMap<String,List<String>> _fileTypes, @JsonProperty("engineVariables") HashMap<String,HashSet<String>> _engineVariables,@JsonProperty("engineFeedback") HashMap<String,List<String>> _engineFeedback,@JsonProperty("clauseOccurance") HashMap<String,Set<String>> _clauseOccurance,@JsonProperty("timeData") HashMap<String,LocalDateTime> _timeData) {
    super(_id);
    for (String t: _type) setType(t.toLowerCase());
    setFriendlyName(_friendlyName);
    engineVariables=_engineVariables;
    engineFeedback=_engineFeedback;
    missValues=_missValues;
    fileData=_fileData;
    fileTypes=_fileTypes;
    clauseOccurance=_clauseOccurance;
    propertiesCache=_propertiesCache;
    timeData=_timeData;
    pass=engineVariables.get("Pass");
    notApplicable=engineVariables.get("NotApplicable");
    fail=engineVariables.get("Fail");
    applicable=engineVariables.get("Applicable");
    rdCache = new HashSet<String>();
  }

  //methods call by the server side code
  
  public void setDataSource(DataSourceService _dataSource) {
    dataSource=_dataSource;
  }
  
  public void setDataSourceCache(Set<DataSourceService> _dataSourceCache) {
    dataSourceCache = _dataSourceCache;
  }
  
  public void setDictionary(DictionaryService _dictionary) {
    dictionary=_dictionary;
  }
  
  public void resetData() {
    for (String k: engineVariables.keySet()) engineVariables.get(k).clear();
    for (String k: engineFeedback.keySet()) engineVariables.get(k).clear();
  }
  
  
  public Set<String> getPropertiesForClause(String clause) {
      Set<String> propsForClause=new HashSet<String>();
      for (String prop:clauseOccurance.keySet()){
        if (clauseOccurance.get(prop).contains(clause)) propsForClause.add(prop);
      }
      return propsForClause;
  }
  
  public Set<String> getProperties() {
      Set<String> props=new HashSet<String>();
      for (String properties:propertiesCache.keySet()) props.add(properties.split(":")[0]);
      return props;
  }
  
  public Set<String> getRelevantClauses() {
    Set<String> clauses=new HashSet<String>();
    for (Set<String> clauseSet:clauseOccurance.values()) clauses.addAll(clauseSet);
    return clauses;
  }
  
  public boolean setAnswer(ComplianceCheckAnswer answer) {
    boolean answerValue;
    boolean returnVal=false;
    if (answer.getAnswer().equals("true")) {
      answerValue=true;
    } else if (answer.getAnswer().equals("false")) {
      answerValue=false;
    } else return false;
    if (propertiesCache.containsKey(answer.getPropertyId())) {
      returnVal=true;
    }
    propertiesCache.put(answer.getPropertyId(),answerValue);
    missValues.put(answer.getPropertyId(),answer.getMissValue());
    if (!fileData.containsKey(answer.getPropertyId())) fileData.put(answer.getPropertyId(),new ArrayList<String>());
    if (!fileTypes.containsKey(answer.getPropertyId())) fileTypes.put(answer.getPropertyId(),new ArrayList<String>());
    fileData.get(answer.getPropertyId()).add(answer.getSupportingFileData());
    fileTypes.get(answer.getPropertyId()).add(answer.getSupportingFileContentType());
    timeData.put(answer.getPropertyId(),LocalDateTime.now());

    for (int i=0; i < requiredData.size();i++) {
      //remove from RequiredData
      if (requiredData.get(i).getId().equals(answer.getPropertyId())) {
        requiredData.remove(requiredData.get(i));
        break;
      }
    }
    return returnVal;
  }

  public boolean setDataItem(ComplianceCheckDataItem data) {
    //this particular rule engine implementation does not make use of direct data input so this is not implemented
    return false;
  }
    
  public String getFriendlyName() {
		if (friendlyName.equals("TBC")) {
      	try {
          friendlyName = dataSource.getData(getId(),"name", "","").get().get(0);
        } catch (Exception e) {
          friendlyName="TBC";
        }
    }
    return friendlyName;
	}
  
  public List<String> getReasons(String property) {
    return engineFeedback.get(property);
  }
  
  public List<String> getSupportingFileData(String property) {
    List<String> returnData = fileData.get(property);
    if (returnData==null) {
      for (String k:fileData.keySet()) {
        if (k.startsWith(property+":")) return fileData.get(k);
      }
    }
    return returnData;
  }

  public List<String> getSupportingFileContentType(String property) {
    List<String> returnData = fileTypes.get(property);
    if (returnData==null) {
      for (String k:fileTypes.keySet()) {
        if (k.startsWith(property+":")) return fileTypes.get(k);
      }
    }
    return returnData;
  }

  public LocalDateTime getResultTime(String property) {
    if (timeData.containsKey(property)) timeData.get(property).toString();
    for (String pC :timeData.keySet()) {
      if (pC.startsWith(property)) return timeData.get(pC);
    }
    return LocalDateTime.now();
  }
  
  public String getResult(String property) {
    if (propertiesCache.containsKey(property)) propertiesCache.get(property).toString();
    for (String pC :propertiesCache.keySet()) {
      if (pC.startsWith(property)) return propertiesCache.get(pC).toString();
    }
    return "";
  }
  
  
  // utility functions to be called from rule engine

  public synchronized boolean get(String variable, String comparator, String target,String unit,String rule) {
    try {
      //this will look up the property and fetch from data source via the dictionary
      if (propertiesCache.containsKey(variable+":"+target)) return propertiesCache.get(variable+":"+target);
      if (!clauseOccurance.containsKey(variable)) clauseOccurance.put(variable,new HashSet<String>());
      if (!engineFeedback.containsKey(variable)) engineFeedback.put(variable,new ArrayList<String>());
      
      clauseOccurance.get(variable).add(rule);
      

      DictionaryItem selectedItem=dictionary.getProperty(getType(),variable);
      if (selectedItem==null) {
          LOGGER.error("Could not find "+variable+" for "+rule+" on "+getId()+getTypeString());
          return false;
      }
      
      DataSourceService selectedDs = null;
      for (DataSourceService dS: dataSourceCache) {
        if (selectedItem.getApplication()!=null && dS.getName().startsWith(selectedItem.getApplication())){
          selectedDs = dS;
          break;
        }
      }
      if (selectedDs==null) {
        if (selectedItem.getApplication()!=null)   LOGGER.info("Could not find datasource for "+selectedItem.getApplication());
        selectedDs = dataSource;
      }
      selectedDs = dataSource;
      engineFeedback.get(variable).add("Aquired from "+(selectedDs.getName()==null?"BIM":selectedDs.getName()));
      String logText="["+(selectedDs.getName()==null?"BIM":selectedDs.getName())+"]["+getId()+"]"+variable+comparator+target+unit;
      ComplianceCheckAnswer answer = selectedDs.getAnswer(getId(),variable,comparator,target,unit,rule).get().get(0);
      if (answer.isJob()) {
      
          try {
            Thread.sleep(5000);
          } catch (Exception e) {
            e.printStackTrace();
          }
          answer=selectedDs.getAnswerFromJobId(getId(),answer.getJobId()).get().get(0);
      } 
      
      if (answer.getAnswer().equals("unknown") || answer.isJob()) {
        logText+="->Unknown";
        if (!rdCache.contains(variable+":"+target)) {
          ComplianceCheckRequiredDataItem rD= new ComplianceCheckRequiredDataItem(variable+":"+target,variable,unit,rule);
          requiredData.add(rD);
          rdCache.add(variable+":"+target);
        }
      } else {
        logText+="->"+answer.getAnswer();
        setAnswer(answer);
      }
      if (answer.getAnswer().equalsIgnoreCase("false") || answer.getAnswer().equalsIgnoreCase("Unknown")) LOGGER.info(logText);
    
      while (!propertiesCache.containsKey(variable+":"+target)) {
        try {
          Thread.sleep(1000);
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    
      return propertiesCache.get(variable+":"+target);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return false;
  }
  
  
  public void setNotApplicable(String clauseName) {
    notApplicable.add(clauseName);
    //LOGGER.info(clauseName+"("+getId()+")"+getTypeString()+"->NA");
  }
  
  public void setApplicable(String clauseName) {
    applicable.add(clauseName);
    //LOGGER.info(clauseName+"("+getId()+")"+getTypeString()+"->A");
  }

  public void setPass(String clauseName) {
    pass.add(clauseName);
   //LOGGER.info(clauseName+"("+getId()+")"+getTypeString()+"->Pass");
  }

  public void setFail(String clauseName) {
    fail.add(clauseName);
    //LOGGER.info(clauseName+"("+getId()+")"+getTypeString()+"->Fail");
  }

  public Set<String> getNotApplicable () {
    return notApplicable;
  }
  
  public Set<String> getApplicable() {
    return applicable;
  }

  public Set<String> getFail() {
    return fail;
  }

  public Set<String> getPass () {
    return pass;
  }
}