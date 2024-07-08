all: janus.jar data index

janus.jar: src/*.java src/janusengine/*.java
	mkdir -p bin
	javac -source 1.6 -target 1.6 -d ./bin -sourcepath src -cp lucene.jar:bin ./src/*.java ./src/janusengine/*.java
	jar cvf janus.jar -C ./bin/ .

clean:
	rm -rf bin janus.jar data index

data: data/mf data/lp data/vc

data/mf: _original/_mf.xml
	# extract MF data
	java -classpath janus.jar janusengine.SplitMF_XML _original/_mf.xml data/mf/
	rm -f data/mf/*ubi.txt

data/lp: _original/_lp.xml
	# extract LP data
	cat _original/_lp.xml | sed -e 's/H>/wd>/g' -e 's/T>/q>/g' -e 's/<i>//g' -e 's/<\/i>//g' -e 's/<W>//g' -e 's/<\/W>//g' -e 's/<S>/<cite>/g' -e 's/<\/S>/<\/cite>/g' > _lp.tmp
	java -classpath janus.jar janusengine.SplitMF_XML _lp.tmp data/lp/
	rm -f data/lp/*ubi.txt _lp.tmp

data/vc: _original/_vc.xml
	# extract VC data
	cat _original/_vc.xml | sed -e 's/H>/wd>/g' -e 's/T>/q>/g' -e 's/<i>//g' -e 's/<\/i>//g' -e 's/<W>//g' -e 's/<\/W>//g' -e 's/<S>/<cite>/g' -e 's/<\/S>/<\/cite>/g' > _vc.tmp
	java -classpath janus.jar janusengine.SplitMF_XML _vc.tmp data/vc/
	rm -f data/vc/*ubi.txt _vc.tmp

index: data/*/*
	# create index
	java -classpath janus.jar:lucene.jar JanusCreateIndex

# create alldata-*.txt files
#sh combine-data.sh mf
#sh combine-data.sh lp
#sh combine-data.sh vc

