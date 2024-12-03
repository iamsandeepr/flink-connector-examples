# #Flink Connectors
Customer details events received in the source Kafka topic will be sunk into two separate kafka topics based on the age of the customer. 
Customers whose age is an odd number will be sunk to odd topic and even number to even topic.
All the events will be sunk to OpenSearch database also.

## Getting started
1. JDK 11
2. Docker
3. Lombok

## Integration Testing
1. Start the `FlinkConnectorExampleTest` test class. Make sure docker is running in your machine
2. The above test class will spin up a OpenSearch TestContainer and an in-memory Kafka broker

Test on Mac
