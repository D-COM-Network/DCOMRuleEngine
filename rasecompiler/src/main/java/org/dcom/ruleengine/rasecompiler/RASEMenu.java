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
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import java.io.IOException;
import java.awt.FileDialog;
import java.io.File;
import java.awt.Frame;


/**
*This is the main class of the RASEDRL menu, it provides a menu when the jar is executed in a windows environment.
*
*/
public class RASEMenu {
	
	public static void main(String[] args) throws IOException {
			
		
		int option = JOptionPane.showOptionDialog(null,
								 "What functionality to you want??",
								 "Question",JOptionPane.OK_CANCEL_OPTION,JOptionPane.QUESTION_MESSAGE,
								 null,
								 new String[] {"1. Convert Scraping to Document","2. Compile Dictionary","3. Check Dictionary and Populate","Others.."},
							 	null);
		 if (option ==0) {
			 JOptionPane.showMessageDialog(null,"Please Select the directory where the scraping files are");
			 JFileChooser chooser = new JFileChooser(); 
			 String inputDirectory="";
			 String outputDirectory="";
			 chooser.setCurrentDirectory(new java.io.File("."));
			 chooser.setDialogTitle("Please Select Input Directory");
			 chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			 chooser.setAcceptAllFileFilterUsed(false);
			 if (chooser.showOpenDialog(new JFrame()) == JFileChooser.APPROVE_OPTION)  inputDirectory=chooser.getSelectedFile().getAbsolutePath();
			 else System.exit(1);
			 
			 JOptionPane.showMessageDialog(null,"Please Select the directory where you want to save the output");
			
			 chooser = new JFileChooser(); 
			 chooser.setCurrentDirectory(new java.io.File("."));
			 chooser.setDialogTitle("Please Select Output Directory");
			 chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			 chooser.setAcceptAllFileFilterUsed(false);
			 if (chooser.showOpenDialog(new JFrame()) == JFileChooser.APPROVE_OPTION) outputDirectory=chooser.getSelectedFile().getAbsolutePath();
			 else System.exit(1);
			 
			 int startNo=Integer.parseInt(JOptionPane.showInputDialog(null,"What Section number does the document start at?"));
 			
			 String name=JOptionPane.showInputDialog(null,"Please give the document a short name?");
 		 
				String[] newArgs = new String[] {inputDirectory,outputDirectory,name,""+startNo};
				ScrapingParser.main(newArgs);
		 
		 } else if (option==1) {
			 RASEDictionary.main(args);
		 } else if (option==2) {
			 RASEDictionaryCheck.main(args);
		 } else if (option ==3) {
			  option = JOptionPane.showOptionDialog(null,
 									 "What functionality to you want??",
 									 "Question",JOptionPane.OK_CANCEL_OPTION,JOptionPane.QUESTION_MESSAGE,
 									 null,
 									 new String[] {"4. Generate SpreadSheets","5. Merge Documents","6. Merge Dictionaries","7. RASE Compiler"},
 								 	null);
				if (option == 0) {
					RASESpreadsheet.main(args);
				} else if (option ==1) {
					RASEMerger.main(args);
				} else if (option ==2) {
						RASEDictionaryMerge.main(args);
				} else if (option ==3) {
						 FileDialog fc = new FileDialog(new Frame(),"Please Select your ComplianceDocument File",FileDialog.LOAD);
						 fc.setVisible(true);
						 File docFile= fc.getFiles()[0];
						 fc = new FileDialog(new Frame(),"Please Select your Dictionary File",FileDialog.LOAD);
						 fc.setFilenameFilter(new DictionaryFileNameFilter());
						 fc.setVisible(true);
						 File dicFile = fc.getFiles()[0];
						 String[] newArgs = new String[] {docFile.getAbsolutePath(),dicFile.getAbsolutePath()};
						 RASEDRLCompiler.main(newArgs);
			}
		}
	}	
}