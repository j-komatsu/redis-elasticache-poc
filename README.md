# Redis ElastiCache PoC

AWS ElastiCache for Redis OSSに接続するSpring BootアプリケーションのPoC（概念実証）プロジェクトです。

## 📚 ドキュメント目次

| ドキュメント | 説明 |
|-----------|------|
| **[INDEX.md](INDEX.md)** | 📑 全ドキュメントの索引と目的別ガイド |
| **[README.md](README.md)** | このファイル - プロジェクト概要とローカル開発手順 |
| **[ARCHITECTURE.md](ARCHITECTURE.md)** | 🏗️ アーキテクチャ図とシーケンス図（Mermaid） |
| **[DEPLOYMENT_SUMMARY.md](DEPLOYMENT_SUMMARY.md)** | 📖 完全なデプロイメントレポート（実行コマンドとテスト結果） |
| **[ELASTICACHE_SETUP.md](ELASTICACHE_SETUP.md)** | AWS ElastiCache for Redis OSS作成の完全ガイド |
| **[DEPLOY.md](DEPLOY.md)** | Elastic Beanstalkへのデプロイ手順 |
| **[PROGRESS.md](PROGRESS.md)** | プロジェクト進捗チェックリスト |

> 💡 **初めての方は**: [INDEX.md](INDEX.md) でドキュメント全体を把握してから、[ARCHITECTURE.md](ARCHITECTURE.md) で構成を理解し、[DEPLOYMENT_SUMMARY.md](DEPLOYMENT_SUMMARY.md) で実装例を確認することをお勧めします。

## ✅ プロジェクト完了状態

- ✅ Spring Bootアプリケーション開発完了
- ✅ AWS ElastiCache Redis クラスター構築完了
- ✅ AWS Elastic Beanstalkデプロイ完了
- ✅ すべてのAPIエンドポイントのテスト成功

**デプロイ済みURL**: `http://your-app-env.elasticbeanstalk.com`

## 🎯 このプロジェクトについて

このPoCでは以下を実装・検証しました：

- Spring Data Redisを使用したキャッシュ操作
- AWS ElastiCache for Redis OSSとの連携
- TTL（有効期限）を使用したキャッシュ管理
- VPC内のプライベート/パブリックサブネット構成
- Elastic Beanstalkへのデプロイと運用

## 前提条件

- Java 17以上
- Gradle 8.x
- AWS ElastiCache Redis インスタンス
- AWS Elastic Beanstalk環境（本番デプロイ用）

## プロジェクト構成

```
redis-elasticache-poc/
├── src/main/java/com/example/redispoc/
│   ├── RedisElastiCachePocApplication.java  # メインアプリケーション
│   ├── config/
│   │   └── RedisConfig.java                  # Redis設定
│   ├── controller/
│   │   └── CacheController.java              # REST APIエンドポイント
│   ├── service/
│   │   └── CacheService.java                 # キャッシュ操作サービス
│   └── model/
│       ├── CacheRequest.java                 # リクエストDTO
│       └── CacheResponse.java                # レスポンスDTO
└── src/main/resources/
    └── application.yml                        # アプリケーション設定
```

## セットアップ

### 1. ElastiCache接続情報の設定

`src/main/resources/application.yml`を編集して、ElastiCacheのエンドポイントを設定します：

```yaml
spring:
  data:
    redis:
      host: your-cluster.xxxxx.ng.0001.apne1.cache.amazonaws.com
      port: 6379
```

または、環境変数で設定：

```bash
export REDIS_HOST=your-cluster.xxxxx.ng.0001.apne1.cache.amazonaws.com
export REDIS_PORT=6379
```

### 2. ネットワーク設定

ElastiCacheはVPC内のプライベートリソースなので、以下を確認してください：

- **EC2/ECS上で実行する場合**: ElastiCacheと同じVPC内に配置
- **セキュリティグループ**: ElastiCacheのセキュリティグループでポート6379へのインバウンドを許可

### 3. アプリケーションのビルドと起動

```bash
# ビルド
./gradlew build

# 実行
./gradlew bootRun
```

Windowsの場合：
```cmd
gradlew.bat build
gradlew.bat bootRun
```

アプリケーションは`http://localhost:8080`で起動します。

## API エンドポイント

### 1. ヘルスチェック

Redis接続の確認：

```bash
curl http://localhost:8080/api/cache/health
```

### 2. データの保存

```bash
# 無期限で保存
curl -X POST http://localhost:8080/api/cache \
  -H "Content-Type: application/json" \
  -d '{
    "key": "user:1001",
    "value": "John Doe"
  }'

# 有効期限付き（300秒）で保存
curl -X POST http://localhost:8080/api/cache \
  -H "Content-Type: application/json" \
  -d '{
    "key": "session:abc123",
    "value": "session-data-here",
    "ttlSeconds": 300
  }'
```

### 3. データの取得

```bash
curl http://localhost:8080/api/cache/user:1001
```

### 4. データの削除

```bash
curl -X DELETE http://localhost:8080/api/cache/user:1001
```

### 5. キーの存在確認

```bash
curl http://localhost:8080/api/cache/user:1001/exists
```

### 6. 残り有効期限の確認

```bash
curl http://localhost:8080/api/cache/session:abc123/ttl
```

## レスポンス例

### 成功時

```json
{
  "success": true,
  "message": "Data saved successfully",
  "data": null
}
```

### データ取得時

```json
{
  "success": true,
  "message": "Data found",
  "data": "John Doe"
}
```

### エラー時

```json
{
  "success": false,
  "message": "Error: Connection refused",
  "data": null
}
```

## トラブルシューティング

### 接続できない場合

1. **セキュリティグループの確認**
   - ElastiCacheのセキュリティグループでポート6379が開いているか
   - アプリケーションのソースからのアクセスが許可されているか

2. **VPCの確認**
   - アプリケーションとElastiCacheが同じVPC内にあるか

3. **エンドポイントの確認**
   - `application.yml`のホスト名が正しいか
   - プライマリエンドポイントを使用しているか

### ログの確認

アプリケーションログで詳細を確認：

```bash
# application.ymlでDEBUGレベルに設定済み
tail -f logs/spring.log
```

## クラスターモードの場合

クラスターモードを使用する場合は、`application.yml`を以下のように変更：

```yaml
spring:
  data:
    redis:
      cluster:
        nodes:
          - node1.cache.amazonaws.com:6379
          - node2.cache.amazonaws.com:6379
          - node3.cache.amazonaws.com:6379
```

## 本番環境への移行

このPoCを本番環境で使用する場合、以下を追加してください：

1. **認証の有効化**（ElastiCacheで認証を有効にしている場合）
2. **接続プールの最適化**
3. **エラーハンドリングの強化**
4. **メトリクスとモニタリング**
5. **SSL/TLS接続の有効化**（ElastiCacheで有効な場合）

## 💰 コスト見積もり

このプロジェクトで使用したAWSリソースのコスト：

| リソース | タイプ | 料金/時間 | 料金/月 |
|---------|-------|----------|---------|
| ElastiCache | cache.t3.micro | $0.028 | 約3,000円 |
| Beanstalk EC2 | t3.micro | $0.0104 | 約1,100円 |
| **合計** | - | **$0.0384** | **約4,100円** |

⚠️ **使用後は必ずリソースを削除してください！** 削除手順は[DEPLOYMENT_SUMMARY.md](DEPLOYMENT_SUMMARY.md#-リソース削除手順)を参照。

## 📊 技術スタック

- **言語**: Java 17
- **フレームワーク**: Spring Boot 3.2.5
- **キャッシュライブラリ**: Spring Data Redis, Jedis 5.1.0
- **ビルドツール**: Gradle 8.x
- **AWS サービス**:
  - ElastiCache for Redis OSS (cache.t3.micro)
  - Elastic Beanstalk (Corretto 17)
  - VPC, Security Groups, Subnets

## 🔗 関連リンク

- [Spring Data Redis 公式ドキュメント](https://spring.io/projects/spring-data-redis)
- [AWS ElastiCache for Redis](https://docs.aws.amazon.com/ja_jp/AmazonElastiCache/latest/red-ug/)
- [AWS Elastic Beanstalk](https://docs.aws.amazon.com/ja_jp/elasticbeanstalk/)

## ライセンス

MIT
