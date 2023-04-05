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

import java.util.ArrayList;
import java.util.List;

/**
*This class represents a single rase tag
*
*/
public class RASEBox extends RASEItem{
	
		public static int REQUIREMENT_SECTION =1;
		public static int APPLICATION_SECTION =2;
		public static int SELECTION_SECTION =3;
		public static int EXCEPTION_SECTION =4;
	
		private int type;
		private ArrayList<RASEItem> subItems;


		public RASEBox(String _type,String _id) {
				super(_id);
				if (_type.equalsIgnoreCase("RequirementSection")) type=REQUIREMENT_SECTION;
				else if (_type.equalsIgnoreCase("SelectionSection")) 	 type=SELECTION_SECTION;
				else if (_type.equalsIgnoreCase("ApplicationSection")) type=APPLICATION_SECTION;
				else if (_type.equalsIgnoreCase("ExceptionSection")) type=EXCEPTION_SECTION;
				subItems = new ArrayList<RASEItem>();
		}

		public int getType() {
		 return type;
		}
		
		public int getNoSubItems() {
			return subItems.size();
		}
		
		public RASEItem getSubItem(int i) {
			return subItems.get(i);
		}
		
		public void addSubItem(RASEItem item) {
			subItems.add(item);
		}
		
		public void addAllSubItems(List<RASEItem> items) {
			subItems.addAll(items);
		}
		
		public void removeSubItem(RASEItem item) {
			subItems.remove(item);
		}
		
		public List<RASEItem> getAllSubItems() {
			return new ArrayList<RASEItem>(subItems);
		}
		
		public boolean isTag() { return false; }
		public boolean isBox() { return true; }
		
		public String toString() {
			String t="";
			if (type==REQUIREMENT_SECTION) t="RequirementSection";
			else if (type==SELECTION_SECTION) t="SelectionSection";
			else if (type==APPLICATION_SECTION) t="ApplicationSection";
			else if (type==EXCEPTION_SECTION) t="ExceptionSection";
			return t+"("+getId()+")["+getDocumentReference()+"]";
		}
		
		public String toStringShort() {
			String t="";
			if (type==REQUIREMENT_SECTION) t="RequirementSection";
			else if (type==SELECTION_SECTION) t="SelectionSection";
			else if (type==APPLICATION_SECTION) t="ApplicationSection";
			else if (type==EXCEPTION_SECTION) t="ExceptionSection";
			return t+"("+getId()+")";
		}
}