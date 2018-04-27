Simple example of Spark with Scala running on Docker Ubuntu

Build:
```
mvn package
```

Run:
```
spark-submit --master local target/triangle-count-0.1.0-jar-with-dependencies.jar
```

Build docker image:
```
docker build -t custom-triangle-count .
```

Run docker image:
```
docker run custom-triangle-count
```

Connect to container:
```
docker run -i -t custom-triangle-count /bin/bash
```
