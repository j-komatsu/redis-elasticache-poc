# Elastic Beanstalk デプロイ手順

このアプリケーションをAWS Elastic Beanstalkにデプロイする手順です。

## 前提条件

- AWS CLIがインストール済み
- EB CLIがインストール済み（`pip install awsebcli`）
- Java 17以上
- Gradle

## 1. ElastiCache Redisの準備

### ElastiCacheインスタンスの作成

1. AWS ConsoleでElastiCache > Redis OSSに移動
2. **キャッシュの作成**をクリック
3. 設定:
   - **デザイン**: クラスターキャッシュ（推奨）またはサーバーレス
   - **名前**: `redis-poc-cluster`
   - **ノードタイプ**: cache.t4g.micro（デモ用）
   - **レプリカ数**: 0（デモ用）または1（本番用）
   - **サブネット**: Beanstalkと同じVPC内のプライベートサブネット
   - **セキュリティグループ**: 後で設定

4. **プライマリエンドポイント**をメモ
   - 例: `redis-poc-cluster.xxxxx.ng.0001.apne1.cache.amazonaws.com`

## 2. Elastic Beanstalk環境の作成

### 方法A: EB CLIを使用（推奨）

```bash
# プロジェクトディレクトリに移動
cd redis-elasticache-poc

# EB初期化
eb init -p corretto-17 redis-elasticache-poc --region ap-northeast-1

# 環境を作成（VPC設定含む）
eb create redis-poc-env --vpc

# VPC設定画面で:
# - ElastiCacheと同じVPCを選択
# - パブリックサブネットとプライベートサブネットを選択
# - ELB: パブリックサブネット
# - EC2: プライベートサブネット（ElastiCacheと同じ）
```

### 方法B: AWS Consoleを使用

#### ステップ1: 環境情報の設定
1. **Elastic Beanstalk**コンソールに移動
2. **環境を作成**をクリック
3. 設定:
   - **環境層**: ウェブサーバー環境
   - **アプリケーション名**: `redis-elasticache-poc`
   - **環境名**: `Redis-elasticache-poc-env`（自動生成でOK）
   - **プラットフォーム**: Managed platform - Corretto 17
   - **アプリケーションコード**: サンプルアプリケーション（後で置き換え）
   - **プリセット**: 単一インスタンス（コスト削減のため）

#### ステップ2: サービスアクセスの設定
- **サービスロール**:
  - 既存: `aws-elasticbeanstalk-service-role`
  - なければ「サービスロールを作成して使用」を選択
- **EC2キーペア**: 任意（SSHアクセスが必要な場合のみ）
- **EC2インスタンスプロファイル**:
  - 既存: `aws-elasticbeanstalk-ec2-role`
  - なければ自動作成される

#### ステップ3: ネットワーキング、データベース、タグをセットアップ
- **VPC**: ElastiCacheと同じVPCを選択（例: `vpc-xxxxxxxxxxxxxxxxx`）
- **パブリックIPアドレス**: 無効化（チェックを外す）
- **インスタンスサブネット**: プライベートサブネット2つを選択
  - 例: `private-subnet-1a`, `private-subnet-1c`
  - ※ElastiCacheと同じサブネットまたは同じVPC内のプライベートサブネット
- **データベース**: 設定しない（Redisを使用するため）

#### ステップ4: インスタンスのトラフィックとスケーリングを設定
- **ルートボリューム**:
  - タイプ: マグネティック（最もコスト効率的）
  - サイズ: 8 GB（最小値）
- **EC2セキュリティグループ**:
  - `redis-poc-sg`を選択（ElastiCacheへのアクセスのため）
  - または空欄のままにして後でセキュリティグループを追加
- **容量**:
  - 環境タイプ: 単一インスタンス
  - インスタンスタイプ: `t3.micro`（最小コスト）
  - アーキテクチャ: x86_64

#### ステップ5: 更新、モニタリング、ログ記録を設定
- **モニタリング**:
  - CloudWatchカスタムメトリクス: すべて無効（コスト削減）
- **マネージドプラットフォームの更新**:
  - マイナー更新: 有効（推奨）
- **環境プロパティ**（重要）:
  ```
  REDIS_HOST = redis-poc-cluster.xxxxx.ng.0001.apne1.cache.amazonaws.com
  REDIS_PORT = 6379
  ```
  - ソース: プレーンテキスト
  - ※実際のElastiCacheエンドポイントに置き換えてください

#### ステップ6: レビュー
- すべての設定を確認
- **送信**をクリック

**環境作成には5〜10分かかります**

## 3. セキュリティグループの設定

### ElastiCacheのセキュリティグループ

Beanstalk EC2からの接続を許可:

```
タイプ: カスタムTCP
ポート: 6379
ソース: <Beanstalk EC2のセキュリティグループID>
説明: Allow from Beanstalk
```

### Beanstalk EC2のセキュリティグループ

アウトバウンドでElastiCacheへのアクセスを許可（デフォルトで許可されているはず）

## 4. 環境変数の設定

### EB CLIを使用

```bash
eb setenv REDIS_HOST=redis-poc-cluster.xxxxx.ng.0001.apne1.cache.amazonaws.com
eb setenv REDIS_PORT=6379
```

### AWS Consoleを使用

1. Beanstalk環境を選択
2. **設定** > **ソフトウェア** > **編集**
3. **環境プロパティ**に追加:
   - `REDIS_HOST`: `redis-poc-cluster.xxxxx.ng.0001.apne1.cache.amazonaws.com`
   - `REDIS_PORT`: `6379`
4. **適用**

## 5. アプリケーションのビルド

```bash
# JARファイルをビルド
./gradlew clean bootJar

# ビルド確認
ls -lh build/libs/application.jar
```

Windowsの場合:
```cmd
gradlew.bat clean bootJar
dir build\libs\application.jar
```

## 6. デプロイ

### EB CLIを使用

```bash
# デプロイ
eb deploy

# ステータス確認
eb status

# URLを開く
eb open

# ログ確認
eb logs
```

### AWS Consoleを使用

1. JARファイルを準備:
   ```bash
   cp build/libs/application.jar .
   zip redis-poc-v1.zip application.jar Procfile .ebextensions/*
   ```

2. Beanstalk環境の**アップロードとデプロイ**からZIPファイルをアップロード

## 7. 動作確認

### ヘルスチェック

```bash
# EB CLIでURLを取得
eb status

# または環境のURLを使用
curl http://your-env.ap-northeast-1.elasticbeanstalk.com/api/cache/health
```

### データの保存と取得

```bash
# 保存
curl -X POST http://your-env.ap-northeast-1.elasticbeanstalk.com/api/cache \
  -H "Content-Type: application/json" \
  -d '{"key": "test", "value": "Hello Beanstalk!"}'

# 取得
curl http://your-env.ap-northeast-1.elasticbeanstalk.com/api/cache/test
```

## 8. トラブルシューティング

### 接続エラーが発生する場合

1. **セキュリティグループを確認**
   ```bash
   # Beanstalk EC2のSGを確認
   aws ec2 describe-security-groups --group-ids <beanstalk-sg-id>

   # ElastiCacheのSGでBeanstalk SGからのアクセスが許可されているか確認
   aws ec2 describe-security-groups --group-ids <elasticache-sg-id>
   ```

2. **VPCとサブネットを確認**
   - BeanstalkとElastiCacheが同じVPC内か
   - 同じサブネット、または通信可能なサブネット内か

3. **環境変数を確認**
   ```bash
   eb printenv
   ```

4. **ログを確認**
   ```bash
   eb logs
   # または
   eb ssh
   tail -f /var/log/web.stdout.log
   ```

### ログの詳細確認

```bash
# SSHで接続
eb ssh

# アプリケーションログ
sudo tail -f /var/log/web.stdout.log

# Beanstalkログ
sudo tail -f /var/log/eb-engine.log

# JVMプロセス確認
ps aux | grep java
```

## 9. スケーリング設定（オプション）

### オートスケーリングの設定

AWS Console > Beanstalk > 設定 > 容量:

- **環境タイプ**: 負荷分散
- **最小インスタンス**: 1
- **最大インスタンス**: 4
- **スケーリングトリガー**: CPU使用率 > 70%

## 10. コスト最適化

### デモ後の削除

```bash
# 環境を削除
eb terminate redis-poc-env

# ElastiCacheも削除
aws elasticache delete-cache-cluster --cache-cluster-id redis-poc-cluster
```

### 本番環境のコスト削減

1. **予約インスタンス**を検討
2. **スケジュールアクション**で夜間停止（開発環境のみ）
3. **Savings Plans**の活用

## デプロイフロー（まとめ）

```bash
# 1. ビルド
./gradlew clean bootJar

# 2. デプロイ
eb deploy

# 3. 確認
eb open
curl http://your-env.elasticbeanstalk.com/api/cache/health

# 4. ログ確認（問題がある場合）
eb logs
```

## 継続的デプロイ（CI/CD）

GitHub ActionsやCodePipelineと連携する場合は、`.github/workflows/deploy.yml`や`buildspec.yml`を追加してください。

詳細は別途ドキュメント化できます。
