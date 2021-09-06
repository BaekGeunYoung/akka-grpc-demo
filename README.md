## akka-grpc-demo
akka framework를 이용한 event sourcing architecture 구현 예시 프로젝트

## tech stacks

- scala 3
- akka-grpc
- akka-actor
- akka-persistence (with cassandra)
- akka-cluster
- akka-management

## scala 3 + akka 2.6.16

현재 akka 최신 버전은 scala 3를 공식적으로 지원하지 않기 때문에 2.13용 akka를 임포트해야 한다.

### how to import dependencies for scala 2.13
기본적으로 scala 3를 사용하는 프로젝트에서 2.13용 라이브러리를 임포트하려면 아래와 같이 `CrossVersion`을 사용하면 된다.

`build.sbt`

```sbt
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream" % AkkaVersion cross CrossVersion.for3Use2_13,
  "com.typesafe.akka" %% "akka-cluster-typed" % AkkaVersion cross CrossVersion.for3Use2_13,
  "com.typesafe.akka" %% "akka-cluster-sharding-typed" % AkkaVersion cross CrossVersion.for3Use2_13,
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test cross CrossVersion.for3Use2_13,
  "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion % Test cross CrossVersion.for3Use2_13,
  ...
)
```

### how to import akka-grpc plugin

akka framework 중 `akka-grpc`는 plugin의 형태로 지원이 되기 때문에 위와는 다른 방법으로 임포트해야 한다. 기본적으로는 `project/plugins.sbt`에 다음 코드를 추가하면 된다. (2.0.0 버전까지는 scala 3와 호환이 안되는 버그가 있어 2.1.0부터 사용 가능)

```sbt
addSbtPlugin("com.lightbend.akka.grpc" % "sbt-akka-grpc" % "2.1.0")
```

그리고 `build.sbt`에 아래 코드블럭을 추가해야 한다.

```sbt
scalaVersion := "3.0.1"

// akka-grpc plugin에 포함된 dependency들을 2.13 기준으로 받아오기 위함
inConfig(Compile)(scalaBinaryVersion := "2.13")

// plugin 활성화
enablePlugins(AkkaGrpcPlugin)

/**
 * com.thesamet.scalapb 라이브러리의 경우 3와 2.13 모두 호환이 되어서 그냥 두면 에러가 남.
 * 그래서 여기서는 일단 제거해주고 아래 부분에서 명시적으로 2.13용으로 다시 임포트해줌.
 */
lazy val util = (project in file("."))
  .settings(
    libraryDependencies ~= (_.filter { module =>
      module.organization != "com.thesamet.scalapb"
    })
  )

libraryDependencies ++= Seq(
  ...
  // 2.13용으로 다시 임포트
  "com.thesamet.scalapb" %% "lenses" % "0.11.3" cross CrossVersion.for3Use2_13,
  "com.thesamet.scalapb" %% "scalapb-runtime" % "0.11.3" cross CrossVersion.for3Use2_13,
)
```

## 코드 설명

### Main

- main 함수 정의
  - ActorSystem 생성
  - AkkaManagement 및 ClusterBootStrap 초기화
  - entity ClusterSharding 초기화
  - ShoppingCartServer.start 호출

### ShoppingCartServer

- HttpServer 생성 및 시작
- GrpcService (ShoppingCartService) 바인딩

### ShoppingCartServiceImpl

- GrpcService Interface 구현
- ClusterSharding 인스턴스를 통해 entityRef에 접근
- entityRef를 통해 persistent Actor와 통신

### ShoppingCart

- EventSourcedBehavior 정의
- Command, Event, State 정의
- CommandHandler & EventHandler 정의
- 비즈니스 로직이 담기는 곳

### ProtobufSerializer

- Event를 DB에 persist할 때 protobuf로 serialize하여 저장하도록 serialization 규칙 정의

### DemoHealthCheck

- akka-management가 제공해주는 health check API endpoint에 연동시킬 health check 로직

## akka-persistence-Cassandra

configuration에서 keyspace 및 table autocreate을 활성화시킬 시 아래 명세대로 테이블이 생성됨.

### Messages table

```sql
CREATE TABLE IF NOT EXISTS akka.messages (
  persistence_id text,
  partition_nr bigint,
  sequence_nr bigint,
  timestamp timeuuid,
  timebucket text,
  writer_uuid text,
  ser_id int,
  ser_manifest text,
  event_manifest text,
  event blob,
  meta_ser_id int,
  meta_ser_manifest text,
  meta blob,
  tags set<text>,
  PRIMARY KEY ((persistence_id, partition_nr), sequence_nr, timestamp))
  WITH gc_grace_seconds =864000
  AND compaction = {
    'class' : 'SizeTieredCompactionStrategy',
    'enabled' : true,
    'tombstone_compaction_interval' : 86400,
    'tombstone_threshold' : 0.2,
    'unchecked_tombstone_compaction' : false,
    'bucket_high' : 1.5,
    'bucket_low' : 0.5,
    'max_threshold' : 32,
    'min_threshold' : 4,
    'min_sstable_size' : 50
    };
```
(persistence_id, partition_nr)이 partition key이고, (sequence_nr, timestamp)가 clsutering key임.

### Tag_views table

```sql
CREATE TABLE IF NOT EXISTS akka.tag_views (
  tag_name text,
  persistence_id text,
  sequence_nr bigint,
  timebucket bigint,
  timestamp timeuuid,
  tag_pid_sequence_nr bigint,
  writer_uuid text,
  ser_id int,
  ser_manifest text,
  event_manifest text,
  event blob,
  meta_ser_id int,
  meta_ser_manifest text,
  meta blob,
  PRIMARY KEY ((tag_name, timebucket), timestamp, persistence_id, tag_pid_sequence_nr))
  WITH gc_grace_seconds =864000
  AND compaction = {
    'class' : 'SizeTieredCompactionStrategy',
    'enabled' : true,
    'tombstone_compaction_interval' : 86400,
    'tombstone_threshold' : 0.2,
    'unchecked_tombstone_compaction' : false,
    'bucket_high' : 1.5,
    'bucket_low' : 0.5,
    'max_threshold' : 32,
    'min_threshold' : 4,
    'min_sstable_size' : 50
    };
```

이벤트별로 tag를 지정할 수 있는데, tag별로 이벤트를 query하고 싶을 때 `tag_views` 테이블 활용 가능

## how to run application

### local에서 실행하기

먼저 docker-compose로 cassandra 실행

```
docker-compose up -d
```

터미널 3개 켜고 아래와 같이 configuration file을 달리 해서 어플리케이션 실행

```
// 터미널마다 하나씩 실행
sbt -Dconfig.resource=local1.conf run

sbt -Dconfig.resource=local2.conf run

sbt -Dconfig.resource=local3.conf run
```

노드가 잘 뜬 후에 `http://127.0.0.1:9191/bootstrap/seed-nodes` 로 요청을 보내보면 아래와 같이 cluster node들에 대한 정보를 얻을 수 있음.

```
{
    "seedNodes": [
        {
            "node": "akka://ShoppingCartService@127.0.0.1:2551",
            "nodeUid": -4752708708757340769,
            "roles": [
                "dc-default"
            ],
            "status": "Up"
        },
        {
            "node": "akka://ShoppingCartService@127.0.0.1:2552",
            "nodeUid": -3086382165092431550,
            "roles": [
                "dc-default"
            ],
            "status": "Up"
        },
        {
            "node": "akka://ShoppingCartService@127.0.0.1:2553",
            "nodeUid": -3920310837560852947,
            "roles": [
                "dc-default"
            ],
            "status": "Up"
        }
    ],
    "selfNode": "akka://ShoppingCartService@127.0.0.1:2551"
}
```

그리고 `grpcui`나 `grpcurl` 등을 이용해 request를 보내본다.

```
// 최초 액터 생성됨
grpcurl -d '{"cartId":"cart1", "itemId":"asdasd", "quantity": 10}' -plaintext 127.0.0.1:8101 shoppingcart.ShoppingCartService.AddItem

// 액터가 생성된 노드와 다른 노드에 request를 보내도 정상적으로 작동함
grpcurl -d '{"cartId":"cart1", "itemId":"asdasd", "quantity": 10}' -plaintext 127.0.0.1:8102 shoppingcart.ShoppingCartService.UpdateItem

grpcurl -d '{"cartId":"cart1"}' -plaintext 127.0.0.1:8103 shoppingcart.ShoppingCartService.GetCart
```

### kubernetes에서 띄우기

configuration file에서 kubernetes API를 이용해 akka discovery를 구성하도록 설정할 수 있다.

`clsuter.conf`
```
akka.management {
  http {
    port = 8558
  }

  cluster.bootstrap {
    contact-point-discovery {
      # pick the discovery method you'd like to use:
      discovery-method = kubernetes-api

      required-contact-point-nr = ${REQUIRED_CONTACT_POINT_NR}
    }
  }

  health-checks {
    readiness-checks {
      example-ready = "shopping.cart.DemoHealthCheck"
    }
  }
}

akka.discovery {
  kubernetes-api {
    pod-label-selector = "app=shopping-cart"
  }
}
```

minikube 설치하고 아래 명령어를 입력하면 클러스터를 구성할 수 있음. (카산드라는 포함돼있지 않아서 실제 동작은 안함. akka-management를 통한 cluster 정보 확인까지는 가능)

```
kubectl apply -f kubernetes/namespace.json
kubectl apply -f kubernetes/shopping-cart-cluster.yml

// shopping-cart deployment를 LoadBalancer 타입의 Service로 노출시키기
kubectl expose deployment shopping-cart --type=LoadBalancer --name=shopping-cart-service

// minikube에 생성된 service에 접근하도록 포트 열어주기
minikube service shopping-cart-service -n shopping-cart-1
```

1. actor에 command1 보냄
2. command1 handler에서 program: (command, state) => IO(Result, List[Event])를 실행
3. program을 실행해서 나온 future를 pipeToSelf해서 새로운 command2를 만들어 보냄
4. 그리고 Effect.stash()
5. command2 handler에서는 메세지에 담겨져 온 Event를 persist하고 result를 replyTo에 보냄
6. Effect.unstashAll()

원하는 동작:
```
1. actor에 command를 보내면
2. future1을 호출하고
3-1. future1의 결과가 A면 future2 호출
   3-1-1. future2의 결과가 C면 event 1,2,3 + result1 리턴
   3-1-2. future2의 결과가 D면 event 1,2,4 + result2 리턴
3-2. future1의 결과가 B면 future3 호출
   3-2-1. future2의 결과가 E면 event 1,5,6 + result3 리턴
   3-2-2. future2의 결과가 F면 event 1,5,7 + result4 리턴
```
