FROM ubuntu:trusty

RUN apt-get update && apt-get -y upgrade && apt-get -y install software-properties-common && add-apt-repository ppa:webupd8team/java -y && apt-get update

RUN (echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | /usr/bin/debconf-set-selections) && apt-get install -y oracle-java8-installer oracle-java8-set-default

ENV JAVA_HOME /usr/lib/jvm/java-8-oracle
ENV PATH $JAVA_HOME/bin:$PATH

RUN apt-get update && apt-get install -y \
tcpdump

ADD target ./mudgee

CMD java -version

CMD java -jar mudgee/mudgee-1.0.0-SNAPSHOT.jar mudgee/mud_config.json 