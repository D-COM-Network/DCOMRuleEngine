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
/**
* This class allows DROOLS to access global results
*/

public class RuleEngineResult {

		private String ruleId;
		private String result;
		
		public RuleEngineResult(String _ruleId,String _result) {
			ruleId=_ruleId;
			result=_result;
		}
		
		public String getRuleId() {
			return ruleId;
		}
		
		public String getResult() {
				return result;
		}
}