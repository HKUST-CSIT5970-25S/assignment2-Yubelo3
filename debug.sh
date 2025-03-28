#!/bin/bash

mvn clean package --settings settings.xml
# hadoop jar target/assignment-2-1.0-SNAPSHOT.jar hk.ust.csit5970.BigramFrequencyPairs -input 1400-8.txt -output bc -numReducers 2
# hadoop jar target/assignment-2-1.0-SNAPSHOT.jar hk.ust.csit5970.BigramFrequencyStripes -input 1400-8.txt -output bc -numReducers 2
# hadoop jar target/assignment-2-1.0-SNAPSHOT.jar hk.ust.csit5970.AnalyzeBigramFrequency -input bc -word the
# hadoop jar target/assignment-2-1.0-SNAPSHOT.jar hk.ust.csit5970.CORPairs -input cor-demo-sample.txt -output cor -numReducers 1
hadoop jar target/assignment-2-1.0-SNAPSHOT.jar hk.ust.csit5970.CORStripes -input cor-demo-sample.txt -output cor -numReducers 1
rm -r cor mid
hadoop fs -get mid mid
hadoop fs -get cor cor

