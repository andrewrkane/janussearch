all: janus.jar

janus.jar: ./src/*.java ./src/janusengine/*.java
	mkdir -p bin
	javac -source 1.6 -target 1.6 -d ./bin -sourcepath src -cp lucene.jar:bin ./src/*.java ./src/janusengine/*.java
	jar cvf janus.jar -C ./bin/ .

clean:
	rm -rf ./bin janus.jar

