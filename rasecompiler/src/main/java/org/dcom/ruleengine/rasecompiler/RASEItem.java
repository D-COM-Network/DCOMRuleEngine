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




/**
*This class represents an abstract rase item
*
*/
public abstract class RASEItem {
	
		private String id;
		private String documentReference;
		
		public RASEItem(String _id) {
			id=_id;
		}
		
		public String getId() {
			return id;
		}
		
		
		public void setDocumentReference(String _docRef) {
			documentReference=_docRef;
		}
		
		public String getDocumentReference() {
			return documentReference;
		}
		
		public abstract boolean isTag();
		public abstract boolean isBox();
		public abstract int getType();

}