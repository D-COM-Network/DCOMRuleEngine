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
package org.dcom.ruleengine;

import org.dcom.core.security.ServiceCertificate;
import org.dcom.core.DCOM;
import org.slf4j.Logger;
import java.io.File;
import org.slf4j.LoggerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.dcom.core.servicehelper.ServiceBaseInfo;
import javax.ws.rs.ApplicationPath;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.dcom.core.servicehelper.CORSFilter;
import org.dcom.core.services.ServiceLookup;
import org.dcom.ruleengine.core.RuleEngine;
import uk.gov.service.notify.NotificationClient;

/**
* The startup class of the rule engine web service, this configures, sets global variables and then starts the restful web service.
*
*/
@ApplicationPath("/*") // set the path to REST web services
public class RuleEngineService extends ResourceConfig {
  
    private static final Logger LOGGER = LoggerFactory.getLogger( RuleEngine.class );

    public RuleEngineService() {
        //creat the service certificate
        if (!DCOM.checkDCOMCertificatePassword() || !DCOM.checkDCOMCertificatePath()) {
          LOGGER.error("Certificate Variables Not Defined");
          System.exit(0);
        }
        ServiceCertificate myCert=null;
        try {
          myCert=new ServiceCertificate(new File(DCOM.getDCOMCertificatePath()),DCOM.getDCOMCertificatePassword());
        } catch (Exception e) {
          e.printStackTrace();
          System.exit(1);
        }

        
        //create base service info
        final ServiceBaseInfo serviceBaseInfo=new ServiceBaseInfo(ServiceBaseInfo.NAME,ServiceBaseInfo.DESCRIPTION,ServiceBaseInfo.OPERATOR,ServiceBaseInfo.HOSTNAME,ServiceBaseInfo.PORT,"DCOM_SERVICE_DATA_PATH","DCOMDictionaryPath");
        
        final RuleEngine ruleEngine=new RuleEngine(myCert,serviceBaseInfo.getProperty("DCOMDictionaryPath"));
        
        //notify - for email sending
        
        NotificationClient notifyClient = new NotificationClient(DCOM.getEnvironmentVariable("DCOM_SERVICE_NOTIFY_KEY"));
        
        
        LOGGER.info("Registering to Service Lookup");
        ServiceLookup serviceLookup=DCOM.getServiceLookup();
        serviceLookup.registerMyself(ServiceLookup.RULEENGINESERVICE,serviceBaseInfo.getProperty(ServiceBaseInfo.NAME),serviceBaseInfo.getProperty(ServiceBaseInfo.HOSTNAME),Integer.parseInt(serviceBaseInfo.getProperty(ServiceBaseInfo.PORT)),myCert.generateBearerToken());
        
        register(new CORSFilter());
        register(RuleEngineAPI.class);
        register(new AbstractBinder() {
          @Override
          protected void configure() {
            bind(serviceBaseInfo).to(ServiceBaseInfo.class);
            bind(ruleEngine).to(RuleEngine.class);
            bind(notifyClient).to(NotificationClient.class);
          }
        });
    }
}