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
*This takes care of parsing the scraping files
*
*/


import org.dcom.core.compliancedocument.*;
import org.dcom.core.compliancedocument.serialisers.XMLComplianceDocumentSerialiser;
import org.dcom.core.compliancedocument.serialisers.JSONComplianceDocumentSerialiser;
import org.dcom.core.compliancedocument.utils.GuidHelper;
import org.dcom.core.security.ServiceCertificate;
import java.io.File;
import java.io.IOException;
import com.owlike.genson.Genson;
import java.io.FileFilter;
import com.owlike.genson.GensonBuilder;
import java.util.HashMap;
import java.nio.file.Files;

import java.io.FileInputStream;

import java.nio.file.StandardOpenOption;
import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;


class FNFilter implements FileFilter {
  
  private String[] extensions;
  public FNFilter(String[] _e){
    extensions=_e;
  }
  public boolean accept(File pathName) {
    for (String e: extensions) {
        if (pathName.getName().toLowerCase().endsWith(e.toLowerCase())) return true;
    }
    return false;
  }
}

public class ScrapingParser {
  
  private static String standardsSection="AppendixB";
  private static String abbrevPara="Z25";
  private static String authoritySection="AppendixA";
  private static int startSection=0;
    
  private static HashSet<String> insertedTables=new HashSet<String>();
  
  private static String getOrNull(String name, HashMap<String,Object> data) {
    if (data.keySet().contains(name)) {
      return data.get(name).toString();
    }
    return null;
  }
  
  private static void quitWithError(String err) {
    System.out.println("[Error] "+err);
    System.exit(1);
  }
  
  private static void generateGuid(ComplianceItem i) {
      i.setMetaData("dcterms:identifier",GuidHelper.generateGuid());
  }
  
  private static void processForStandards(ComplianceItem item,HashMap<String,Object> standards) {
    ArrayList<Object> rows=(ArrayList<Object>) ((HashMap<String,Object>) (standards.get("SAdata-set"))).get("record");
    for (int i=0; i < rows.size();i++) {
      HashMap<String,Object> row = (HashMap<String,Object>) rows.get(i);
        if (item.getMetaDataString("body").contains(" "+row.get("_SA_DocNumber").toString()+" ")) {
          String nameText=row.get("_SA_DocNumber").toString();
          if (!row.get("_SA_DURL").toString().equals("#")) {
            nameText+=" ("+row.get("_SA_DURL").toString()+")";
          }else {
            nameText+=" ("+row.get("_SA_URL").toString()+")";
          }
          item.setMetaData("dcterms:relation",nameText);

        }
    }
  }
  
  
  private static void processForSchedule1(ComplianceItem item,Object secNum,Object parNum,String title,HashMap<String,Object> schedule1) {
    if (secNum==null || parNum==null) return;
    String stringNum=secNum.toString()+"."+parNum.toString();
    HashMap<String,Object> rows=(HashMap<String,Object>) schedule1.get("Schedule1RR");
    for (int i=1; i < rows.size()-1;i++) {
        HashMap<String,Object> row = (HashMap<String,Object>) rows.get(""+i);
        if (row.get("_Req_Reference").toString().startsWith(title)) {
          if (stringNum.equals(row.get("_Req_BeforeSectNum").toString())) {
            item.setMetaData("dcterms:relation","https://regulations.dcom.org.uk/GB-ENG/2/Schedule1/"+row.get("_Req_M").toString()+row.get("_Req_Vol").toString()+"/"+row.get("_Req_BeforeSectNum").toString());
          }
        }
    
    }
  }
  
  
  private static void processForRelated(ComplianceItem item,HashMap<String,Object> relevantAuthorityList) {
      ArrayList<Object> rows=(ArrayList<Object>) ((HashMap<String,Object>) (relevantAuthorityList.get("RAdata-set"))).get("record");
      for (int i=0; i < rows.size();i++) {
        HashMap<String,Object> row= (HashMap<String,Object>) rows.get(i);
          if (item.getMetaDataString("body").contains(" "+row.get("_RA_DocNumber").toString()+" ")) {
              String nameText=row.get("_RA_DocNumber").toString();
              if (!row.get("_RA_DURL").toString().equals("#")) {
                nameText+=" ("+row.get("_RA_DURL").toString()+")";
              }else {
                nameText+=" ("+row.get("_RA_URL").toString()+")";
              }
              item.setMetaData("dcterms:relation",nameText);
              
          }
        
      }
  }
  
  private static void allocateTable(Paragraph p, String table,HashMap<String,Table> tables) {
    if (table==null) return;
    String[] tableList=table.split(",");
    for (String t:tableList) {
      t=t.trim().replace("T","");
      if (t.contains(".")) {
        t=t.substring(t.lastIndexOf(".")+1);
      }
      p.setMetaData("dcterms:relation","Table/"+t);
      if (!insertedTables.contains(t)) {
          System.out.println("Inserting Table:"+t);
          if (tables.containsKey("T"+t)) {
              insertedTables.add(t);
            p.addInsert(tables.get("T"+t));
          } else {
            System.out.println("TABLE NOT FOUND:"+t);
          }
          
      }
    }
      
    
  }
  
  private static void allocateFigure(Paragraph p, String figure, HashMap<Figure,HashSet<String>> imageData) {
    if (figure==null || imageData==null) return;
    System.out.println("Allocating:"+figure);
    String[] figureList;
    if (figure.contains(",")) {
      figureList=figure.split(",");
    } else {
      figureList=new String[1];
      figureList[0]=figure;
    }
    for (int i=0; i < figureList.length;i++) {
        Figure found=null;
        for (Figure fig:imageData.keySet()) {
          HashSet<String> lists=imageData.get(fig);
          String figureNum=figureList[i].trim().substring(figureList[i].trim().indexOf(".")+1);
          p.setMetaData("dcterms:relation","Figure/"+figureNum);
        //  System.out.println(figureList[i].trim());
          if (lists.contains(figureList[i].trim())) {
            found=fig;
            p.addInsert(found);
            System.out.println("Found Image:"+  figureList[i].trim()+" NUM:"+figureNum);
            
            break;
            
          }
      }
      if (found!=null) imageData.remove(found);
    }
  }
  
  private static String paraTextReplacements(String t) {
    if (t.contains(":")) {
      if (t.indexOf(":") < t.length()/3) {
        //only replace if in first 1/3
        t="<b>"+t.substring(0,t.indexOf(":"))+"</b>"+t.substring(t.indexOf(":"));
      }
    }
    return t;
  }
  
  private static ComplianceDocument parseSection(  HashMap<String, Object> input, HashMap<String,Table> tables, HashMap<Figure,HashSet<String>> imageData,String date, HashMap<String,Object> standardsReferred, HashMap<String,Object> relevantAuthority, HashMap<String,Object> abbreviations, HashMap<String,Object> schedule1) {
      ComplianceDocument document=new ComplianceDocument();
      ArrayList<Object> rows=(ArrayList<Object>) ((HashMap<String,Object>) (input.get("data-set"))).get("record");
      HashMap<String,Paragraph> subParaMaps=new HashMap<String,Paragraph>();
      HashMap<String,Paragraph> paraMaps=new HashMap<String,Paragraph>();
      HashMap<String,Section> paraHeadMap=new HashMap<String,Section>();
      //skip the first row
      boolean docDataPop=false;
      String prevSectionNum=null;
      for (int i=1; i < rows.size();i++) {
        HashMap<String,Object> row=(HashMap<String,Object>) rows.get(i);
        String sectionNum=null;
        if (row.get("_Cl_SectionNum")==null) {
          sectionNum=prevSectionNum;
        } else {
          sectionNum=row.get("_Cl_SectionNum").toString();
          prevSectionNum=sectionNum;
        }
      

        if (!docDataPop){
          docDataPop=true;
          String title=row.get("_Title").toString();
          String jurisdiction=row.get("_Jurisdiction").toString();
          System.out.println("Parsing Section: "+sectionNum);
          if (startSection!=1) {
              document.setMetaData("dcom:startSectionNumber",""+startSection);
          }
          document.setMetaData("dcterms:coverage.spatial",jurisdiction);
          document.setMetaData("dcterms:title",title);
          document.setMetaData("dcterms:language","en-gb");
      		document.setMetaData("dcterms:type","Guidance");
          document.setMetaData("dcterms:coverage.temporal",date);
      		document.setMetaData("dcterms:dateCreated",date);
      		document.setMetaData("dcterms:modified",date);
          document.setMetaData("ckterms:version","1.0");
          
          document.setMetaData("ckterms:sector","En_20");
          document.setMetaData("ckterms:sector","En_25");
          document.setMetaData("ckterms:sector","En_30");
          document.setMetaData("ckterms:sector","En_32");
          document.setMetaData("ckterms:sector","En_35");
          document.setMetaData("ckterms:sector","En_40");
          document.setMetaData("ckterms:sector","En_42");
          document.setMetaData("ckterms:sector","En_50");
          document.setMetaData("ckterms:sector","En_55");
          document.setMetaData("ckterms:sector","En_60");
          document.setMetaData("ckterms:sector","En_65");
          document.setMetaData("ckterms:sector","En_70");
          document.setMetaData("ckterms:sector","En_75");
          document.setMetaData("ckterms:sector","En_80");
          document.setMetaData("ckterms:sector","En_90");
          document.setMetaData("dcterms:subject","Pm_30_30");
          document.setMetaData("dcterms:subject","Pm_35_40");
        
      		document.setMetaData("dcterms:identifier",GuidHelper.generateGuid());
        }
        String sectionName=row.get("_Cl_SectionName").toString();
        String paraText=getOrNull("_Cl_ParaText",row);
        String paraHead1=getOrNull("_Cl_ParaHead1",row);
        String paraHead2=getOrNull("_Cl_ParaHead2",row);
        String paraHead3=getOrNull("_Cl_ParaHead3",row);
        String paraHead4=getOrNull("_Cl_ParaHead4",row);
        
        if (paraHead2!=null && paraHead1==null) {
          paraHead1=paraHead2;
          paraHead2=null;
        }
        if (paraHead2==null && paraHead3!=null) {
          paraHead2=paraHead3;
          paraHead3=null;
        }
        boolean corrected=false;
        String paraNum=row.get("_Cl_ParaNum").toString();
        if (paraNum.contains("z")) {
          paraNum=paraNum.substring(0,paraNum.indexOf("z")+1);
          System.out.println("Corrected:"+paraNum);
          corrected=true;
        }
        String subParaNum=getOrNull("_Cl_SubParaNum",row);
        String subParaHead1=getOrNull("_Cl_SubParaHead1",row);
        String subParaText=getOrNull("_Cl_SubParaText",row);
        String subSubParaNum=getOrNull("_Cl_SubSubParaNum",row);
        String subSubParaName=getOrNull("_Cl_SubSubParaName",row);
        String subSubParaText=getOrNull("_Cl_SubSubParaText",row);
        
        if (document.getSection(0)==null) {
          Section s=new Section(document);
          generateGuid(s);
          s.setMetaData("dcterms:title",sectionName.strip());
          s.setMetaData("numbered","global");
          document.addSection(s);
        } 
        
        if (paraHead1!=null ) {
         if (!paraHeadMap.containsKey(paraHead1.strip())) {
           Section s=new Section(document.getSection(0));
           generateGuid(s);
           s.setMetaData("dcterms:title",paraHead1.strip());
           s.setMetaData("numbered","none");
           document.getSection(0).addSection(s);
           paraHeadMap.put(paraHead1.strip(),s);
         } 
         if (paraHead2!=null ) {
           if (paraHead1==null) paraHead1="null";
           if (!paraHeadMap.containsKey(paraHead1.strip()+"_"+paraHead2.strip())) {
             Section s=new Section(paraHeadMap.get(paraHead1.strip()));
             generateGuid(s);
             s.setMetaData("dcterms:title",paraHead2.strip());
             s.setMetaData("numbered","none");
             paraHeadMap.get(paraHead1.strip()).addSection(s);
             paraHeadMap.put(paraHead1.strip()+"_"+paraHead2.strip(),s);
           }  
           if (paraHead3!=null ) {
             if (!paraHeadMap.containsKey(paraHead1.strip()+"_"+paraHead2.strip()+"_"+paraHead3.strip())) {
              Section s=new Section(paraHeadMap.get(paraHead1.strip()+"_"+paraHead2.strip()));
               generateGuid(s);
               s.setMetaData("dcterms:title",paraHead3.strip());
               s.setMetaData("numbered","none");
               paraHeadMap.get(paraHead1.strip()+"_"+paraHead2.strip()).addSection(s);
               paraHeadMap.put(paraHead1.strip()+"_"+paraHead2.strip()+"_"+paraHead3.strip(),s);
             }
             if (paraHead4!=null ) {
               if (!paraHeadMap.containsKey(paraHead1.strip()+"_"+paraHead2.strip()+"_"+paraHead3.strip()+"_"+paraHead4.strip())) {
                Section s=new Section(paraHeadMap.get(paraHead1.strip()+"_"+paraHead2.strip()+"_"+paraHead3.strip()));
                 generateGuid(s);
                 s.setMetaData("dcterms:title",paraHead4.strip());
                 s.setMetaData("numbered","none");
                 paraHeadMap.get(paraHead1.strip()+"_"+paraHead2.strip()+"_"+paraHead3.strip()).addSection(s);
                 paraHeadMap.put(paraHead1.strip()+"_"+paraHead2.strip()+"_"+paraHead3.strip()+"_"+paraHead4.strip(),s);
               }
             }
           }
         }
       }
      
        Section s;
      if (paraHead4!=null ) {
        s=paraHeadMap.get(paraHead1.strip()+"_"+paraHead2.strip()+"_"+paraHead3.strip()+"_"+paraHead4.strip());
      } else if (paraHead3!=null ) {
         s=paraHeadMap.get(paraHead1.strip()+"_"+paraHead2.strip()+"_"+paraHead3.strip());
       } else if (paraHead2!=null ) {
          s=paraHeadMap.get(paraHead1.strip()+"_"+paraHead2.strip());
       } else if (paraHead1!=null ) {
          s=paraHeadMap.get(paraHead1.strip());
       } else {
         s=document.getSection(0);
       }
       
        //we should now have the section identified
         
        Paragraph p=null;
        if (paraMaps.get(""+paraNum)==null) {
            if (paraNum.equals(abbrevPara)) {
              ArrayList<Object> abRows=(ArrayList<Object>) ((HashMap<String,Object>) (abbreviations.get("Abbreviations"))).get("record");
              for (int z=0; z < abRows.size();z++) {
                HashMap<String,Object> abRow= (HashMap<String,Object>) abRows.get(z);
                String text="<b>"+abRow.get("_Abbreviation").toString()+"</b>: "+abRow.get("_AbbDefinition").toString();
                Paragraph aP=new Paragraph(s);
                aP.setMetaData("body",text);
                s.addParagraph(aP);
              }
              continue;
            }
            p=new Paragraph(s);
            generateGuid(p);
          
            if (paraText==null) {
              System.out.println("[WARN!] Para Text Null when should not be:"+paraHead1+":"+paraHead2+":"+paraNum);
              paraText="";
            }
            if (getOrNull("_Cl_SubSubParaName",row)!=null) {
              //we have a footnote
              String footNoteName=getOrNull("_Cl_SubSubParaName",row);
              String footNoteText=getOrNull("_Cl_SubSubParaText",row);
              row.remove("_Cl_SubSubParaName");
              row.remove("_Cl_SubSubParaText");
              subSubParaText=null;
              footNoteText="<span title=\""+footNoteText.replaceAll("“","").replaceAll("”","")+"\"><sup>"+footNoteName+"</sup></span>";
              paraText=paraText.replaceFirst(footNoteName,footNoteText);
              
            }
            
            p.setMetaData("body",paraText);
            processForStandards(p,standardsReferred);
            if (getOrNull("_Cl_BBOtherRef",row)!=null) processForRelated(p,relevantAuthority);
            processForSchedule1(p,row.get("_Cl_SectionNum"),row.get("_Cl_ParaNum"),row.get("_Title").toString(),schedule1);
            allocateTable(p,getOrNull("_Cl_TableNum",row),tables);
            allocateFigure(p,getOrNull("_Cl_FigureNum",row),imageData);
            if (!corrected) p.setMetaData("numbered","global");
            s.addParagraph(p);
            paraMaps.put(""+paraNum,p);
        } else {
          p=paraMaps.get(""+paraNum);
          //but we need to check if the para is broken by a paraHead4
          boolean found=false;
          for (int z=0; z < s.getNoParagraphs();z++) {
            if (s.getParagraph(z)==p) found=true;
          }
          if (!found) {
            System.out.println("Found Split Para!");
            p=new Paragraph(s);
            generateGuid(p);
            paraMaps.put(""+paraNum,p);
            s.addParagraph(p);
            p.setMetaData("body","");
          }
        }
        
        //deal with sub para
        if (subParaText!=null  ) {
          if (!subParaMaps.containsKey(paraNum+"_"+getOrNull("_Cl_SubParaNum",row)) && !subParaMaps.containsKey(paraNum+"_"+getOrNull("_Cl_SubParaText",row)) ) {
          Paragraph subP=new Paragraph(p);
          generateGuid(subP);
          if (getOrNull("_Cl_SubParaHead1",row)!=null) {
            subParaText="<b>"+getOrNull("_Cl_SubParaHead1",row).strip()+"</b> "+subParaText;
          }
      
          allocateTable(subP,getOrNull("_Cl_TableNum",row),tables);
          allocateFigure(subP,getOrNull("_Cl_FigureNum",row),imageData);
          p.addParagraph(subP);
          if (getOrNull("_Cl_SubParaNum",row)!=null) {
            if (getOrNull("_Cl_SubParaNum",row).equalsIgnoreCase("NOTE")) {
              subP.setMetaData("numbered","none");
              subParaText="NOTE: "+subParaText;
            } else {
              subP.setMetaData("numbered","global");
              subP.setMetaData("numberedstyle","a");
              subParaMaps.put(paraNum+"_"+getOrNull("_Cl_SubParaNum",row),subP);
            }
          } else {
            subP.setMetaData("numbered","none");
            subParaMaps.put(paraNum+"_"+getOrNull("_Cl_SubParaText",row),subP);
          }
          if (getOrNull("_Cl_SubSubParaName",row)!=null) {
            //we have a footnote
            String footNoteName=getOrNull("_Cl_SubSubParaName",row);
            String footNoteText=getOrNull("_Cl_SubSubParaText",row);
            row.remove("_Cl_SubSubParaName");
            row.remove("_Cl_SubSubParaText");
            subSubParaText=null;
            footNoteText="<span title=\""+footNoteText.replaceAll("“","").replaceAll("”","")+"\"><sup>"+footNoteName+"</sup></span>";
            subParaText=subParaText.replaceFirst(footNoteName,footNoteText);
            
          }
          subP.setMetaData("body",paraTextReplacements(subParaText));
          processForStandards(subP,standardsReferred);
        
          if (getOrNull("_Cl_BBOtherRef",row)!=null) processForRelated(subP,relevantAuthority);
          processForSchedule1(p,row.get("_Cl_SectionNum"),row.get("_Cl_ParaNum"),row.get("_Title").toString(),schedule1);
        }
      }
      
        //deal with sub sub paras
        if (subSubParaText !=null && getOrNull("_Cl_SubSubParaName",row)==null) {
            
            Paragraph parentP=null;
            if (getOrNull("_Cl_SubParaNum",row)!=null ) {
              parentP=subParaMaps.get(paraNum+"_"+getOrNull("_Cl_SubParaNum",row));
            } else {
              parentP=paraMaps.get(""+paraNum);
            }
            if (parentP==null) {
              System.out.println("Could not find:"+paraNum+"."+getOrNull("_Cl_SubParaNum",row));
            }
            Paragraph subSubP=new Paragraph(parentP);
            generateGuid(subSubP);
          
            allocateTable(subSubP,getOrNull("_Cl_TableNum",row),tables);
            allocateFigure(subSubP,getOrNull("_Cl_FigureNum",row),imageData);
            
            parentP.addParagraph(subSubP);
          
            if (getOrNull("_Cl_SubSubParaNum",row)!=null) {
              subSubP.setMetaData("numbered","global");
              if (getOrNull("_Cl_SubSubParaNum",row).equalsIgnoreCase("NOTE")) {
                subSubP.setMetaData("numbered","none");
                subSubParaText="NOTE: "+subSubParaText;
              }else {
                subSubP.setMetaData("numberedstyle","a");
              }
            
            } else subSubP.setMetaData("numbered","none");
            if (getOrNull("_Cl_SubSubParaName",row)!=null) {
              //we have a footnote
              String footNoteName=getOrNull("_Cl_SubSubParaName",row);
              String footNoteText=getOrNull("_Cl_SubSubParaText",row);
              row.remove("_Cl_SubSubParaName");
              row.remove("_Cl_SubSubParaText");
              footNoteText="<span title=\""+footNoteText.replaceAll("“","").replaceAll("”","")+"\">"+footNoteName+"</span>";
              paraText=paraText.replaceFirst(footNoteName,footNoteText);
              
            }
            subSubP.setMetaData("body",paraTextReplacements(subSubParaText));
            processForStandards(subSubP,standardsReferred);
            if (getOrNull("_Cl_BBOtherRef",row)!=null) processForRelated(subSubP,relevantAuthority);
            processForSchedule1(p,row.get("_Cl_SectionNum"),row.get("_Cl_ParaNum"),row.get("_Title").toString(),schedule1);
        }
      }
      return document;
  }
  
  
  private static Table parseTable(HashMap<String, Object> input, HashMap<String,Object> standardsReferred, HashMap<String,Object> relevantAuthority) {
    Table t=new Table(null);
    String tName=(String)(input.keySet().iterator().next());
    System.out.println("Parsing Table:"+tName);
    ArrayList<Object> rows=(ArrayList<Object>) ((HashMap<String,Object>) (input.get(tName))).get("record");
  
    //skip the first one
    //process the headings
    TableHeader header=new TableHeader(t);
    Row r=new Row(header);
    HashMap<String,Object> headingRow=(HashMap<String,Object>)rows.get(1);
    t.setMetaData("caption",headingRow.get("_"+tName+"_Col2").toString());
    for (int x=4; x < headingRow.keySet().size();x++) {
      if (headingRow.keySet().contains("_"+tName+"_Col"+x)) {
        TitleCell tC=new TitleCell(r);
        tC.setMetaData("body",headingRow.get("_"+tName+"_Col"+x).toString());
        r.addCell(tC);
      }
    }
    ArrayList<String> notes=new ArrayList<String>();
    int noNotes=0;
    String noteColumnTop=headingRow.get("_"+tName+"_Col"+headingRow.values().size()).toString();
    if (!noteColumnTop.equals("#")) {
      noNotes++;
      notes.add(noteColumnTop);
    }
  
    header.addRow(r);
    t.setHeader(header);
  
    int noCols=-1;
    TableBody body=new TableBody(t);

    for (int i=2; i < rows.size();i++) {
      HashMap<String,Object> thisRow=(HashMap<String,Object>)rows.get(i);
    
      if (thisRow.containsKey("_"+tName+"_Col"+thisRow.values().size())) {
        String noteColumn=thisRow.get("_"+tName+"_Col"+thisRow.values().size()).toString();
        if (!noteColumn.equals("#")) noNotes++;
      }
      
    }
    //get the next rows
    for (int i=2; i < rows.size();i++) {
      HashMap<String,Object> thisRow=(HashMap<String,Object>)rows.get(i);
      if (noCols==-1) noCols=thisRow.values().size()-4;
      r=new Row(body);
      if (thisRow.containsKey("_"+tName+"_Col"+thisRow.values().size())) {
        String noteColumn=thisRow.get("_"+tName+"_Col"+thisRow.values().size()).toString();
        boolean hasNote=false;
        int notePos=0;
        if (!noteColumn.equals("#")) {
          boolean found=false;
          for (int z=0; z< notes.size();z++) {
            if (notes.get(z).equals(noteColumn)) {
              found=true;
              notePos=z+1;
            }
          }
          if (!found) {
            notes.add(noteColumn);
            notePos=notes.size();
          }
          hasNote=true;
        }
        for (int x=4; x < thisRow.values().size(); x++) {
            DataCell dC=new DataCell(r);
            //System.out.println("_"+tName+"_Col"+x);
            String content=thisRow.get("_"+tName+"_Col"+x).toString();
            if (x==4) {
              boolean needsNote=true;
              for (int z=1; z<= noNotes;z++) {
                if (content.endsWith(String.valueOf(z))) {
                  content=content.substring(0,content.length()-1);
                  content=content+"<sup>"+z+"</sup>";
                  needsNote=false;
                }
                if (content.contains(z+" ")) {
                  content=content.replaceAll(z+" ","<sup>"+z+"</sup>");
                  needsNote=false;
                } 
                if (content.contains(z+",") ) {
                  content=content.replaceAll(z+",","<sup>"+z+",</sup>");
                  needsNote=false;
                }
              }
              if (hasNote==true && needsNote) {
                content+="<sup>"+notePos+"</sup>";
              }
            }
            dC.setMetaData("body",content);
            r.addCell(dC);
        }
        body.addRow(r);
      }
    
    }
    
    
    t.setBody(body);
    
    if (notes.size()>0) {
      TableFooter foot=new TableFooter(t);
      r=new Row(foot);
      DataCell c=new DataCell(r);
      c.setMetaData("body","Notes:");
      c.setMetaData("colspan",""+noCols);
      r.addCell(c);
      foot.addRow(r);
      for (int j=0; j < notes.size();j++) {
          String note=notes.get(j);
          r=new Row(foot);
          c=new DataCell(r);
          if (!note.startsWith(""+(j+1))) {
              note=(j+1)+". "+note;
          }
          c.setMetaData("body",note);
          processForStandards(c,standardsReferred);
          processForRelated(c,relevantAuthority);
          c.setMetaData("colspan",""+noCols);
          r.addCell(c);
          foot.addRow(r);
      }
      t.setFooter(foot);
    }

    return t;
  }
  
  private static File[] fileList;
  
  private File identifyFile(String name) {
    for (File f: fileList) {
      if (f.isDirectory() && f.getName().equals(name)) return f;
    }
    return null;
  }
  
  private static ComplianceDocument parseSchedule1(HashMap<String,Object> schedule1) {
    ComplianceDocument document=new ComplianceDocument();
    HashMap<String,Object> rows=(HashMap<String,Object>) schedule1.get("Schedule1RR");
    
    HashMap<String,Section> sections=new HashMap<String,Section>();
    HashMap<String,Section> subSections=new HashMap<String,Section>();
  
  
    for (int i=1; i < rows.size()-1;i++) {
      HashMap<String,Object> row=(HashMap<String,Object>) rows.get(""+i);
      if (!sections.containsKey(row.get("_Req_M").toString()+row.get("_Req_Vol").toString())) {
        Section s= new Section(document);
        sections.put(row.get("_Req_M").toString()+row.get("_Req_Vol").toString(),s);
        document.addSection(s);
        s.setMetaData("dcterms:title",row.get("_Req_M").toString()+row.get("_Req_Vol").toString());
        s.setMetaData("numbered","None");
      }
      Section section=sections.get(row.get("_Req_M").toString()+row.get("_Req_Vol").toString());
      if (!subSections.containsKey(row.get("_Req_M").toString()+row.get("_Req_Vol").toString()+"_"+row.get("_Req_BeforeSectNum").toString())){
        Section s= new Section(section);
        subSections.put(row.get("_Req_M").toString()+row.get("_Req_Vol").toString()+"_"+row.get("_Req_BeforeSectNum").toString(),s);
        section.addSection(s);
        s.setMetaData("dcterms:title",row.get("_Req_BeforeSectNum").toString());
        s.setMetaData("numbered","None");
      }
      Section subSection=subSections.get(row.get("_Req_M").toString()+row.get("_Req_Vol").toString()+"_"+row.get("_Req_BeforeSectNum").toString());
      Paragraph p=new Paragraph(subSection);
      subSection.addParagraph(p);
      p.setMetaData("body",row.get("_Req_Requirement").toString());
      
    }
    return document;
  }

  public static void main(String[] args) {
    
    ScrapingParser.startSection = Integer.parseInt(args[3]);
    Genson genson=new GensonBuilder().create();
    HashMap<String,Object> standardsReferred=null;
    HashMap<String,Object> relevantAuthority=null;
    HashMap<String,Object> abbreviations=null;
    HashMap<String,Object> schedule=null;
    try {
      System.out.println("Parsing: Standards");
      standardsReferred=genson.deserialize(Files.readString(new File(args[0]+File.separator+"_StandardsReferred.json").toPath()), HashMap.class);
      System.out.println("Parsing: Relevant Authority Docs");
      relevantAuthority=genson.deserialize(Files.readString(new File(args[0]+File.separator+"_RelevantAuthorityRefDocs.json").toPath()), HashMap.class);
      System.out.println("Parsing: Abbreviations");
      abbreviations=genson.deserialize(Files.readString(new File(args[0]+File.separator+"_Abbreviations.json").toPath()), HashMap.class);
      System.out.println("Parsing: Schedule 1");
      schedule=genson.deserialize(Files.readString(new File(args[0]+File.separator+"_Schedule1RR.json").toPath()), HashMap.class);
      ComplianceDocument schedule1Doc=parseSchedule1(schedule);
      Files.writeString(FileSystems.getDefault().getPath(args[1]+File.separator+"Schedule1.html"),XMLComplianceDocumentSerialiser.serialise(schedule1Doc),StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
      Files.writeString(FileSystems.getDefault().getPath(args[1]+File.separator+"Schedule1.json"),JSONComplianceDocumentSerialiser.serialise(schedule1Doc),StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);

    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
    String rootName=args[0];
    fileList=new File(rootName).listFiles();
    System.out.println("Found "+fileList.length+" files");
    //parse sections
    String[] modifiers=new String[] { "Section","Appendix"};
    System.out.println("Parsing Sections");
    ComplianceDocument metaDocument=new ComplianceDocument();
    boolean first=true;
    for (String modifier:modifiers) {
      int startNum=0;
      if (modifier.equals("Section")) startNum+=startSection;
      for (int i=startNum; i < 100;i++) {
          String name=rootName+File.separator+modifier;
          if (modifier.equals("Section")) name+=i;
          if (modifier.equals("Appendix")) name+=String.valueOf((char)(i + 65));
          System.out.println("Trying "+name);
          File f=new File(name);
          if (!f.exists()) break;
          File[] thisDirFiles=f.listFiles(new FNFilter(new String[] {".json"}));
          if (name.equals(rootName+standardsSection)) {
            ComplianceDocument document=new ComplianceDocument();
            Section s=new Section(document);
            s.setMetaData("numbered","global");
            s.setMetaData("dcterms:title",modifier +" "+String.valueOf((char)(i + 65))+": "+"Standards referred to");
            document.addSection(s);
            ArrayList<Object> rows=(ArrayList<Object>) ((HashMap<String,Object>) (standardsReferred.get("SAdata-set"))).get("record");
            for (int z=0; z < rows.size();z++) {
              HashMap<String,Object> row= (HashMap<String,Object>) rows.get(z);
              String text="<b>"+row.get("_SA_DocNumber").toString()+"</b> "+row.get("_SA_DocName").toString();
              Paragraph p=new Paragraph(s);
              p.setMetaData("body",text);
              s.addParagraph(p);
            }
            metaDocument.addSection(s);
            try {
              Files.writeString(FileSystems.getDefault().getPath(args[1]+File.separator+"-"+modifier+i+".html"),XMLComplianceDocumentSerialiser.serialise(document),StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
              Files.writeString(FileSystems.getDefault().getPath(args[1]+File.separator+"-"+modifier+i+".json"),JSONComplianceDocumentSerialiser.serialise(document),StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
          
            } catch (IOException e) {
              e.printStackTrace();
              System.exit(0);
            }
            continue;
          } else if (name.equals(rootName+authoritySection)) {
            ComplianceDocument document=new ComplianceDocument();
            Section s=new Section(document);
            s.setMetaData("dcterms:title",modifier +" "+String.valueOf((char)(i + 65))+": "+"Documents referred to");
            document.addSection(s);
            s.setMetaData("numbered","global");
            ArrayList<Object> rows=(ArrayList<Object>) ((HashMap<String,Object>) (relevantAuthority.get("RAdata-set"))).get("record");
            for (int z=0; z < rows.size();z++) {
              HashMap<String,Object> row= (HashMap<String,Object>) rows.get(z);
              String text="<b>"+row.get("_RelevantAuthority").toString()+"</b> "+row.get("_RA_URL").toString()+" "+row.get("_RA_DocName").toString();
              Paragraph p=new Paragraph(s);
              p.setMetaData("body",text);
              s.addParagraph(p);
            }
            metaDocument.addSection(s);
            try {
              Files.writeString(FileSystems.getDefault().getPath(args[0]+File.separator+"-"+modifier+i+".html"),XMLComplianceDocumentSerialiser.serialise(document),StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
              Files.writeString(FileSystems.getDefault().getPath(args[0]+File.separator+"-"+modifier+i+".json"),JSONComplianceDocumentSerialiser.serialise(document),StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
          
            } catch (IOException e) {
              e.printStackTrace();
              System.exit(0);
            }
            continue;
          }
          if (thisDirFiles.length!=1) {
            System.out.println("Did not find correct number of files in:"+name);
            continue;
          }
          System.out.println("Parsing:"+thisDirFiles[0].getName());
          String content=null;
          try {
              content=Files.readString(thisDirFiles[0].toPath());
          } catch (IOException e) {
            e.printStackTrace();
            System.exit(0);
          }  
          HashMap<String, Object> sectionData = genson.deserialize(content, HashMap.class);
          //parse the tables
          String tableName=name+"/Tables";
          f=new File(tableName);
          HashMap<String,Table> tables=new HashMap<String,Table>();
          if (f.exists()){
            File[] thisDirFilesTables=f.listFiles(new FNFilter(new String[] {".json"}));
            for (int x=0; x< thisDirFilesTables.length;x++) {
              System.out.println("Parsing:"+thisDirFilesTables[x].getName());
              try {
                  content=Files.readString(thisDirFilesTables[x].toPath());
              } catch (IOException e) {
                e.printStackTrace();
                System.exit(0);
              }  
              HashMap<String, Object> table = genson.deserialize(content, HashMap.class);
              String[] tableNameReal=thisDirFilesTables[x].getName().replace(".json","").split("_");
              tables.put(tableNameReal[tableNameReal.length-1],parseTable(table,standardsReferred, relevantAuthority));
            }
          }
          //parse the images
          String imageName=name+"/Diagrams";
          HashMap<String,Figure> images=new HashMap<String,Figure>();
          HashMap<Figure,HashSet<String>> imageData=new HashMap<Figure,HashSet<String>>();
          f=new File(imageName);
          if (f.exists()){
            File[] thisDirFilesImages=f.listFiles(new FNFilter(new String[] {".png",".jpg"}));
            for (int x=0; x< thisDirFilesImages.length;x++) {
              System.out.println("Parsing:"+thisDirFilesImages[x].getName());
              try {
                  byte [] imageByteArray = Files.readAllBytes(thisDirFilesImages[x].toPath());
                  Figure figure=new Figure(null);
                  figure.setImageData(imageByteArray);
                  images.put(thisDirFilesImages[x].getName().toLowerCase(),figure);
                  System.out.println("Adding Figure:"+thisDirFilesImages[x].getName());
              } catch (IOException e) {
                e.printStackTrace();
                System.exit(0);
              }  
            }
            thisDirFilesImages=f.listFiles(new FNFilter(new String[] {".json"}));
            for (int x=0; x< thisDirFilesImages.length;x++) {
              System.out.println("Parsing:"+thisDirFilesImages[x].getName());
              try {
                content=Files.readString(thisDirFilesImages[x].toPath());
                HashMap<String,Object> imageDataIn=genson.deserialize(content, HashMap.class);
                String imgName=imageDataIn.keySet().iterator().next();
                imageDataIn=((HashMap<String,Object>)imageDataIn.get(imgName));
                Object recordVal=imageDataIn.get("record");
                //to deal with the fact that this is sometimes an object or array
                ArrayList<Object> imagesList=new ArrayList<Object>();
                if (recordVal instanceof ArrayList) imagesList=(ArrayList<Object>)recordVal;
                else imagesList.add(recordVal);
                for (Object o: imagesList) {
                  HashMap<String,Object> data=(HashMap<String,Object>)o;
                  System.out.println("_"+imgName+"_Number");
                  String number=data.get("_"+imgName+"_Number").toString();
                  number=number.substring(number.indexOf(".")+1);
                  String imgNameIntName=thisDirFilesImages[x].getName().substring(0,thisDirFilesImages[x].getName().indexOf("_D"))+"_D"+number+".png";
                  Figure figure=images.get(imgNameIntName.toLowerCase());
                  if (figure==null) {
                    System.out.println("Couldn't Resolve Image Name"+imgNameIntName);
                    figure=images.get(imgNameIntName);
                  }
                  if (!figure.hasMetaData("caption")){
                    figure.setMetaData("caption",(String)data.get("_"+imgName+"_Name"));
                  } 
                  if (!imageData.containsKey(figure)) {
                      imageData.put(figure,new HashSet<String>());  
                  }
                  imageData.get(figure).add(data.get("_"+imgName+"_Number").toString());
                  
                }
              }  catch (IOException e) {
                e.printStackTrace();
                System.exit(0);
              }  
            }
          }
          ComplianceDocument document=parseSection(sectionData,tables,imageData,args[1],standardsReferred,relevantAuthority,abbreviations,schedule);
          Section sec= document.getSection(0);
          if (modifier.equals("Appendix")) {
            String oldTitle=sec.getMetaDataString("dcterms:title");
            sec.removeMetaData("dcterms:title");
            sec.setMetaData("dcterms:title",modifier +" "+String.valueOf((char)(i + 65))+": "+oldTitle);
          }
          if (first) {
            Set<String> metaData=document.getMetaDataList();
            for (String dataItem: metaData) {
                if (document.isListMetadata(dataItem)) {
                    ArrayList<String> listMetaData=document.getMetaDataList(dataItem);
                    for (String listMdItem:listMetaData) {
                      metaDocument.setMetaData(dataItem,listMdItem);
                    }
                } else {
                  metaDocument.setMetaData(dataItem,document.getMetaDataString(dataItem));
                }
            }
            first=false;
          }
          metaDocument.addSection(document.getSection(0));
          String s=XMLComplianceDocumentSerialiser.serialise(document);
          String sJ=JSONComplianceDocumentSerialiser.serialise(document);
          try {
            Files.writeString(FileSystems.getDefault().getPath(args[1]+File.separator+args[2]+"-"+modifier+i+".html"),s,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
            Files.writeString(FileSystems.getDefault().getPath(args[1]+File.separator+args[2]+"-"+modifier+i+".json"),sJ,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
        
          } catch (IOException e) {
            e.printStackTrace();
            System.exit(0);
          }
          
      }
    }
    String s=XMLComplianceDocumentSerialiser.serialise(metaDocument);
    String sJ=JSONComplianceDocumentSerialiser.serialise(metaDocument);
    try {
      Files.writeString(FileSystems.getDefault().getPath(args[1]+File.separator+args[2]+".html"),s,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
      Files.writeString(FileSystems.getDefault().getPath(args[1]+File.separator+args[2]+".json"),sJ,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
  
    } catch (IOException e) {
      e.printStackTrace();
      System.exit(0);
    }
    System.exit(1);
  
  }

}
