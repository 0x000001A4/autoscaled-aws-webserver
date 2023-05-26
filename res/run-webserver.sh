mvn clean compile package
java -cp webserver/target/webserver-1.0.0-SNAPSHOT-jar-with-dependencies.jar -javaagent:instrumentation/target/JavassistWrapper-1.0-jar-with-dependencies.jar=PrintMetrics:pt.ulisboa.tecnico.cnv:output  pt.ulisboa.tecnico.cnv.webserver.WebServer
