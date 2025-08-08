export JAVA_HOME="/c/development/IBM/jdk-17.0.9"
export PATH="$JAVA_HOME/bin:$PATH"
mvn clean install
docker build -t homefinance:1.0 .
docker rm -f homefinance
docker run --detach --name=homefinance -p 8585:8585 -p 5005:5005 homefinance:1.0
