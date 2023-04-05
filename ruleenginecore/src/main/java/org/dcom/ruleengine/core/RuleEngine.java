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

import java.util.HashMap;
import org.dcom.core.compliancedocument.utils.GuidHelper;
import org.dcom.core.services.DictionaryService;
import org.dcom.core.services.FileDictionaryService;
import org.dcom.core.services.DataSourceService;
import org.dcom.core.services.ComplianceCheckSettings;
import com.owlike.genson.Genson;
import com.owlike.genson.GensonBuilder;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.dcom.core.security.ServiceCertificate;
import java.nio.charset.StandardCharsets;
import java.io.File;
import com.owlike.genson.reflect.VisibilityFilter;
import com.owlike.genson.ext.javadatetime.JavaDateTimeBundle;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
* This is the main class of the rule engine, it takes care of managing multiple running compliance checks that the service may be engaged in.
*
*/

public class RuleEngine {
  
    private HashMap<String,RuleEngineComplianceCheck> complianceChecks;
    private DictionaryService dictionary;
    private ServiceCertificate myCert;
    
    private static final Logger LOGGER = LoggerFactory.getLogger( RuleEngine.class );

    public RuleEngine(ServiceCertificate _myCert) {
      //will load a templater dictionary
      myCert = _myCert;
      complianceChecks=new HashMap<String,RuleEngineComplianceCheck>();
    }
    
    public RuleEngine(ServiceCertificate _myCert, String dictionaryFile) {
      //will load a dictionary file
      myCert = _myCert;
      complianceChecks=new HashMap<String,RuleEngineComplianceCheck>();
      try {
        dictionary = new FileDictionaryService(new File(dictionaryFile));
      } catch (Exception e) {
        LOGGER.error("Cannot find Dictionary File+"+dictionaryFile);
      }
    }
    
    private void setupDynamicParameters(String guid,RuleEngineComplianceCheck check,ComplianceCheckSettings settings) {
      check.setDictionary(dictionary);
      if (settings.getModelServerType()!=null && settings.getModelServerType().equals("Solibri") && settings.getModelServerURL()!=null) {
				//we have a supported model server request the IDS
				LOGGER.info("Setting up Data Source");
				DataSourceService dataSource = new DataSourceService(settings.getModelServerURL(),myCert,guid);
        check.setDataSources(dataSource,myCert);
			}
    }
    
    public String createComplianceCheck(String guid,ComplianceCheckSettings settings) {
      RuleEngineComplianceCheck check=new RuleEngineComplianceCheck(settings,guid);
      setupDynamicParameters(guid,check,settings);
      complianceChecks.put(guid,check);
      LOGGER.info("Starting Compliance Check");
      try {
        check.initalise();
        LOGGER.info("Starting Engine");
        complianceChecks.get(guid).startEngines();
      } catch (Exception e) {
        e.printStackTrace();
      }
      return guid;
    }
    
    public String createComplianceCheck(ComplianceCheckSettings settings) {
        String guid=UUID.randomUUID().toString();
        return createComplianceCheck(guid,settings);
    }
    
    public RuleEngineComplianceCheck getComplianceCheck(String guid,String dataPath){
      if (complianceChecks.containsKey(guid)) return complianceChecks.get(guid);
      try {
        complianceChecks.put(guid,deserialiseComplianceCheck(guid,dataPath));
        setupDynamicParameters( guid,complianceChecks.get(guid),  complianceChecks.get(guid).getCheckSettings());
      } catch (Exception e) {
        e.printStackTrace();
      }
      return complianceChecks.get(guid);
    }
    
    private RuleEngineComplianceCheck deserialiseComplianceCheck(String guid,String dataPath) {
      try {
        String content = Files.readString(Paths.get(dataPath+File.separator+guid), StandardCharsets.US_ASCII);
        Genson genson=new GensonBuilder().useFields(true, VisibilityFilter.PRIVATE).useMethods(false).withBundle(new JavaDateTimeBundle()).create();
        RuleEngineComplianceCheck check = (RuleEngineComplianceCheck) genson.deserialize(content, RuleEngineComplianceCheck.class);
        check.setDictionary(dictionary);
        return check;
      } catch (Exception e) {
          e.printStackTrace();
          return null;
      }
    }
    
    public void serialiseComplianceCheck(String guid,String dataPath) {
      try {
        complianceChecks.get(guid).updateGlobalResultSet();
        String content = new GensonBuilder().useFields(true, VisibilityFilter.PRIVATE).useMethods(false).withBundle(new JavaDateTimeBundle()).create().serialize(complianceChecks.get(guid));
        Files.writeString(Paths.get(dataPath+File.separator+guid),content,StandardCharsets.US_ASCII);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  
}
