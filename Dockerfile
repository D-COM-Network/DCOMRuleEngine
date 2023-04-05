FROM tomcat:9.0.44-jdk16-openjdk-buster

LABEL maintainer="beachth@cf.ac.uk"

RUN rm -rf /usr/local/webapps/*
ADD RuleEngine.pem /opt/RuleEngine.pem
ADD dictionary.json /opt/dictionary.json
ENV DCOMCertificatePath /opt/RuleEngine.pem
ENV DCOMDictionaryPath /opt/dictionary.json
ENV DCOMCertificatePassword a5b50932
ENV DCOM_SERVICE_DATA_PATH /opt/ruleenginedata


ARG MAVEN_URL=https://dlcdn.apache.org/maven/maven-3/3.8.6/binaries/apache-maven-3.8.6-bin.tar.gz
RUN mkdir -p /usr/share/maven /usr/share/maven/ref
RUN curl -fsSL -o /tmp/apache-maven.tar.gz ${MAVEN_URL}
RUN tar -xzf /tmp/apache-maven.tar.gz -C /usr/share/maven --strip-components=1 
RUN rm -f /tmp/apache-maven.tar.gz 
RUN ln -s /usr/share/maven/bin/mvn /usr/bin/mvn

ENV MAVEN_HOME /usr/share/maven
ENV M2_HOME /usr/share/maven
ENV MAVEN_CONFIG "/root/.m2"
COPY scripts/settings-docker.xml /usr/share/maven/ref/
COPY scripts/startup.sh /opt/startup.sh
COPY scripts/kmodule.xml /opt/kmodule.xml
COPY scripts/pom.xml /opt/pom.xml
RUN mvn org.apache.maven.plugins:maven-dependency-plugin:3.3.0:get  -Dartifact=org.drools:drools-model-compiler:7.68.0.Final
RUN mvn org.apache.maven.plugins:maven-dependency-plugin:3.3.0:get  -Dartifact=org.kie:kie-maven-plugin:7.68.0.Final
ADD rasecompiler/target/RaseCompiler.jar /opt/RaseCompiler.jar
RUN mvn install:install-file -Dfile=/opt/RaseCompiler.jar -DgroupId=org.dcom -DartifactId=RuleEngineCore -Dversion=1.0 -Dpackaging=jar
ADD ruleengineservice/target/RuleEngine.war /usr/local/tomcat/webapps/ROOT.war

EXPOSE 8080
CMD ["/opt/startup.sh", "run"]
