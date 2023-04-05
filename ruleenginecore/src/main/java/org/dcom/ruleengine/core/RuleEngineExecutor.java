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


import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;
import org.kie.api.builder.ReleaseId;
import org.kie.api.runtime.KieSession;
import org.kie.api.KieBase;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.kie.api.runtime.ObjectFilter;
import org.kie.api.event.rule.RuleRuntimeEventListener;
import org.kie.api.event.rule.ObjectDeletedEvent;
import org.kie.api.event.rule.ObjectInsertedEvent;
import org.kie.api.event.rule.ObjectUpdatedEvent;


/**
* This class manages the rule engine in its own separate thread. This separation is needed so that when a rule engine is waiting for data input it does not cause the overall rule engine service to freeze.
*
*/
public class RuleEngineExecutor extends Thread {
	
	private KieSession ruleSession;
	private boolean running;
	private String ruleName;
	
	private static final Logger LOGGER = LoggerFactory.getLogger( RuleEngineExecutor.class );
	
	public RuleEngineExecutor(String _ruleName,Collection<RuleEngineComplianceObject> entities) {
		LOGGER.info("Setting up Rule Engine");
		ruleName=_ruleName;
		running=false;
		KieServices ks = KieServices.Factory.get();
		ReleaseId releaseId = ks.newReleaseId("org.dcom.rules", ruleName, "1.0");
		KieContainer ruleContainer = ks.newKieContainer(releaseId);
		KieBase kieBase=ruleContainer.getKieBase("dcom");
		ruleSession=kieBase.newKieSession();
		ruleSession.addEventListener(new LoggerListener());
		for (RuleEngineComplianceObject o: entities) ruleSession.insert(o);
	}

	public void run() {
		running=true;
		LOGGER.info("Starting Rule Engine:"+ruleName);
		ruleSession.getAgenda().getAgendaGroup("sort").setFocus();
		ruleSession.fireAllRules();
		ruleSession.getAgenda().getAgendaGroup("compute").setFocus();
		ruleSession.fireAllRules();
		ruleSession.getAgenda().getAgendaGroup("summary").setFocus();
		ruleSession.fireAllRules();
		ruleSession.dispose();
		LOGGER.info("Rule Engine Finished:"+ruleName);
		running=false;
	}
	
	public boolean isRunning(){
		return running;
	}
	
	
	public Collection<RuleEngineResult> getResults() {
		return (Collection<RuleEngineResult>)ruleSession.getObjects(new ResultObjectFilter());
	}
	
	public void stopEngine() {
		ruleSession.halt();
		ruleSession.dispose();
		running=false;
	}

}

class LoggerListener implements RuleRuntimeEventListener {
	
	private static final Logger LOGGER = LoggerFactory.getLogger( RuleEngineExecutor.class );
	
	public void objectInserted​(ObjectInsertedEvent event) {
			if (! (event.getObject() instanceof RuleEngineResult)) return;
			RuleEngineResult result = (RuleEngineResult) event.getObject();
			LOGGER.info("New Result:"+result.getRuleId()+"("+result.getResult()+")");
	}
	
	public void objectUpdated​(ObjectUpdatedEvent event) {
		
	}
	
	public void objectDeleted(ObjectDeletedEvent event) {
		
	}
}

class ResultObjectFilter implements ObjectFilter {
	public boolean accept(Object object) {
		if (object instanceof RuleEngineResult) return true;
		else return false;
	}
}