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

import javax.ws.rs.Path;
import javax.inject.Inject;
import org.dcom.core.servicehelper.ServiceBaseInfo;
import org.dcom.core.servicehelper.UserAuthorisationValidator;
import org.dcom.core.servicehelper.KeycloakUserAuthorisationValidator;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.POST;
import javax.ws.rs.PATCH;
import javax.ws.rs.Produces;
import javax.ws.rs.Consumes;
import javax.ws.rs.PathParam;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import org.w3c.dom.Element;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import java.io.StringReader;
import org.xml.sax.InputSource;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Map;
import java.time.LocalDateTime;
import org.dcom.core.security.ServiceCertificate;
import org.dcom.core.security.DCOMBearerToken;
import org.dcom.core.DCOM;
import javax.ws.rs.core.MultivaluedMap;
import java.util.List;
import org.dcom.ruleengine.core.RuleEngine;
import org.dcom.ruleengine.core.RuleEngineComplianceCheck;
import org.dcom.core.services.ComplianceCheckSettings;
import org.dcom.core.services.ResultService;
import org.dcom.core.services.ComplianceCheckResultItem;
import org.dcom.core.services.ComplianceCheckResultSubmission;
import org.dcom.core.services.ComplianceCheckDataItem;
import org.dcom.core.services.ComplianceCheckAnswer;
import org.dcom.core.services.DataSourceService;
import com.owlike.genson.Genson;
import java.util.Set;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.SendEmailResponse;



/**
*This is the implementation of the REST API for the rule engine service. This is separate from the rest of the rule engine core as the rule compiler needs to run before the web service is started.
*
*/
@Path("/")
public class RuleEngineAPI {


	@Inject
	public ServiceBaseInfo serviceInfo;
	
	@Inject
	public RuleEngine ruleEngine;
	
	@Inject
	public NotificationClient notifyClient;
	
	//utility functions
	
	private String successMessageJSON="{\"success\":true}";
	private String successMessageXML="<success>true</success>";

	private UserAuthorisationValidator constructValidator(String securityType, String securityURI) {
		UserAuthorisationValidator authenticator=null;
		if (securityType.equalsIgnoreCase("Keycloak")) {
			 authenticator = new KeycloakUserAuthorisationValidator(securityURI);
		} else {
			System.out.println("Authentication Service Type Not Supported");
			System.exit(0);
		} 
		return authenticator;
	}
	
	private boolean authorize(RuleEngineComplianceCheck check, String token) {
		if (token==null) return false;
		UserAuthorisationValidator teamValidator = constructValidator(check.getCheckSettings().getSecurityType(), check.getCheckSettings().getSecurityURI());
		if (teamValidator.validatePermission(token,"projectteam")) return true;
		Set<ResultService> resultServices=DCOM.getServiceLookup().getResultServices();
		for (ResultService result: resultServices) 	{
			UserAuthorisationValidator authenticator = constructValidator(result.getSecurityServiceType(), result.getSecurityServiceURI());
			if (authenticator.validatePermission(token,"buildingcontrol")) return true;
		}

		token=token.replace("Bearer","").trim();
		Set<DataSourceService> datasources=DCOM.getServiceLookup().getDataSources();
		for (DataSourceService datasource: datasources) {
			try {
				ServiceCertificate cert=datasource.getCertificate();
				if (cert.checkTokenValidity(new DCOMBearerToken(token))) return true;	
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return false;
	}
	
	
	
	@GET
	@Path("/")
	@Produces(MediaType.APPLICATION_JSON)
	public Response serviceInfoJSON() {
		return Response.ok(serviceInfo.toJSON()).build();
	}
	
	@GET
	@Path("/")
	@Produces(MediaType.APPLICATION_XML)
	public Response serviceInfoXML() {
		return Response.ok(serviceInfo.toXML()).build();
	}
	
	
	@PUT
	@Path("/")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response initCheckJSON(String body,@HeaderParam("Authorization") String token,@Context UriInfo info) {
		MultivaluedMap<String,String> queryParams=info.getQueryParameters();
		String guid=queryParams.getFirst("id");
		ComplianceCheckSettings settings=ComplianceCheckSettings.fromJSON(body);
		if (guid!=null) guid = ruleEngine.createComplianceCheck(guid,settings);
		else guid = ruleEngine.createComplianceCheck(settings);
		HashMap<String,Object> emailData=new HashMap<String,Object>();
		emailData.put("Reference",guid);
		for (int i=0; i < settings.getNoIndividuals();i++) {
			try {
				emailData.put("Name",settings.getIndividual(i).getName());
				//SendEmailResponse response = notifyClient.sendEmail("8a1163de-341f-42b8-a495-0dcd2ad103a3",settings.getIndividual(i).getEmail(),emailData,"",null);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		RuleEngineComplianceCheck check = ruleEngine.getComplianceCheck(guid,"");
		while (check.isInProgress()) {
			try {
				Thread.sleep(1000);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		ruleEngine.serialiseComplianceCheck(guid,serviceInfo.getProperty("DCOM_SERVICE_DATA_PATH"));
		return  Response.ok("{ \"complianceId\":\""+guid+"\"}").build();
	}
	
	
	@PUT
	@Path("/")
	@Consumes(MediaType.APPLICATION_XML)
	public Response initCheckXML(String body,@HeaderParam("Authorization") String token,@Context UriInfo info) {
		MultivaluedMap<String,String> queryParams=info.getQueryParameters();
		String guid=queryParams.getFirst("id");
		ComplianceCheckSettings settings=ComplianceCheckSettings.fromJSON(body);
		if (guid!=null) guid = ruleEngine.createComplianceCheck(guid,settings);
		else guid = ruleEngine.createComplianceCheck(settings);
		HashMap<String,Object> emailData=new HashMap<String,Object>();
		emailData.put("Reference",guid);
		for (int i=0; i < settings.getNoIndividuals();i++) {
			try {
				emailData.put("Name",settings.getIndividual(i).getName());
				//SendEmailResponse response = notifyClient.sendEmail("8a1163de-341f-42b8-a495-0dcd2ad103a3",settings.getIndividual(i).getEmail(),emailData,"",null);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		RuleEngineComplianceCheck check = ruleEngine.getComplianceCheck(guid,"");
		while (check.isInProgress()) {
			try {
				Thread.sleep(1000);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		ruleEngine.serialiseComplianceCheck(guid,serviceInfo.getProperty("DCOM_SERVICE_DATA_PATH"));
		return  Response.ok("<ComplianceId>"+guid+"</ComplianceId>").build();
	}
	
	@GET
	@Path("/{complianceCheckUID}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response buildingJSON(@PathParam("complianceCheckUID") String checkId,@HeaderParam("Authorization") String token) {
		RuleEngineComplianceCheck check=ruleEngine.getComplianceCheck(checkId,serviceInfo.getProperty("DCOM_SERVICE_DATA_PATH"));
		if (check==null) return Response.status(404).type("text/plain").build();
		StringBuffer str=new StringBuffer();
		ComplianceCheckSettings settings=check.getCheckSettings();
		str.append("{");
		str.append("\"securityServiceType\":\"").append(settings.getSecurityType()).append("\"");
		str.append(",\"securityServiceURI\":\"").append(settings.getSecurityURI()).append("\"");
		
		if (authorize(check,token)) {
			str.append(",\"idSet\":[");
			boolean first=true;
			for (String entityId: check.getEntityList()) {
				if (first) first=false; else str.append(",");
				str.append(check.getEntity(entityId).toJSON());
			}
			str.append("],");
			
			str.append("\"conditions\":[");
			first=true;
			for (String cond: check.getConditions()) {
				if (first) first=false; else str.append(",");
				str.append("\""+cond+"\"");
			}
			str.append("],");
			str.append("\"approval\":\"").append(check.getApproval()).append("\"");
			str.append(",\"log\":[");
			List<String> logs=check.getLogEntries();
			first=true;
			for (String log: logs) {
				if (first) first=false; else str.append(",");
				str.append("\"").append(log).append("\"");
			}
			str.append("],");
			str.append("\"messages\":[");
			List<String> messages=check.getMessages();
			first=true;
			for (String message: messages) {
				if (first) first=false; else str.append(",");
				str.append("\"").append(message.replace("\n","<br/>")).append("\"");
			}
			str.append("]");
			
		}
		str.append("}");
		return Response.ok(str.toString()).build();		
	}
	
	@GET
	@Path("/{complianceCheckUID}")
	@Produces(MediaType.APPLICATION_XML)
	public Response buildingXML(@PathParam("complianceCheckUID") String checkId,@HeaderParam("Authorization") String token) {
		RuleEngineComplianceCheck check=ruleEngine.getComplianceCheck(checkId,serviceInfo.getProperty("DCOM_SERVICE_DATA_PATH"));
		if (check==null) return Response.status(404).type("text/plain").build();
		StringBuffer str=new StringBuffer();
		ComplianceCheckSettings settings=check.getCheckSettings();
		str.append("<ComplianceCheck>");
		str.append("<SecurityServiceType>").append(settings.getSecurityType()).append("</SecurityServiceType>");
		str.append("<SecurityServiceURI>").append(settings.getSecurityURI()).append("</SecurityServiceURI>");
		
		if (authorize(check,token)) {
			str.append("<IDSet>");
			for (String entityId: check.getEntityList()) {
				str.append(check.getEntity(entityId).toXML());
			}
			str.append("</IDSet>");
			str.append("<Conditions>");
			for (String cond: check.getConditions()) {
				str.append("<Condition>");
				str.append(cond);
				str.append("</Condition>");
			}
			str.append("</Conditions>");
			str.append("<Approval>").append(check.getApproval()).append("</Approval>");
		}
		str.append("</ComplianceCheck>");
		return Response.ok(str.toString()).build();		
	}
	
	@POST
	@Path("/{complianceCheckUID}")
	@Consumes(MediaType.APPLICATION_XML)
	public Response putIDXML(@PathParam("complianceCheckUID") String checkId,String body,@HeaderParam("Authorization") String token) {
			RuleEngineComplianceCheck check=ruleEngine.getComplianceCheck(checkId,serviceInfo.getProperty("DCOM_SERVICE_DATA_PATH"));
			if (check==null) return Response.status(404).type("text/plain").build();
			if (!authorize(check,token)) return Response.status(403).type("text/plain").entity("Not Authorised").build();
			List<String> idSet=new ArrayList<String>();
			try {
					DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
					DocumentBuilder builder = factory.newDocumentBuilder();
					Document document = builder.parse(new InputSource(new StringReader(body)));
					Element root = (Element) document.getDocumentElement();
					NodeList idElements =  root.getElementsByTagName("ids");
					for (int i=0; i < idElements.getLength();i++) {
						Element id=(Element) idElements.item(i);
						idSet.add(id.getTextContent());
					}
			} catch (Exception e) {
				e.printStackTrace();
				return Response.status(500).build();
				
			}
			check.submitIdSet(idSet);
			ruleEngine.serialiseComplianceCheck(checkId,serviceInfo.getProperty("DCOM_SERVICE_DATA_PATH"));
			return  Response.ok(successMessageXML.toString()).build();
			
	}
	
	@POST
	@Path("/{complianceCheckUID}")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response putIDJSON(@PathParam("complianceCheckUID") String checkId,String body,@HeaderParam("Authorization") String token) {
		RuleEngineComplianceCheck check=ruleEngine.getComplianceCheck(checkId,serviceInfo.getProperty("DCOM_SERVICE_DATA_PATH"));
		if (check==null) return Response.status(404).type("text/plain").build();
		if (!authorize(check,token)) return Response.status(403).type("text/plain").entity("Not Authorised").build();
		Map<String, Object> data = new Genson().deserialize(body, Map.class);
		check.submitIdSet((List<String>)data.get("ids"));
		ruleEngine.serialiseComplianceCheck(checkId,serviceInfo.getProperty("DCOM_SERVICE_DATA_PATH"));
		return  Response.ok(successMessageXML.toString()).build();
	}
	
	@POST
	@Path("/{complianceCheckUID}/answers")
	@Consumes(MediaType.APPLICATION_XML)
	public Response putAnswerXML(@PathParam("complianceCheckUID") String checkId,String body,@HeaderParam("Authorization") String token) {
		RuleEngineComplianceCheck check=ruleEngine.getComplianceCheck(checkId,serviceInfo.getProperty("DCOM_SERVICE_DATA_PATH"));
		if (check==null) return Response.status(404).type("text/plain").build();
		if (!authorize(check,token)) return Response.status(403).type("text/plain").entity("Not Authorised").build();
		List<ComplianceCheckAnswer> dataItems=ComplianceCheckAnswer.fromJSONCollection(body);
		check.submitAnswers(dataItems);
		ruleEngine.serialiseComplianceCheck(checkId,serviceInfo.getProperty("DCOM_SERVICE_DATA_PATH"));
		return  Response.ok(successMessageJSON.toString()).build();
	}
	
	@POST
	@Path("/{complianceCheckUID}/answers")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response putAnswerJSON(@PathParam("complianceCheckUID") String checkId,String body,@HeaderParam("Authorization") String token) {
		RuleEngineComplianceCheck check=ruleEngine.getComplianceCheck(checkId,serviceInfo.getProperty("DCOM_SERVICE_DATA_PATH"));
		if (check==null) return Response.status(404).type("text/plain").build();
		if (!authorize(check,token)) return Response.status(403).type("text/plain").entity("Not Authorised").build();
		List<ComplianceCheckAnswer> dataItems=ComplianceCheckAnswer.fromXMLCollection(body);
		check.submitAnswers(dataItems);
		ruleEngine.serialiseComplianceCheck(checkId,serviceInfo.getProperty("DCOM_SERVICE_DATA_PATH"));
		return  Response.ok(successMessageXML.toString()).build();
	}
	
	@POST
	@Path("/{complianceCheckUID}/data")
	@Consumes(MediaType.APPLICATION_XML)
	public Response putDataXML(@PathParam("complianceCheckUID") String checkId,String body,@HeaderParam("Authorization") String token) {
		RuleEngineComplianceCheck check=ruleEngine.getComplianceCheck(checkId,serviceInfo.getProperty("DCOM_SERVICE_DATA_PATH"));
		if (check==null) return Response.status(404).type("text/plain").build();
		if (!authorize(check,token)) return Response.status(403).type("text/plain").entity("Not Authorised").build();
		List<ComplianceCheckDataItem> dataItems=ComplianceCheckDataItem.fromXMLCollection(body);
		check.submitData(dataItems);
		ruleEngine.serialiseComplianceCheck(checkId,serviceInfo.getProperty("DCOM_SERVICE_DATA_PATH"));
		return  Response.ok(successMessageXML.toString()).build();
		
	}
	
	@POST
	@Path("/{complianceCheckUID}/data")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response putDataJSON(@PathParam("complianceCheckUID") String checkId,String body,@HeaderParam("Authorization") String token) {
		RuleEngineComplianceCheck check=ruleEngine.getComplianceCheck(checkId,serviceInfo.getProperty("DCOM_SERVICE_DATA_PATH"));
		if (check==null) return Response.status(404).type("text/plain").build();
		if (!authorize(check,token)) return Response.status(403).type("text/plain").entity("Not Authorised").build();
		List<ComplianceCheckDataItem> dataItems=ComplianceCheckDataItem.fromJSONCollection(body);
		check.submitData(dataItems);
		ruleEngine.serialiseComplianceCheck(checkId,serviceInfo.getProperty("DCOM_SERVICE_DATA_PATH"));
		return  Response.ok(successMessageJSON.toString()).build();
	}
	
	@POST
	@Path("/{complianceCheckUID}/results")
	@Consumes(MediaType.APPLICATION_XML)
	public Response putResultsXML(@PathParam("complianceCheckUID") String checkId,String body,@HeaderParam("Authorization") String token) {
		RuleEngineComplianceCheck check=ruleEngine.getComplianceCheck(checkId,serviceInfo.getProperty("DCOM_SERVICE_DATA_PATH"));
		if (check==null) return Response.status(404).type("text/plain").build();	
		if (!authorize(check,token)) return Response.status(403).type("text/plain").entity("Not Authorised").build();
		List<ComplianceCheckResultSubmission> dataItems=ComplianceCheckResultSubmission.fromXMLCollection(body);
		check.submitResults(dataItems,new DCOMBearerToken(token).getIdentifier());
		ruleEngine.serialiseComplianceCheck(checkId,serviceInfo.getProperty("DCOM_SERVICE_DATA_PATH"));
		return  Response.ok(successMessageXML.toString()).build();
		
	}
	
	@POST
	@Path("/{complianceCheckUID}/results")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response putResultsJSON(@PathParam("complianceCheckUID") String checkId,String body,@HeaderParam("Authorization") String token) {
		RuleEngineComplianceCheck check=ruleEngine.getComplianceCheck(checkId,serviceInfo.getProperty("DCOM_SERVICE_DATA_PATH"));
		if (check==null) return Response.status(404).type("text/plain").build();
		if (!authorize(check,token)) return Response.status(403).type("text/plain").entity("Not Authorised").build();
		List<ComplianceCheckResultSubmission> dataItems=ComplianceCheckResultSubmission.fromJSONCollection(body);
		check.submitResults(dataItems,new DCOMBearerToken(token).getIdentifier());
		ruleEngine.serialiseComplianceCheck(checkId,serviceInfo.getProperty("DCOM_SERVICE_DATA_PATH"));
		return  Response.ok(successMessageJSON.toString()).build();
	}
	
	@POST
	@Path("/{complianceCheckUID}/approval")
	@Consumes(MediaType.APPLICATION_XML)
	public Response approvalXML(@PathParam("complianceCheckUID") String checkId,String body,@HeaderParam("Authorization") String token) {
		RuleEngineComplianceCheck check=ruleEngine.getComplianceCheck(checkId,serviceInfo.getProperty("DCOM_SERVICE_DATA_PATH"));
		if (check==null) return Response.status(404).type("text/plain").build();
		if (!authorize(check,token)) return Response.status(403).type("text/plain").entity("Not Authorised").build();
		try {
				DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
				DocumentBuilder builder = factory.newDocumentBuilder();
				Document document = builder.parse(new InputSource(new StringReader(body)));
				Element root = (Element) document.getDocumentElement();
				List<String> conditions=new ArrayList<String>();
				NodeList idElements =  root.getElementsByTagName("condition");
				for (int i=0; i < idElements.getLength();i++) {
					Element id=(Element) idElements.item(i);
					conditions.add(id.getTextContent());
				}
				String approval=root.getElementsByTagName("approval").item(0).getTextContent();
				check.submitApproval(approval,conditions);
				ruleEngine.serialiseComplianceCheck(checkId,serviceInfo.getProperty("DCOM_SERVICE_DATA_PATH"));
		} catch (Exception e) {
			e.printStackTrace();
			return Response.status(500).build();
			
		}
		return  Response.ok(successMessageXML.toString()).build();
		
	}
	
	@POST
	@Path("/{complianceCheckUID}/approval")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response approvalJSON(@PathParam("complianceCheckUID") String checkId,String body,@HeaderParam("Authorization") String token) {
		RuleEngineComplianceCheck check=ruleEngine.getComplianceCheck(checkId,serviceInfo.getProperty("DCOM_SERVICE_DATA_PATH"));
		if (check==null) return Response.status(404).type("text/plain").build();
		if (!authorize(check,token)) return Response.status(403).type("text/plain").entity("Not Authorised").build();
		Map<String, Object> data = new Genson().deserialize(body, Map.class);
		check.submitApproval((String)data.get("approval"),(List<String>)data.get("conditions"));
		ruleEngine.serialiseComplianceCheck(checkId,serviceInfo.getProperty("DCOM_SERVICE_DATA_PATH"));
		return  Response.ok(successMessageJSON.toString()).build();
	}
	
	@PUT
	@Path("/{complianceCheckUID}/messaging")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response messageJSON(@PathParam("complianceCheckUID") String checkId,String body,@HeaderParam("Authorization") String token) {
		RuleEngineComplianceCheck check=ruleEngine.getComplianceCheck(checkId,serviceInfo.getProperty("DCOM_SERVICE_DATA_PATH"));
		if (check==null) return Response.status(404).type("text/plain").build();
		if (!authorize(check,token)) return Response.status(403).type("text/plain").entity("Not Authorised").build();
		Map<String, Object> data = new Genson().deserialize(body, Map.class);
		String message = data.get("message").toString();
		check.submitMessage(message);
		ruleEngine.serialiseComplianceCheck(checkId,serviceInfo.getProperty("DCOM_SERVICE_DATA_PATH"));
		return  Response.ok(successMessageJSON.toString()).build();
	}
	
	@PUT
	@Path("/{complianceCheckUID}/messaging")
	@Consumes(MediaType.APPLICATION_XML)
	public Response messageXML(@PathParam("complianceCheckUID") String checkId,String body,@HeaderParam("Authorization") String token) {
		RuleEngineComplianceCheck check=ruleEngine.getComplianceCheck(checkId,serviceInfo.getProperty("DCOM_SERVICE_DATA_PATH"));
		if (check==null) return Response.status(404).type("text/plain").build();
		if (!authorize(check,token)) return Response.status(403).type("text/plain").entity("Not Authorised").build();
		String message=null;
		try {
				DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
				DocumentBuilder builder = factory.newDocumentBuilder();
				Document document = builder.parse(new InputSource(new StringReader(body)));
				Element root = (Element) document.getDocumentElement();
				message=root.getElementsByTagName("approval").item(0).getTextContent();
				check.submitMessage(message);
				ruleEngine.serialiseComplianceCheck(checkId,serviceInfo.getProperty("DCOM_SERVICE_DATA_PATH"));
		} catch (Exception e) {
			e.printStackTrace();
		}
		return  Response.ok(successMessageXML.toString()).build();
	}
	
	private List<ComplianceCheckResultItem> getResults(RuleEngineComplianceCheck check,UriInfo info) {
		MultivaluedMap<String,String> queryParams=info.getQueryParameters();
		String sVal=queryParams.getFirst("start");
		LocalDateTime start=null;
		if (sVal!=null) start= LocalDateTime.parse(sVal);
		String eVal=queryParams.getFirst("end");
		LocalDateTime end=null;
		if (eVal!=null) end=LocalDateTime.parse(eVal);
		String freeText=queryParams.getFirst("search");
		List<ComplianceCheckResultItem> results = check.getResults(start,end,freeText);
		return results;
	}
	
	@GET
	@Path("/{complianceCheckUID}/results")
	@Produces(MediaType.APPLICATION_JSON)
	public Response resultJSON(@PathParam("complianceCheckUID") String checkId,@HeaderParam("Authorization") String token,@Context UriInfo info) {
		RuleEngineComplianceCheck check=ruleEngine.getComplianceCheck(checkId,serviceInfo.getProperty("DCOM_SERVICE_DATA_PATH"));
		if (check==null) return Response.status(404).type("text/plain").build();
		if (!authorize(check,token)) return Response.status(403).type("text/plain").entity("Not Authorised").build();
		List<ComplianceCheckResultItem> results=getResults(check,info);
		StringBuffer str=new StringBuffer();
		str.append("{ \"results\":[");
		boolean first=true;
		for (ComplianceCheckResultItem item: results) {
			if (first) first=false;
			else str.append(",");
			str.append(item.toJSON());
		}
		str.append("]}");
		return  Response.ok(str.toString()).build();
	}
	
	
	
	@GET
	@Path("/{complianceCheckUID}/results")
	@Produces(MediaType.APPLICATION_XML)
	public Response resultXML(@PathParam("complianceCheckUID") String checkId,@HeaderParam("Authorization") String token,@Context UriInfo info) {
		RuleEngineComplianceCheck check=ruleEngine.getComplianceCheck(checkId,serviceInfo.getProperty("DCOM_SERVICE_DATA_PATH"));
		if (check==null) return Response.status(404).type("text/plain").build();
		if (!authorize(check,token)) return Response.status(403).type("text/plain").entity("Not Authorised").build();
		List<ComplianceCheckResultItem> results=getResults(check,info);
		StringBuffer str=new StringBuffer();
		str.append("<Results>");
		for (ComplianceCheckResultItem item: results) str.append(item.toXML());
		str.append("</Results>");
		return  Response.ok(str.toString()).build();
	}
	

	@GET
	@Path("/{complianceCheckUID}/{entityOrClauseId:.+}")
	@Produces(MediaType.APPLICATION_XML)
	public Response getFeedbackXML(@PathParam("complianceCheckUID") String checkId,@PathParam("entityOrClauseId") String entityOrClauseId,@HeaderParam("Authorization") String token) {
		RuleEngineComplianceCheck check=ruleEngine.getComplianceCheck(checkId,serviceInfo.getProperty("DCOM_SERVICE_DATA_PATH"));
		if (check==null) return Response.status(404).type("text/plain").build();
		if (!authorize(check,token)) return Response.status(403).type("text/plain").entity("Not Authorised").build();
		boolean entity=check.getEntityList().contains(entityOrClauseId);
		Set<String> ids;
		if (entity) ids=check.getPropertiesForEntity(entityOrClauseId);
		else ids=check.getEntitiesForClause(entityOrClauseId);
		StringBuffer str=new StringBuffer();
		str.append("<ResultItems>");
		if (entity) {
			str.append("<Properties>");
			for (String id:ids) str.append("<Property>").append(id).append("</Property>");
			str.append("</Properties>");
		} else {
			str.append("<Entities>");
			for (String id:ids) str.append("<Entity>").append(id).append("</Entity>");
			str.append("</Entities>");
		}
		str.append("<ComplianceResults>");
		for (String id:ids) {
					str.append("<ComplianceResults>");
					List<ComplianceCheckResultItem> feedback;
					if (entity) feedback=check.getResultsByEntity(entityOrClauseId,id);	
					else feedback=check.getResultsByClause(entityOrClauseId,id);	
					for (ComplianceCheckResultItem item: feedback) {
						str.append(item.toXML());
					}
					str.append("</ComplianceResults>");
		}
		str.append("</ComplianceResults>");
		
		str.append("</ResultItems>");
		return  Response.ok(str.toString()).build();
	}

	@GET
	@Path("/{complianceCheckUID}/{entityOrClauseId:.+}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getFeedbackJSON(@PathParam("complianceCheckUID") String checkId,@PathParam("entityOrClauseId") String entityOrClauseId,@HeaderParam("Authorization") String token) {
		RuleEngineComplianceCheck check=ruleEngine.getComplianceCheck(checkId,serviceInfo.getProperty("DCOM_SERVICE_DATA_PATH"));
		if (check==null) return Response.status(404).type("text/plain").build();
		if (!authorize(check,token)) return Response.status(403).type("text/plain").entity("Not Authorised").build();
		boolean entity=check.getEntityList().contains(entityOrClauseId);
		Set<String> ids;
		if (entity) ids=check.getPropertiesForEntity(entityOrClauseId);
		else ids=check.getEntitiesForClause(entityOrClauseId);
		StringBuffer str=new StringBuffer();
		str.append("{");
		if (entity) {
			str.append("\"properties\":[");
			boolean first=true;
			for (String id:ids) {
				if (!first) str.append(",");
				first=false;
				str.append("\"").append(id).append("\"");
			}
			str.append("],");
		} else {
			str.append("\"entities\":[");
			boolean first=true;
			for (String id:ids) {
					if (!first) str.append(",");
					first=false;
					str.append("\"").append(id).append("\"");
				
			}
			str.append("],");
		}
		str.append("\"complianceResults\":[");
		boolean outerFirst=true;
		for (String id:ids) {
					if (!outerFirst) str.append(",");
					outerFirst=false;
					str.append("[");
					List<ComplianceCheckResultItem> feedback;
					if (entity) feedback=check.getResultsByEntity(entityOrClauseId,id);	
					else feedback=check.getResultsByClause(entityOrClauseId,id);	
					boolean first=true;
					for (ComplianceCheckResultItem item: feedback) {
						if (!first) str.append(",");
						str.append(item.toJSON());
						first=false;
					}
					str.append("]");
					
		}
		str.append("]");
		
		str.append("}");
		return  Response.ok(str.toString()).build();
	}
	
}