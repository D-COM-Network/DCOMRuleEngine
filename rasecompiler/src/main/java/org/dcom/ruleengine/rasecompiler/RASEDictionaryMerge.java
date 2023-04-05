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
import javax.swing.JOptionPane;
import java.awt.FileDialog;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import org.dcom.core.services.FileDictionaryService;
import org.dcom.core.services.DictionaryItem;
import java.util.Set;
import java.awt.Frame;

/**
*This code to merge dictionaries
*
*/
public class RASEDictionaryMerge {
	
	private static FileDictionaryService dictionary;
	
	public static void main(String[] args) throws IOException {

			FileDialog fc = new FileDialog(new Frame(),"Please Select your Dictionary Files",FileDialog.LOAD);
			fc.setFilenameFilter(new DictionaryFileNameFilter());
			fc.setMultipleMode(true);
			fc.setVisible(true);
			File[] files = fc.getFiles();
			if (files.length==0) System.exit(1);
			FileDictionaryService dictionary = new FileDictionaryService();
			for (int i=0; i < files.length;i++) {
				FileDictionaryService dic = new FileDictionaryService(files[i]);
				for (String o: dic.getObjects()) {
						dictionary.addObject(o);
						dictionary.setIfcType(o,dic.getIfcType(o));
						dictionary.setIfcSubType(o,dic.getIfcSubType(o));
						dictionary.setClassification(o,dic.getClassification(o));
						Set<DictionaryItem> properties = dic.getProperties(o);
						for (DictionaryItem item: properties) dictionary.addProperty(o,item);
				}
			
			}

			JOptionPane.showMessageDialog(null,"Please Select where to save your dictionary file");
			fc = new FileDialog(new Frame(),"Please Select your Dictionary File",FileDialog.SAVE);
			fc.setFilenameFilter(new DictionaryFileNameFilter());
			fc.setVisible(true);
			files = fc.getFiles();
			if (files.length==0) System.exit(1);
			dictionary.saveDictionary(files[0]);
			System.exit(1);
	}	
}