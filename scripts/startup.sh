#! /bin/bash

# get a list of compliance documents to build
mkdir /root/documents
cd /root/documents
java -cp /opt/RaseCompiler.jar org.dcom.ruleengine.core.DocumentListGenerator

# compile the documents
for d in ./*/ ; do
   echo $d
   mkdir -p /root/rule/src/main/resources/META-INF
   cp /opt/kmodule.xml /root/rule/src/main/resources/META-INF
   cp /opt/pom.xml /root/rule
   i=1
   for f in $d/*.html ; do 
   	echo $f
   	java -cp /opt/RaseCompiler.jar org.dcom.ruleengine.rasecompiler.RASEDRLCompiler "$f" /opt/dictionary.json > /root/rule/src/main/resources/rule$i.drl
    ((i=i+1))
   done
   cd /root/rule
   d1="${d//\//}"
   d1="${d1//./}"
   sed -i.bak -e "s/\RULE_NAME/$d1/" pom.xml 
   mvn install
   cd /root
   rm -rf /root/rule
   cd /root/documents
done 

# start the rule engine service
rm -rf /root/documents
cd /usr/local/tomcat/bin
./catalina.sh run
