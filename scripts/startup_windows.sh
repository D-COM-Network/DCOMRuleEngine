#! /bin/bash

# get a list of compliance documents to build
mkdir /c/documents
cd /c/documents
java -cp /c/ProgramData/RaseCompiler.jar org.dcom.ruleengine.core.DocumentListGenerator
export MAVEN_OPTS='-Xmx3000m'

if [ "$(ls -A ./)" ]; then
  
  # compile the documents
  for d in ./*/ ; do
     echo $d
     mkdir -p /c/rule/src/main/resources/META-INF
     cp /c/ProgramData/kmodule.xml /c/rule/src/main/resources/META-INF
     cp /c/ProgramData/pom.xml /c/rule
     i=1
     for f in $d/*.html ; do 
     	echo $f
     	java -cp /c/ProgramData/RaseCompiler.jar org.dcom.ruleengine.rasecompiler.RASEDRLCompiler "$f" /c/ProgramData/dictionary.json > /c/rule/src/main/resources/rule$i.drl
      ((i=i+1))
     done
     cd /c/rule
     d1="${d//\//}"
     d1="${d1//./}"
     sed -i.bak -e "s/\RULE_NAME/$d1/" pom.xml 
     mvn install
     cd /c
     rm -rf /c/rule
     cd /c/documents
  done 

fi
rm -rf /c/documents