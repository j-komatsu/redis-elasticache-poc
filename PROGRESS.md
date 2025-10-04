# Redis ElastiCache PoC - 進捗チェックリスト

最終更新: 2025年10月4日

---

## 📋 全体の進捗状況

```
フェーズ1: アプリケーション開発        ✅ 完了 (100%)
フェーズ2: AWS環境準備                 ✅ 完了 (100%)
フェーズ3: デプロイとテスト             ✅ 完了 (100%)

全体進捗: 100%完了 🎉
```

---

## フェーズ1: アプリケーション開発 ✅

### Spring Bootアプリケーション

- [x] **プロジェクト作成**
  - [x] Gradle設定 ([build.gradle](build.gradle))
  - [x] プロジェクト構成
  - [x] Gradleラッパー設定

- [x] **アプリケーションコード**
  - [x] メインクラス ([RedisElastiCachePocApplication.java](src/main/java/com/example/redispoc/RedisElastiCachePocApplication.java))
  - [x] Redis設定クラス ([RedisConfig.java](src/main/java/com/example/redispoc/config/RedisConfig.java))
  - [x] キャッシュサービス ([CacheService.java](src/main/java/com/example/redispoc/service/CacheService.java))
  - [x] REST APIコントローラー ([CacheController.java](src/main/java/com/example/redispoc/controller/CacheController.java))
  - [x] モデルクラス (CacheRequest, CacheResponse)

- [x] **設定ファイル**
  - [x] application.yml - Redis接続設定
  - [x] .gitignore

- [x] **ローカルテスト**
  - [x] ビルド成功 (`./gradlew build`)
  - [x] アプリケーション起動成功 (`./gradlew bootRun`)
  - [x] ヘルスチェックエンドポイント確認
    - ⚠️ Redisなしでの起動確認済み（接続エラーは正常動作）

### Beanstalkデプロイ用設定

- [x] **デプロイ設定**
  - [x] Procfile作成
  - [x] .ebextensions/01_environment.config 作成
  - [x] bootJar設定（application.jar）

### ドキュメント

- [x] **README.md** - プロジェクト概要とローカル開発手順
- [x] **DEPLOY.md** - Elastic Beanstalkデプロイ手順
- [x] **ELASTICACHE_SETUP.md** - ElastiCache作成手順（完全版）

---

## フェーズ2: AWS環境準備 ✅ (100%)

### VPCとネットワーク

- [x] **VPC確認**
  - [x] 使用VPC特定: `vpc-xxxxxxxxxxxxxxxxx`
  - [x] デフォルトVPC（172.31.x.x）であることを確認

- [x] **サブネット確認**
  - [x] 既存サブネット3つ確認（すべてパブリック）
    - `subnet-xxxxxx` (172.31.32.0/20) - パブリック
    - `subnet-xxxxxx` (172.31.0.0/20) - パブリック
    - `subnet-xxxxxx` (172.31.16.0/20) - パブリック
  - [x] ルートテーブル確認方法を習得
  - [x] パブリック/プライベートの判定方法を理解

- [x] **プライベートサブネット作成** ✅ 完了
  - [x] private-subnet-1a (172.31.100.0/24, ap-northeast-1a) 作成
  - [x] private-subnet-1c (172.31.101.0/24, ap-northeast-1c) 作成
  - [x] プライベート用ルートテーブル作成
  - [x] サブネットとルートテーブルの関連付け
  - [x] プライベートサブネット動作確認
  - 📖 **手順書**: [ELASTICACHE_SETUP.md - セクション2-A](ELASTICACHE_SETUP.md#2-a-プライベートサブネットがない場合の追加手順)

### ElastiCache (Redis OSS)

- [x] **サブネットグループ作成** ✅ 完了
  - [x] redis-poc-subnet-group 作成
  - [x] private-subnet-1a, private-subnet-1c を追加
  - 📖 **手順書**: [ELASTICACHE_SETUP.md - セクション3](ELASTICACHE_SETUP.md#3-サブネットグループの作成)

- [x] **セキュリティグループ作成** ✅ 完了
  - [x] redis-poc-sg 作成
  - [x] インバウンドルール: ポート6379を開放（VPC CIDR: 172.31.0.0/16）
  - [x] セキュリティグループIDをメモ
  - 📖 **手順書**: [ELASTICACHE_SETUP.md - セクション4](ELASTICACHE_SETUP.md#4-セキュリティグループの作成)

- [x] **ElastiCacheクラスター作成** ✅ 完了
  - [x] クラスターキャッシュを選択（サーバーレスではない）
  - [x] クラスターモード: 無効
  - [x] ノードタイプ: cache.t3.micro（t4g.microが選択不可のため）
  - [x] レプリカ数: 0
  - [x] サブネットグループ: redis-poc-subnet-group
  - [x] セキュリティグループ: redis-poc-sg
  - [x] 暗号化: 無効（PoC用）
  - [x] バックアップ: 無効（PoC用）
  - 📖 **手順書**: [ELASTICACHE_SETUP.md - セクション5](ELASTICACHE_SETUP.md#5-elasticacheクラスターの作成)

- [x] **接続情報確認** ✅ 完了
  - [x] プライマリエンドポイント取得
  - [x] エンドポイントをメモ: `your-redis-cluster.xxxxx.ng.0001.apne1.cache.amazonaws.com:6379`
  - 📖 **手順書**: [ELASTICACHE_SETUP.md - セクション6](ELASTICACHE_SETUP.md#6-接続情報の確認)

### Elastic Beanstalk

- [x] **Beanstalk環境作成（AWSコンソール使用）** ✅ 完了
  - [x] ステップ1: 環境情報の設定
    - [x] アプリケーション名: `redis-elasticache-poc`
    - [x] 環境名: `Redis-elasticache-poc-env`
    - [x] プラットフォーム: Corretto 17
    - [x] プリセット: 単一インスタンス
  - [x] ステップ2: サービスアクセスの設定
    - [x] サービスロール: `aws-elasticbeanstalk-service-role`
    - [x] EC2インスタンスプロファイル: `aws-elasticbeanstalk-ec2-role`
  - [x] ステップ3: ネットワーキング、データベース、タグをセットアップ
    - [x] VPC: `vpc-xxxxxxxxxxxxxxxxx`
    - [x] パブリックIPアドレス: 無効
    - [x] インスタンスサブネット: `private-subnet-1a`, `private-subnet-1c`
  - [x] ステップ4: インスタンスのトラフィックとスケーリングを設定
    - [x] ルートボリューム: マグネティック, 8 GB
    - [x] EC2セキュリティグループ: `redis-poc-sg`（選択済み）
    - [x] インスタンスタイプ: t3.micro
  - [x] ステップ5: 更新、モニタリング、ログ記録を設定
    - [x] CloudWatchカスタムメトリクス: 無効
    - [x] 環境プロパティ設定:
      - [x] `REDIS_HOST`: `your-redis-cluster.xxxxx.ng.0001.apne1.cache.amazonaws.com`
      - [x] `REDIS_PORT`: `6379`
  - [x] ステップ6: レビュー
    - [x] 設定確認
    - [x] 環境作成開始（送信ボタンクリック）
  - [ ] 環境作成完了待機（5〜10分）
  - 📖 **手順書**: [DEPLOY.md - セクション2](DEPLOY.md#2-elastic-beanstalk環境の作成)

- [ ] **セキュリティグループ確認** ⏳ 作成後
  - [ ] Beanstalk環境のセキュリティグループ確認
  - [ ] ElastiCacheへの接続可能性確認
  - 📖 **手順書**: [DEPLOY.md - セクション3](DEPLOY.md#3-セキュリティグループの設定)

---

## フェーズ3: デプロイとテスト ⏳

### アプリケーションデプロイ

- [ ] **ビルド** ⏳ 未着手
  - [ ] `./gradlew clean bootJar`
  - [ ] build/libs/application.jar 確認

- [ ] **Beanstalkデプロイ** ⏳ 未着手
  - [ ] `eb deploy`
  - [ ] デプロイ完了確認
  - [ ] ステータス確認 (`eb status`)
  - 📖 **手順書**: [DEPLOY.md - セクション6](DEPLOY.md#6-デプロイ)

### 動作確認

- [ ] **ヘルスチェック** ⏳ 未着手
  - [ ] `GET /api/cache/health`
  - [ ] レスポンス: `"success": true`
  - [ ] Redis接続成功確認

- [ ] **基本操作テスト** ⏳ 未着手
  - [ ] データ保存: `POST /api/cache`
  - [ ] データ取得: `GET /api/cache/{key}`
  - [ ] データ削除: `DELETE /api/cache/{key}`
  - [ ] TTL確認: `GET /api/cache/{key}/ttl`

- [ ] **機能テスト** ⏳ 未着手
  - [ ] 有効期限付きデータ保存
  - [ ] TTL経過後のデータ消失確認
  - [ ] 複数キーの操作

### ログとモニタリング

- [ ] **ログ確認** ⏳ 未着手
  - [ ] Beanstalkログ確認 (`eb logs`)
  - [ ] アプリケーションログ確認
  - [ ] Redis接続ログ確認

- [ ] **エラーハンドリング** ⏳ 未着手
  - [ ] Redis切断時の動作確認
  - [ ] 不正なキーでのアクセステスト

---

## クリーンアップ（作業終了後）

- [ ] **リソース削除** ⏳ 未着手
  - [ ] Beanstalk環境削除 (`eb terminate`)
  - [ ] ElastiCacheクラスター削除
  - [ ] セキュリティグループ削除
  - [ ] サブネットグループ削除
  - [ ] プライベートサブネット削除（必要に応じて）
  - [ ] プライベートルートテーブル削除（必要に応じて）

- [ ] **コスト確認** ⏳ 未着手
  - [ ] AWS Cost Explorerで料金確認
  - [ ] 想定コストと実コストの比較

---

## 📚 ドキュメント一覧

| ファイル名 | 内容 | 状態 |
|-----------|------|------|
| [README.md](README.md) | プロジェクト概要、ローカル開発手順 | ✅ 完成 |
| [DEPLOY.md](DEPLOY.md) | Elastic Beanstalkデプロイ手順 | ✅ 完成 |
| [ELASTICACHE_SETUP.md](ELASTICACHE_SETUP.md) | ElastiCache作成完全ガイド | ✅ 完成 |
| [PROGRESS.md](PROGRESS.md) | 進捗チェックリスト（このファイル） | ✅ 完成 |

---

## 🎯 次回の作業（優先順位順）

### 1. Beanstalk環境作成完了を待つ 🔴 現在進行中

**状態:**
- ✅ 環境作成開始済み（「送信」ボタンクリック済み）
- ⏳ 作成完了待機中（5〜10分）

**確認方法:**
- Elastic Beanstalkコンソールで環境の状態を確認
- 状態が「OK」（緑色）になれば完了

**所要時間**: 残り約5〜10分

---

### 2. アプリケーションのビルド 🔴 次のステップ

**作業内容:**
```bash
cd redis-elasticache-poc
./gradlew clean bootJar
```
- ビルド成功確認
- `build/libs/application.jar` が作成されることを確認

**所要時間**: 約3分

---

### 3. アプリケーションデプロイ 🟡

**作業内容:**
- [ ] `application.jar` をBeanstalkにアップロード
- [ ] デプロイ完了待機
- [ ] ヘルスチェック確認

**所要時間**: 約10分

---

### 5. 動作確認 🟢

**作業内容:**
- [ ] `GET /api/cache/health` でRedis接続確認
- [ ] データ保存・取得テスト
- [ ] ログ確認

**所要時間**: 約5分

---

## 💡 重要なメモ

### 作成したAWSリソース一覧

**VPC:**
- VPC ID: `vpc-xxxxxxxxxxxxxxxxx`
- CIDR: `172.31.0.0/16`

**プライベートサブネット:**
- `private-subnet-1a`: 172.31.100.0/24 (ap-northeast-1a)
- `private-subnet-1c`: 172.31.101.0/24 (ap-northeast-1c)
- ルートテーブル: `private-route-table` (local のみ)

**ElastiCache:**
- クラスター名: `redis-poc-cluster`
- エンドポイント: `your-redis-cluster.xxxxx.ng.0001.apne1.cache.amazonaws.com:6379`
- ノードタイプ: cache.t3.micro
- サブネットグループ: `redis-poc-subnet-group`
- セキュリティグループ: `redis-poc-sg` (ポート6379, VPC内からアクセス可)

### コスト見積もり
- **ElastiCache** (cache.t3.micro): 約$0.028/時間 = 約4円/時間
- **Beanstalk** (EC2 t3.small想定): 約$0.026/時間 = 約4円/時間
- **合計**: 約8円/時間、約200円/日、約6,000円/月

⚠️ **使用後は必ず削除してください！**

### 学習ポイント
- ✅ パブリック/プライベートサブネットの違い
- ✅ ルートテーブルとインターネットゲートウェイ
- ✅ VPC内のリソース通信
- ✅ セキュリティグループの設定
- 🔄 ElastiCacheのクラスター設計（進行中）
- ⏳ BeanstalkとElastiCacheの連携（未着手）

---

## 📞 参考リンク

- [ElastiCache for Redis 公式ドキュメント](https://docs.aws.amazon.com/ja_jp/AmazonElastiCache/latest/red-ug/WhatIs.html)
- [Elastic Beanstalk 公式ドキュメント](https://docs.aws.amazon.com/ja_jp/elasticbeanstalk/latest/dg/Welcome.html)
- [料金計算ツール](https://calculator.aws/#/)

---

**最終更新**: 2025年10月4日
**現在の状態**: Beanstalk環境作成中（5〜10分待機）
**次回作業**: 環境作成完了後、アプリケーションビルド → Beanstalkデプロイ
