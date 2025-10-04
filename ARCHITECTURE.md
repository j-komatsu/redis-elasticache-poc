# アーキテクチャ図とシーケンス図

このドキュメントでは、Redis ElastiCache PoCプロジェクトのアーキテクチャとデータフローを視覚的に説明します。

---

## 📐 システムアーキテクチャ

### 全体構成図

```mermaid
graph TB
    subgraph Internet["インターネット"]
        User["👤 ユーザー"]
    end

    subgraph AWS["AWS Cloud (ap-northeast-1)"]
        subgraph VPC["VPC (172.31.0.0/16)"]
            subgraph PublicSubnet["パブリックサブネット"]
                ELB["Elastic Load Balancer<br/>(オプション)"]
                EC2["EC2 Instance<br/>(Beanstalk)<br/>t3.micro"]
            end

            subgraph PrivateSubnet["プライベートサブネット"]
                Redis1["ElastiCache Redis<br/>cache.t3.micro<br/>(AZ: 1a)"]
            end

            IGW["Internet Gateway"]
            SG_EB["Security Group<br/>redis-poc-sg"]
            SG_Redis["Security Group<br/>redis-poc-sg"]
        end
    end

    User -->|"HTTP Request"| EC2
    EC2 -->|"Internet Gateway"| IGW
    IGW -->|"Route to Internet"| Internet

    EC2 -.->|"Port 6379<br/>(内部通信)"| Redis1

    EC2 -.-|"所属"| SG_EB
    Redis1 -.-|"所属"| SG_Redis

    style User fill:#f9f,stroke:#333,stroke-width:2px
    style EC2 fill:#bbf,stroke:#333,stroke-width:2px
    style Redis1 fill:#fbb,stroke:#333,stroke-width:2px
    style VPC fill:#efe,stroke:#333,stroke-width:2px
    style PublicSubnet fill:#ddf,stroke:#333,stroke-width:2px
    style PrivateSubnet fill:#fdd,stroke:#333,stroke-width:2px
```

### ネットワーク詳細図

```mermaid
graph LR
    subgraph VPC["VPC: vpc-xxxxxxxxxxxxxxxxx (172.31.0.0/16)"]
        subgraph AZ1a["Availability Zone: ap-northeast-1a"]
            PubSub1["パブリックサブネット<br/>172.31.32.0/20"]
            PrivSub1["プライベートサブネット<br/>private-subnet-1a<br/>172.31.100.0/24"]
        end

        subgraph AZ1c["Availability Zone: ap-northeast-1c"]
            PubSub2["パブリックサブネット<br/>172.31.0.0/20"]
            PrivSub2["プライベートサブネット<br/>private-subnet-1c<br/>172.31.101.0/24"]
        end

        subgraph AZ1d["Availability Zone: ap-northeast-1d"]
            PubSub3["パブリックサブネット<br/>172.31.16.0/20"]
        end

        RT_Pub["パブリックルートテーブル<br/>0.0.0.0/0 → IGW"]
        RT_Priv["プライベートルートテーブル<br/>local only"]

        IGW["Internet Gateway"]
    end

    PubSub1 -.-> RT_Pub
    PubSub2 -.-> RT_Pub
    PubSub3 -.-> RT_Pub
    RT_Pub --> IGW

    PrivSub1 -.-> RT_Priv
    PrivSub2 -.-> RT_Priv

    style PubSub1 fill:#ddf,stroke:#333
    style PubSub2 fill:#ddf,stroke:#333
    style PubSub3 fill:#ddf,stroke:#333
    style PrivSub1 fill:#fdd,stroke:#333
    style PrivSub2 fill:#fdd,stroke:#333
    style RT_Pub fill:#bfb,stroke:#333
    style RT_Priv fill:#fbb,stroke:#333
    style IGW fill:#bbf,stroke:#333
```

---

## 🔄 データフローとシーケンス図

### 1. ヘルスチェックのシーケンス

```mermaid
sequenceDiagram
    participant User as 👤 ユーザー
    participant ELB as Elastic Load Balancer
    participant App as Spring Boot App<br/>(EC2)
    participant Redis as ElastiCache Redis

    User->>App: GET /api/cache/health
    activate App

    App->>Redis: PING (接続確認)
    activate Redis
    Redis-->>App: PONG
    deactivate Redis

    App->>Redis: SET health-check "OK-{timestamp}"
    activate Redis
    Redis-->>App: OK
    deactivate Redis

    App->>Redis: GET health-check
    activate Redis
    Redis-->>App: "OK-{timestamp}"
    deactivate Redis

    App->>Redis: DEL health-check
    activate Redis
    Redis-->>App: 1 (削除成功)
    deactivate Redis

    App-->>User: 200 OK<br/>{"success": true, "message": "Redis connection is healthy"}
    deactivate App
```

### 2. データ保存（POST）のシーケンス

```mermaid
sequenceDiagram
    participant User as 👤 ユーザー
    participant App as Spring Boot App
    participant Redis as ElastiCache Redis

    User->>App: POST /api/cache<br/>{"key": "user:1001", "value": "John Doe", "ttlSeconds": 300}
    activate App

    App->>App: バリデーション<br/>(key必須チェック)

    alt TTL指定あり
        App->>Redis: SETEX user:1001 300 "John Doe"
        activate Redis
        Redis-->>App: OK
        deactivate Redis
        App-->>User: 200 OK<br/>{"success": true, "message": "Data saved with TTL: 300 seconds"}
    else TTL指定なし
        App->>Redis: SET user:1001 "John Doe"
        activate Redis
        Redis-->>App: OK
        deactivate Redis
        App-->>User: 200 OK<br/>{"success": true, "message": "Data saved successfully"}
    end

    deactivate App
```

### 3. データ取得（GET）のシーケンス

```mermaid
sequenceDiagram
    participant User as 👤 ユーザー
    participant App as Spring Boot App
    participant Redis as ElastiCache Redis

    User->>App: GET /api/cache/user:1001
    activate App

    App->>Redis: GET user:1001
    activate Redis

    alt データが存在
        Redis-->>App: "John Doe"
        deactivate Redis
        App-->>User: 200 OK<br/>{"success": true, "message": "Data found", "data": "John Doe"}
    else データが存在しない
        Redis-->>App: nil
        deactivate Redis
        App-->>User: 200 OK<br/>{"success": true, "message": "No data found", "data": null}
    end

    deactivate App
```

### 4. TTL確認のシーケンス

```mermaid
sequenceDiagram
    participant User as 👤 ユーザー
    participant App as Spring Boot App
    participant Redis as ElastiCache Redis

    User->>App: GET /api/cache/user:1001/ttl
    activate App

    App->>Redis: TTL user:1001
    activate Redis

    alt TTLが設定されている
        Redis-->>App: 245 (残り秒数)
        deactivate Redis
        App-->>User: 200 OK<br/>{"success": true, "message": "TTL: 245 seconds", "data": 245}
    else TTLが設定されていない
        Redis-->>App: -1
        deactivate Redis
        App-->>User: 200 OK<br/>{"success": true, "message": "No expiration set", "data": -1}
    else キーが存在しない
        Redis-->>App: -2
        deactivate Redis
        App-->>User: 200 OK<br/>{"success": true, "message": "Key does not exist", "data": -2}
    end

    deactivate App
```

### 5. データ削除（DELETE）のシーケンス

```mermaid
sequenceDiagram
    participant User as 👤 ユーザー
    participant App as Spring Boot App
    participant Redis as ElastiCache Redis

    User->>App: DELETE /api/cache/user:1001
    activate App

    App->>Redis: DEL user:1001
    activate Redis
    Redis-->>App: 1 (削除された個数)
    deactivate Redis

    App-->>User: 200 OK<br/>{"success": true, "message": "Data deleted successfully"}
    deactivate App
```

---

## 🏗️ アプリケーション内部構造

### Spring Bootコンポーネント図

```mermaid
graph TB
    subgraph Client["クライアント層"]
        HTTP["HTTP Request"]
    end

    subgraph Controller["Controller層"]
        CacheController["CacheController<br/>@RestController"]
    end

    subgraph Service["Service層"]
        CacheService["CacheService<br/>@Service"]
    end

    subgraph Config["設定層"]
        RedisConfig["RedisConfig<br/>@Configuration"]
    end

    subgraph SpringDataRedis["Spring Data Redis"]
        RedisTemplate["RedisTemplate<br/>&lt;String, Object&gt;"]
    end

    subgraph Client_Layer["Redis Client"]
        Jedis["Jedis Client"]
    end

    subgraph External["外部サービス"]
        ElastiCache["ElastiCache Redis<br/>(port 6379)"]
    end

    HTTP --> CacheController
    CacheController --> CacheService
    CacheService --> RedisTemplate
    RedisConfig -.->|"Bean定義"| RedisTemplate
    RedisTemplate --> Jedis
    Jedis -->|"RESP Protocol"| ElastiCache

    style CacheController fill:#bbf,stroke:#333,stroke-width:2px
    style CacheService fill:#bfb,stroke:#333,stroke-width:2px
    style RedisConfig fill:#fbb,stroke:#333,stroke-width:2px
    style RedisTemplate fill:#fbf,stroke:#333,stroke-width:2px
    style ElastiCache fill:#f96,stroke:#333,stroke-width:3px
```

### クラス関連図

```mermaid
classDiagram
    class CacheController {
        -CacheService cacheService
        +saveCache(CacheRequest) ResponseEntity
        +getCache(String key) ResponseEntity
        +deleteCache(String key) ResponseEntity
        +checkExists(String key) ResponseEntity
        +getTTL(String key) ResponseEntity
        +healthCheck() ResponseEntity
    }

    class CacheService {
        -RedisTemplate redisTemplate
        +save(String key, Object value) void
        +saveWithExpiry(String key, Object value, long timeout, TimeUnit unit) void
        +get(String key) Object
        +delete(String key) void
        +exists(String key) boolean
        +setExpire(String key, long timeout, TimeUnit unit) boolean
        +getExpire(String key) Long
    }

    class RedisConfig {
        +redisConnectionFactory() JedisConnectionFactory
        +redisTemplate() RedisTemplate
    }

    class CacheRequest {
        +String key
        +String value
        +Long ttlSeconds
    }

    class CacheResponse {
        +boolean success
        +String message
        +Object data
        +success(String message) CacheResponse
        +error(String message) CacheResponse
    }

    CacheController --> CacheService : uses
    CacheController --> CacheRequest : receives
    CacheController --> CacheResponse : returns
    CacheService --> RedisTemplate : uses
    RedisConfig ..> RedisTemplate : creates
```

---

## 🔐 セキュリティグループとネットワークACL

### セキュリティグループ構成

```mermaid
graph TB
    subgraph SG_Redis["Security Group: redis-poc-sg"]
        direction TB
        SG_Redis_In["インバウンドルール<br/>TCP 6379<br/>ソース: 172.31.0.0/16"]
        SG_Redis_Out["アウトバウンドルール<br/>All Traffic<br/>送信先: 0.0.0.0/0"]
    end

    subgraph Resources["リソース"]
        EC2_Instance["EC2 Instance<br/>(Beanstalk)"]
        Redis_Cluster["ElastiCache<br/>Redis Cluster"]
    end

    EC2_Instance -.->|"所属"| SG_Redis
    Redis_Cluster -.->|"所属"| SG_Redis

    EC2_Instance -->|"TCP 6379<br/>許可"| Redis_Cluster

    style SG_Redis fill:#ffe,stroke:#333,stroke-width:2px
    style EC2_Instance fill:#bbf,stroke:#333,stroke-width:2px
    style Redis_Cluster fill:#fbb,stroke:#333,stroke-width:2px
```

---

## 📊 デプロイメントフロー

### CI/CDパイプライン（手動デプロイ版）

```mermaid
graph LR
    subgraph Development["開発環境"]
        Code["📝 コード編集"]
        Build["🔨 Gradle Build<br/>./gradlew clean bootJar"]
        JAR["📦 application.jar<br/>(31MB)"]
    end

    subgraph AWS_Console["AWS Console"]
        Upload["⬆️ アップロード<br/>Beanstalk Console"]
        Deploy["🚀 デプロイ実行"]
    end

    subgraph Beanstalk["Elastic Beanstalk"]
        Extract["📂 JAR展開"]
        Start["▶️ アプリ起動<br/>Port 5000"]
        Health["✅ ヘルスチェック"]
    end

    subgraph Production["本番環境"]
        Running["🌐 稼働中<br/>your-app-env.elasticbeanstalk.com"]
    end

    Code --> Build
    Build --> JAR
    JAR --> Upload
    Upload --> Deploy
    Deploy --> Extract
    Extract --> Start
    Start --> Health
    Health -->|"成功"| Running
    Health -->|"失敗"| Start

    style Code fill:#bfb,stroke:#333
    style Build fill:#bbf,stroke:#333
    style JAR fill:#fbf,stroke:#333
    style Running fill:#f96,stroke:#333,stroke-width:3px
```

---

## 🎯 リクエストフロー全体像

### エンドツーエンドのデータフロー

```mermaid
graph TB
    subgraph User_Device["ユーザーデバイス"]
        Browser["🌐 Webブラウザ<br/>or curl"]
    end

    subgraph AWS_Cloud["AWS Cloud"]
        subgraph Beanstalk_Env["Elastic Beanstalk"]
            Nginx["Nginx<br/>(Reverse Proxy)<br/>Port 80"]
            SpringBoot["Spring Boot App<br/>Port 5000"]
        end

        subgraph ElastiCache_Env["ElastiCache"]
            Redis["Redis OSS<br/>Port 6379"]
        end
    end

    Browser -->|"1. HTTP GET/POST/DELETE<br/>http://your-app-env.elasticbeanstalk.com"| Nginx
    Nginx -->|"2. Proxy Pass<br/>http://localhost:5000"| SpringBoot
    SpringBoot -->|"3. Redis Protocol<br/>SET/GET/DEL"| Redis
    Redis -->|"4. Response<br/>OK/Value/nil"| SpringBoot
    SpringBoot -->|"5. JSON Response"| Nginx
    Nginx -->|"6. HTTP Response"| Browser

    style Browser fill:#f9f,stroke:#333,stroke-width:2px
    style Nginx fill:#9cf,stroke:#333,stroke-width:2px
    style SpringBoot fill:#bbf,stroke:#333,stroke-width:2px
    style Redis fill:#fbb,stroke:#333,stroke-width:2px
```

---

## 📈 スケーラビリティ構成（将来の拡張）

### オートスケーリング構成（参考）

```mermaid
graph TB
    subgraph Internet["インターネット"]
        Users["👥 ユーザー"]
    end

    subgraph AWS["AWS Cloud"]
        ALB["Application Load Balancer"]

        subgraph AutoScaling["Auto Scaling Group"]
            EC2_1["EC2 Instance 1"]
            EC2_2["EC2 Instance 2"]
            EC2_N["EC2 Instance N"]
        end

        subgraph ElastiCache_Cluster["ElastiCache Cluster Mode"]
            Redis_Primary["Redis Primary<br/>Node 1"]
            Redis_Replica1["Redis Replica<br/>Node 2"]
            Redis_Replica2["Redis Replica<br/>Node 3"]
        end
    end

    Users --> ALB
    ALB --> EC2_1
    ALB --> EC2_2
    ALB --> EC2_N

    EC2_1 -.-> Redis_Primary
    EC2_2 -.-> Redis_Primary
    EC2_N -.-> Redis_Primary

    Redis_Primary -.->|"Replication"| Redis_Replica1
    Redis_Primary -.->|"Replication"| Redis_Replica2

    style Users fill:#f9f,stroke:#333,stroke-width:2px
    style ALB fill:#9cf,stroke:#333,stroke-width:2px
    style Redis_Primary fill:#f66,stroke:#333,stroke-width:3px
    style Redis_Replica1 fill:#fbb,stroke:#333
    style Redis_Replica2 fill:#fbb,stroke:#333
```

---

## 🔄 エラーハンドリングフロー

### 接続エラー時の処理

```mermaid
sequenceDiagram
    participant User as 👤 ユーザー
    participant App as Spring Boot App
    participant Redis as ElastiCache Redis

    User->>App: GET /api/cache/user:1001
    activate App

    App->>Redis: GET user:1001
    activate Redis

    Redis--xApp: ❌ Connection Timeout<br/>(ネットワークエラー)
    deactivate Redis

    App->>App: Exception Catch<br/>ログ出力

    App-->>User: 500 Internal Server Error<br/>{"success": false,<br/>"message": "Error: Connection timeout"}
    deactivate App
```

---

## 📝 まとめ

このドキュメントでは、以下の図を使ってシステムを説明しました：

1. **システムアーキテクチャ**: AWS全体の構成
2. **ネットワーク詳細図**: VPC、サブネット、ルートテーブルの関係
3. **シーケンス図**: 各API操作のデータフロー
4. **コンポーネント図**: Spring Bootアプリケーションの内部構造
5. **クラス関連図**: Javaクラスの依存関係
6. **セキュリティグループ**: ネットワークアクセス制御
7. **デプロイメントフロー**: ビルドからデプロイまでの流れ
8. **リクエストフロー**: エンドツーエンドの全体像
9. **スケーラビリティ構成**: 将来の拡張案
10. **エラーハンドリング**: エラー時の処理フロー

これらの図を参考に、システムの理解を深めてください。
