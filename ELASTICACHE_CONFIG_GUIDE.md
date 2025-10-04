# ElastiCache 構成ガイド

ElastiCacheを作成する際の構成パターン、必須項目、選択肢を図解で説明します。

---

## 📋 目次

1. [作成時の必須項目](#作成時の必須項目)
2. [クラスター構成パターン](#クラスター構成パターン)
3. [ノードタイプの選び方](#ノードタイプの選び方)
4. [ネットワーク設定](#ネットワーク設定)
5. [セキュリティ設定](#セキュリティ設定)
6. [バックアップと可用性](#バックアップと可用性)
7. [コスト比較](#コスト比較)
8. [用途別推奨構成](#用途別推奨構成)

---

## 作成時の必須項目

### 必須項目チェックリスト

```mermaid
graph TB
    Start[ElastiCache作成開始]

    Start --> Choice1{デプロイオプション}
    Choice1 -->|選択1| Serverless[サーバーレス]
    Choice1 -->|選択2| Cluster[クラスターキャッシュ]

    Cluster --> Choice2{クラスターモード}
    Choice2 -->|有効| ClusterMode[クラスターモード有効]
    Choice2 -->|無効| SingleMode[クラスターモード無効]

    SingleMode --> Required[必須設定項目]

    Required --> Name[クラスター名]
    Required --> Engine[エンジンバージョン]
    Required --> Node[ノードタイプ]
    Required --> Network[ネットワーク設定]
    Required --> Security[セキュリティグループ]

    style Required fill:#f9f,stroke:#333,stroke-width:2px
    style Name fill:#bfb,stroke:#333
    style Engine fill:#bfb,stroke:#333
    style Node fill:#bfb,stroke:#333
    style Network fill:#bfb,stroke:#333
    style Security fill:#bfb,stroke:#333
```

### 必須項目の詳細

| 項目 | 必須/任意 | 説明 | 例 |
|------|----------|------|-----|
| **クラスター名** | ✅ 必須 | 一意の識別名 | `redis-poc-cluster` |
| **エンジンバージョン** | ✅ 必須 | Redis OSS バージョン | `7.1` |
| **ノードタイプ** | ✅ 必須 | インスタンスサイズ | `cache.t3.micro` |
| **レプリカ数** | ⚪ 任意 | 0〜5個 | `0`（PoC）、`1`（本番） |
| **サブネットグループ** | ✅ 必須 | VPC配置先 | `redis-poc-subnet-group` |
| **セキュリティグループ** | ✅ 必須 | アクセス制御 | `redis-poc-sg` |
| **ポート** | ⚪ 任意 | デフォルト6379 | `6379` |
| **暗号化** | ⚪ 任意 | at-rest/in-transit | 無効（PoC）、有効（本番） |
| **バックアップ** | ⚪ 任意 | 自動バックアップ | 無効（PoC）、有効（本番） |

---

## クラスター構成パターン

### パターン1: 単一ノード（最小構成）

**用途:** PoC、開発環境、キャッシュのみ

```mermaid
graph LR
    App[アプリケーション]

    subgraph Cluster["ElastiCache クラスター"]
        Primary[プライマリノード<br/>cache.t3.micro<br/>メモリ: 0.5GB]
    end

    App -->|読み書き| Primary

    style Primary fill:#f96,stroke:#333,stroke-width:2px
```

**特徴:**
- ✅ 最も安い（約4円/時間）
- ✅ シンプル
- ❌ 障害時にダウン
- ❌ バックアップなし

**設定:**
```yaml
ノードタイプ: cache.t3.micro
レプリカ数: 0
マルチAZ: 無効
```

---

### パターン2: レプリカ付き（高可用性）

**用途:** 本番環境、高可用性が必要

```mermaid
graph LR
    App[アプリケーション]

    subgraph Cluster["ElastiCache クラスター"]
        subgraph AZ1["AZ: ap-northeast-1a"]
            Primary[プライマリノード<br/>読み書き]
        end

        subgraph AZ2["AZ: ap-northeast-1c"]
            Replica[レプリカノード<br/>読み取り専用]
        end

        Primary -.->|レプリケーション| Replica
    end

    App -->|書き込み| Primary
    App -->|読み取り| Primary
    App -->|読み取り| Replica

    style Primary fill:#f96,stroke:#333,stroke-width:2px
    style Replica fill:#9cf,stroke:#333,stroke-width:2px
```

**特徴:**
- ✅ 高可用性（プライマリ障害時にレプリカが昇格）
- ✅ 読み取り性能向上（負荷分散）
- ✅ マルチAZ対応
- ❌ コスト2倍

**設定:**
```yaml
ノードタイプ: cache.t3.micro
レプリカ数: 1
マルチAZ: 有効
自動フェイルオーバー: 有効
```

**動作:**
1. 通常時：プライマリで書き込み、レプリカで読み取り
2. 障害時：レプリカが自動的にプライマリに昇格

---

### パターン3: クラスターモード（スケーラブル）

**用途:** 大規模データ、高スループット

```mermaid
graph TB
    App[アプリケーション]

    subgraph Cluster["ElastiCache クラスターモード有効"]
        subgraph Shard1["シャード1<br/>キー範囲: 0-5461"]
            S1P[プライマリ]
            S1R[レプリカ]
            S1P -.-> S1R
        end

        subgraph Shard2["シャード2<br/>キー範囲: 5462-10922"]
            S2P[プライマリ]
            S2R[レプリカ]
            S2P -.-> S2R
        end

        subgraph Shard3["シャード3<br/>キー範囲: 10923-16383"]
            S3P[プライマリ]
            S3R[レプリカ]
            S3P -.-> S3R
        end
    end

    App -->|キーのハッシュ値で<br/>自動振り分け| Shard1
    App --> Shard2
    App --> Shard3

    style S1P fill:#f96,stroke:#333,stroke-width:2px
    style S2P fill:#f96,stroke:#333,stroke-width:2px
    style S3P fill:#f96,stroke:#333,stroke-width:2px
    style S1R fill:#9cf,stroke:#333
    style S2R fill:#9cf,stroke:#333
    style S3R fill:#9cf,stroke:#333
```

**特徴:**
- ✅ 大量データ対応（データ分散）
- ✅ 高スループット
- ✅ 水平スケーリング可能
- ❌ 複雑な設定
- ❌ コスト高（シャード数 × ノード数）

**設定:**
```yaml
クラスターモード: 有効
シャード数: 3
レプリカ/シャード: 1
ノードタイプ: cache.r6g.large
```

**データ分散の仕組み:**
```
キー "user:1001" → ハッシュ値 3456 → シャード1
キー "user:2002" → ハッシュ値 8901 → シャード2
キー "user:3003" → ハッシュ値 15000 → シャード3
```

---

## ノードタイプの選び方

### ノードタイプ一覧

```mermaid
graph TB
    Start[ノードタイプ選択]

    Start --> Q1{用途は？}

    Q1 -->|PoC/開発| T[Tファミリー<br/>汎用・低コスト]
    Q1 -->|本番| Q2{メモリ重視?}

    Q2 -->|はい| R[Rファミリー<br/>メモリ最適化]
    Q2 -->|いいえ| M[Mファミリー<br/>バランス型]

    T --> T3[cache.t3.micro<br/>0.5GB / $0.028/h]
    T --> T4[cache.t4g.micro<br/>0.5GB / $0.018/h]

    R --> R6[cache.r6g.large<br/>13GB / $0.189/h]

    M --> M6[cache.m6g.large<br/>6.4GB / $0.136/h]

    style T3 fill:#bfb,stroke:#333
    style T4 fill:#bfb,stroke:#333
    style R6 fill:#fbb,stroke:#333
    style M6 fill:#fbf,stroke:#333
```

### ファミリー別比較

| ファミリー | 特徴 | 用途 | 例 |
|-----------|------|------|-----|
| **T（汎用）** | 低コスト、バースト可能 | PoC、開発、小規模 | `cache.t3.micro` |
| **T4g（汎用 Graviton2）** | T3より20%安い | PoC、開発、コスト重視 | `cache.t4g.micro` |
| **M（バランス）** | CPU/メモリバランス | 中規模本番 | `cache.m6g.large` |
| **R（メモリ最適化）** | 大容量メモリ | 大規模データ | `cache.r6g.large` |

### サイズ別比較（Tファミリー）

| ノードタイプ | メモリ | vCPU | 料金/時間 | 料金/月 | 用途 |
|-------------|-------|------|----------|---------|------|
| **cache.t3.micro** | 0.5 GB | 2 | $0.028 | 約3,000円 | PoC |
| **cache.t3.small** | 1.37 GB | 2 | $0.055 | 約6,000円 | 小規模 |
| **cache.t3.medium** | 3.09 GB | 2 | $0.111 | 約12,000円 | 中規模 |

### 選び方フローチャート

```mermaid
graph TB
    Start[どのサイズ?]

    Start --> Q1{データ量は?}

    Q1 -->|100MB未満| Micro[cache.t3.micro<br/>0.5GB]
    Q1 -->|100MB〜500MB| Small[cache.t3.small<br/>1.37GB]
    Q1 -->|500MB〜2GB| Medium[cache.t3.medium<br/>3.09GB]
    Q1 -->|2GB以上| Large[cache.r6g.large<br/>13GB]

    style Micro fill:#bfb,stroke:#333,stroke-width:2px
```

---

## ネットワーク設定

### VPCとサブネット構成

```mermaid
graph TB
    subgraph VPC["VPC: 172.31.0.0/16"]
        subgraph Public["パブリックサブネット"]
            EC2[EC2/Beanstalk<br/>アプリケーション]
        end

        subgraph Private["プライベートサブネット"]
            subgraph AZ1["AZ: 1a"]
                Redis1[ElastiCache<br/>プライマリ]
            end

            subgraph AZ2["AZ: 1c"]
                Redis2[ElastiCache<br/>レプリカ]
            end
        end

        IGW[Internet Gateway]
    end

    Internet[インターネット] --> IGW
    IGW --> EC2
    EC2 -.->|プライベート通信| Redis1
    EC2 -.->|プライベート通信| Redis2

    style Redis1 fill:#fbb,stroke:#333,stroke-width:2px
    style Redis2 fill:#fbb,stroke:#333
    style EC2 fill:#bbf,stroke:#333,stroke-width:2px
```

### 必須ネットワーク設定

#### 1. サブネットグループ

**必須:** 最低2つのAZにサブネットが必要

```yaml
サブネットグループ名: redis-poc-subnet-group
VPC: vpc-xxxxxxxxxxxxxxxxx
サブネット:
  - private-subnet-1a (AZ: ap-northeast-1a)
  - private-subnet-1c (AZ: ap-northeast-1c)
```

**理由:** マルチAZ対応のため

#### 2. セキュリティグループ

**必須:** ポート6379の通信許可

```yaml
セキュリティグループ名: redis-poc-sg
インバウンドルール:
  - タイプ: カスタムTCP
  - ポート: 6379
  - ソース: 172.31.0.0/16（VPC CIDR）または アプリのSG
  - 説明: Allow from VPC
```

---

## セキュリティ設定

### セキュリティオプション

```mermaid
graph TB
    Security[セキュリティ設定]

    Security --> SG[セキュリティグループ<br/>✅ 必須]
    Security --> Auth[認証<br/>⚪ 任意]
    Security --> Enc1[保存時の暗号化<br/>⚪ 任意]
    Security --> Enc2[転送時の暗号化<br/>⚪ 任意]

    SG --> SG_Detail[ポート6379<br/>アクセス元制限]
    Auth --> Auth_Detail[Redis AUTH<br/>パスワード認証]
    Enc1 --> Enc1_Detail[at-rest encryption<br/>ディスク暗号化]
    Enc2 --> Enc2_Detail[in-transit encryption<br/>TLS/SSL]

    style SG fill:#f96,stroke:#333,stroke-width:2px
    style Auth fill:#9cf,stroke:#333
    style Enc1 fill:#9cf,stroke:#333
    style Enc2 fill:#9cf,stroke:#333
```

### 推奨設定

| 設定 | PoC | 本番 | 説明 |
|------|-----|------|------|
| **セキュリティグループ** | ✅ 有効 | ✅ 有効 | 必須 |
| **認証（AUTH）** | ❌ 無効 | ✅ 有効 | パスワード認証 |
| **保存時の暗号化** | ❌ 無効 | ✅ 有効 | データ暗号化 |
| **転送時の暗号化** | ❌ 無効 | ✅ 有効 | TLS通信 |

---

## バックアップと可用性

### バックアップオプション

```mermaid
graph LR
    Backup[バックアップ設定]

    Backup --> Auto[自動バックアップ<br/>⚪ 任意]
    Backup --> Manual[手動スナップショット<br/>⚪ 任意]

    Auto --> Retention[保持期間: 1-35日]
    Auto --> Window[バックアップ時間帯]

    Manual --> OnDemand[任意のタイミング]

    style Auto fill:#9cf,stroke:#333
    style Manual fill:#9cf,stroke:#333
```

### 設定比較

| 項目 | PoC | 本番 |
|------|-----|------|
| **自動バックアップ** | ❌ 無効 | ✅ 有効 |
| **保持期間** | - | 7日 |
| **バックアップウィンドウ** | - | 深夜3-4時 |
| **マルチAZ** | ❌ 無効 | ✅ 有効 |
| **自動フェイルオーバー** | ❌ 無効 | ✅ 有効 |

---

## コスト比較

### 構成別の月額コスト（概算）

```mermaid
graph LR
    subgraph Pattern1["パターン1: 単一ノード"]
        P1[cache.t3.micro × 1<br/>約3,000円/月]
    end

    subgraph Pattern2["パターン2: レプリカ付き"]
        P2[cache.t3.micro × 2<br/>約6,000円/月]
    end

    subgraph Pattern3["パターン3: クラスターモード"]
        P3[cache.r6g.large × 6<br/>約80,000円/月]
    end

    style P1 fill:#bfb,stroke:#333,stroke-width:2px
    style P2 fill:#fbf,stroke:#333
    style P3 fill:#fbb,stroke:#333
```

### 詳細コスト表

| 構成 | ノード数 | ノードタイプ | 時間単価 | 月額 |
|------|----------|-------------|----------|------|
| **最小構成** | 1 | cache.t3.micro | $0.028 | 約3,000円 |
| **PoC + レプリカ** | 2 | cache.t3.micro | $0.056 | 約6,000円 |
| **本番・小規模** | 2 | cache.m6g.large | $0.272 | 約29,000円 |
| **本番・大規模** | 6 | cache.r6g.large | $1.134 | 約122,000円 |

**注意:**
- 上記はインスタンス料金のみ
- データ転送料、バックアップ保存料は別途

---

## 用途別推奨構成

### 1. PoC・検証用（本プロジェクト）

```yaml
デプロイオプション: クラスターキャッシュ
クラスターモード: 無効
ノードタイプ: cache.t3.micro
レプリカ数: 0
マルチAZ: 無効
暗号化: 無効
バックアップ: 無効
コスト: 約3,000円/月
```

**特徴:**
- ✅ 最小コスト
- ✅ シンプル
- ❌ 本番不可

---

### 2. 開発環境

```yaml
デプロイオプション: クラスターキャッシュ
クラスターモード: 無効
ノードタイプ: cache.t3.small
レプリカ数: 0
マルチAZ: 無効
暗号化: 無効
バックアップ: 有効（保持1日）
コスト: 約6,000円/月
```

**特徴:**
- ✅ 低コスト
- ✅ バックアップあり
- ⚪ 可用性は低い

---

### 3. 本番環境・小規模

```yaml
デプロイオプション: クラスターキャッシュ
クラスターモード: 無効
ノードタイプ: cache.m6g.large
レプリカ数: 1
マルチAZ: 有効
自動フェイルオーバー: 有効
暗号化（at-rest）: 有効
暗号化（in-transit）: 有効
認証: 有効
バックアップ: 有効（保持7日）
コスト: 約29,000円/月
```

**特徴:**
- ✅ 高可用性
- ✅ セキュア
- ✅ 自動フェイルオーバー

---

### 4. 本番環境・大規模

```yaml
デプロイオプション: クラスターキャッシュ
クラスターモード: 有効
シャード数: 3
レプリカ/シャード: 1
ノードタイプ: cache.r6g.large
マルチAZ: 有効
暗号化（at-rest）: 有効
暗号化（in-transit）: 有効
認証: 有効
バックアップ: 有効（保持14日）
コスト: 約122,000円/月
```

**特徴:**
- ✅ 超高可用性
- ✅ スケーラブル
- ✅ 大量データ対応
- ❌ 高コスト

---

## 作成フロー全体図

```mermaid
graph TB
    Start[ElastiCache作成開始]

    Start --> Step1[1. デプロイオプション選択]
    Step1 --> Step2[2. クラスターモード選択]
    Step2 --> Step3[3. クラスター名入力]
    Step3 --> Step4[4. エンジンバージョン選択]
    Step4 --> Step5[5. ノードタイプ選択]
    Step5 --> Step6[6. レプリカ数設定]
    Step6 --> Step7[7. サブネットグループ選択]
    Step7 --> Step8[8. セキュリティグループ選択]
    Step8 --> Step9[9. 暗号化設定]
    Step9 --> Step10[10. バックアップ設定]
    Step10 --> Step11[11. メンテナンスウィンドウ]
    Step11 --> Create[作成実行]

    Create --> Wait[作成待機<br/>5-10分]
    Wait --> Complete[完了]

    style Start fill:#9f9,stroke:#333,stroke-width:2px
    style Complete fill:#9f9,stroke:#333,stroke-width:2px
    style Create fill:#f96,stroke:#333,stroke-width:2px
```

---

## まとめ

### 用途別の推奨構成

| 用途 | 推奨パターン | コスト |
|------|-------------|--------|
| **学習・PoC** | パターン1（単一ノード） | 約3,000円/月 |
| **開発環境** | パターン1 + バックアップ | 約6,000円/月 |
| **本番環境** | パターン2（レプリカ付き） | 約29,000円/月〜 |

### チェックリスト

作成前に確認：

- [ ] 用途を明確にする（PoC/開発/本番）
- [ ] 予算を決める
- [ ] VPCとサブネットを準備
- [ ] セキュリティグループを作成
- [ ] ノードタイプを選択
- [ ] レプリカの要否を判断
- [ ] バックアップの要否を判断

---

**作成日:** 2025年10月4日
**関連ドキュメント:** [ELASTICACHE_SETUP.md](ELASTICACHE_SETUP.md)
