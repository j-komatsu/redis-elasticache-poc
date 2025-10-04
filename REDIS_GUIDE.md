# Redis 利用ガイド

このドキュメントでは、Redisの基本的なルール、挙動、ベストプラクティスを説明します。

---

## 📚 目次

1. [Redisの基本概念](#redisの基本概念)
2. [データの保存と取得](#データの保存と取得)
3. [TTL（有効期限）](#ttl有効期限)
4. [重複とデータ上書き](#重複とデータ上書き)
5. [データの確認方法](#データの確認方法)
6. [データ型](#データ型)
7. [ベストプラクティス](#ベストプラクティス)
8. [注意点と制限](#注意点と制限)

---

## Redisの基本概念

### Key-Value ストア

Redisは**Key-Value型**のインメモリデータベースです。

```
Key（キー）    Value（値）
─────────────────────────
user:1001  →  "Alice"
session:abc → {"userId": 1001, "role": "admin"}
counter    →  42
```

**特徴:**
- すべてのデータはメモリ上に保存（高速）
- キーでデータを一意に識別
- シンプルで高速なデータアクセス

---

## データの保存と取得

### 基本操作

#### 保存（SET）

```bash
SET key value
```

**例:**
```bash
SET user:1001 "Alice"
→ OK
```

Spring Bootアプリケーション:
```java
cacheService.save("user:1001", "Alice");
```

#### 取得（GET）

```bash
GET key
```

**例:**
```bash
GET user:1001
→ "Alice"
```

Spring Bootアプリケーション:
```java
String value = cacheService.get("user:1001");
```

#### 削除（DEL）

```bash
DEL key
```

**例:**
```bash
DEL user:1001
→ 1  # 削除された個数
```

---

## TTL（有効期限）

### TTLとは

**TTL = Time To Live（生存時間）**

データが自動的に削除されるまでの残り時間（秒数）。

### TTL付きで保存

```bash
SETEX key seconds value
```

**例:**
```bash
SETEX session:abc 300 "session-data"
→ OK  # 300秒（5分）後に自動削除
```

Spring Bootアプリケーション:
```java
cacheService.saveWithExpiry("session:abc", "session-data", 300, TimeUnit.SECONDS);
```

### TTLの確認

```bash
TTL key
```

**返り値の意味:**
- **正の数**: 残り秒数（例: 245 → あと245秒で削除）
- **-1**: 有効期限なし（永続）
- **-2**: キーが存在しない

**例:**
```bash
TTL session:abc
→ 245  # あと245秒

TTL user:1001
→ -1   # 無期限

TTL not-exists
→ -2   # キーなし
```

### TTLの設定・更新

```bash
EXPIRE key seconds
```

**例:**
```bash
SET user:1001 "Alice"
EXPIRE user:1001 3600  # 1時間後に削除
```

---

## 重複とデータ上書き

### 同じキーで保存した場合

**結論: 上書きされる（エラーにならない）**

```bash
SET user:1001 "Alice"
→ OK

SET user:1001 "Bob"   # 上書き
→ OK

GET user:1001
→ "Bob"  # Aliceは消えてBobになる
```

**動作:**
- 同じキーで再度`SET`すると、古い値が新しい値に置き換わる
- 重複エラーは発生しない
- TTLもリセットされる

### 重複を防ぎたい場合

**SETNX（SET if Not eXists）を使用**

```bash
SETNX key value
```

**動作:**
- キーが**存在しない場合のみ**保存
- 既にキーが存在する場合は何もしない

**例:**
```bash
SETNX user:1001 "Alice"
→ 1  # 成功（新規作成）

SETNX user:1001 "Bob"
→ 0  # 失敗（既に存在）

GET user:1001
→ "Alice"  # 最初の値のまま
```

**返り値:**
- `1`: 保存成功
- `0`: 保存失敗（既に存在）

**Spring Bootでの実装例:**
```java
Boolean success = redisTemplate.opsForValue().setIfAbsent("user:1001", "Alice");
if (success) {
    // 保存成功
} else {
    // 既に存在
}
```

---

## データの確認方法

### AWS ElastiCacheでのデータ確認

**AWSコンソールからは直接確認できません。**

ElastiCacheのコンソールでは、以下のみ確認可能:
- メトリクス（CPU使用率、接続数など）
- クラスター状態
- エンドポイント情報

**実際のキー・値は見られない**

### データを確認する方法

#### 1. アプリケーション経由（推奨）

本プロジェクトのAPIを使用:

```bash
# ヘルスチェック
curl http://your-app.elasticbeanstalk.com/api/cache/health

# データ取得
curl http://your-app.elasticbeanstalk.com/api/cache/user:1001

# キー存在確認
curl http://your-app.elasticbeanstalk.com/api/cache/user:1001/exists

# TTL確認
curl http://your-app.elasticbeanstalk.com/api/cache/user:1001/ttl
```

#### 2. redis-cliを使用

**前提:** VPC内のEC2などから接続

```bash
# Redis CLIで接続
redis-cli -h your-redis-cluster.cache.amazonaws.com -p 6379

# 全キーを表示
KEYS *

# 特定のパターンのキーを検索
KEYS user:*

# 値を取得
GET user:1001

# キーの数を確認
DBSIZE

# キーの型を確認
TYPE user:1001

# キーの存在確認
EXISTS user:1001
```

#### 3. Redis Desktop Manager等のGUIツール

**前提:** VPC内からアクセス可能なツール

- RedisInsight（Redis公式）
- Medis
- Another Redis Desktop Manager

**注意:** ElastiCacheは通常プライベートサブネットにあるため、直接接続できない。SSHトンネル等が必要。

---

## データ型

Redisは複数のデータ型をサポートしています。

### 1. String（文字列）

最も基本的な型。

```bash
SET name "Alice"
GET name
→ "Alice"
```

### 2. List（リスト）

順序付きの値のリスト。

```bash
LPUSH mylist "item1"
LPUSH mylist "item2"
LRANGE mylist 0 -1
→ ["item2", "item1"]
```

### 3. Set（セット）

重複のない値の集合。

```bash
SADD myset "value1"
SADD myset "value2"
SADD myset "value1"  # 重複は無視
SMEMBERS myset
→ ["value1", "value2"]
```

### 4. Hash（ハッシュ）

フィールドと値のマップ。

```bash
HSET user:1001 name "Alice"
HSET user:1001 age "30"
HGET user:1001 name
→ "Alice"
HGETALL user:1001
→ {"name": "Alice", "age": "30"}
```

### 5. Sorted Set（ソート済みセット）

スコア付きの値の集合。

```bash
ZADD leaderboard 100 "player1"
ZADD leaderboard 200 "player2"
ZRANGE leaderboard 0 -1 WITHSCORES
→ ["player1", 100, "player2", 200]
```

### 本プロジェクトでの使用

現在は**String型**のみ使用:

```java
// Spring Data Redisでの保存
redisTemplate.opsForValue().set(key, value);  // String型

// 他の型を使う場合
redisTemplate.opsForList()    // List操作
redisTemplate.opsForSet()     // Set操作
redisTemplate.opsForHash()    // Hash操作
redisTemplate.opsForZSet()    // Sorted Set操作
```

---

## ベストプラクティス

### 1. キーの命名規則

**推奨: コロン区切りの階層構造**

```
✅ 良い例:
user:1001
user:1001:profile
user:1001:sessions
order:2023:12345
cache:product:list

❌ 悪い例:
user_1001_profile_data_cache
u1001
data123
```

**理由:**
- 可読性が高い
- 管理しやすい
- パターン検索しやすい（`KEYS user:*`）

### 2. TTLの設定

**推奨: ほとんどのキャッシュにTTLを設定**

```java
// ✅ 良い例: TTL付き
cacheService.saveWithExpiry("session:abc", data, 3600, TimeUnit.SECONDS);

// ⚠️ 注意: TTLなし（メモリ圧迫の可能性）
cacheService.save("session:abc", data);
```

**理由:**
- メモリの無駄遣いを防ぐ
- 古いデータの自動削除
- メモリ不足を防止

**TTLの目安:**
- セッション: 30分〜1時間
- APIキャッシュ: 5分〜15分
- ユーザープロフィール: 1時間〜24時間
- 永続データ: TTLなし（RDBと併用）

### 3. 適切なデータサイズ

**推奨: 1つの値は100KB以下**

```java
// ✅ 良い例: 小さいデータ
{"userId": 1001, "name": "Alice"}

// ❌ 悪い例: 大きいデータ
// 画像データ、動画、大量のログをRedisに保存
```

**理由:**
- Redisはメモリを使用
- ネットワーク転送の効率
- パフォーマンスの維持

### 4. エラーハンドリング

**推奨: Redis接続エラーに備える**

```java
try {
    String value = cacheService.get(key);
    if (value == null) {
        // キャッシュミス → DBから取得
        value = database.findById(key);
        cacheService.save(key, value);
    }
    return value;
} catch (Exception e) {
    // Redis障害時 → DBから直接取得
    log.error("Redis error, fallback to DB", e);
    return database.findById(key);
}
```

### 5. キャッシュ戦略

#### Cache-Aside（Lazy Loading）

**使用タイミング:** 読み取りが多い場合

```java
// 1. キャッシュを確認
String data = cache.get(key);

// 2. キャッシュミス → DBから取得
if (data == null) {
    data = database.get(key);
    cache.set(key, data, ttl);  // キャッシュに保存
}

return data;
```

#### Write-Through

**使用タイミング:** データの一貫性が重要な場合

```java
// データを保存
database.save(key, data);      // 1. DB保存
cache.set(key, data, ttl);     // 2. キャッシュ更新
```

#### Write-Behind（Write-Back）

**使用タイミング:** 書き込みが多い場合

```java
// 1. キャッシュに書き込み
cache.set(key, data, ttl);

// 2. バックグラウンドでDBに保存（非同期）
async.execute(() -> database.save(key, data));
```

---

## 注意点と制限

### 1. メモリ制限

**注意:** Redisは全データをメモリに保持

```
cache.t3.micro = 0.5 GiB メモリ
→ 実際に使えるのは約400MB
```

**対策:**
- 不要なデータは削除
- TTLを設定
- maxmemory-policyを設定

### 2. データの永続性

**注意:** Redisは基本的にインメモリ

ElastiCacheの設定:
- **自動バックアップ**: 有効化推奨
- **スナップショット**: 定期的に作成

**重要データは必ずRDB等に保存**

### 3. ネットワークレイテンシ

**注意:** Redis操作はネットワーク経由

```
ローカル Redis    : 0.1ms
ElastiCache (VPC): 1-5ms
```

**対策:**
- パイプライン機能を使用
- バッチ処理でまとめて取得

### 4. 同時アクセス

**注意:** Redisはシングルスレッド

**対策:**
- WATCH/MULTI/EXECでトランザクション
- Lua スクリプトでアトミック操作

### 5. キーの有効期限

**注意:** TTL切れのタイミング

- 厳密に秒単位で削除されるわけではない
- アクセス時 or バックグラウンドで削除
- 削除されるまでメモリを使用

---

## よくある質問（FAQ）

### Q1: 同じキーで保存したらどうなる？

**A:** 上書きされます。エラーにはなりません。

```bash
SET user:1001 "Alice"
SET user:1001 "Bob"   # Aliceが消えてBobになる
```

### Q2: 重複エラーにする方法は？

**A:** `SETNX`を使います。

```bash
SETNX user:1001 "Alice"  # 成功（1）
SETNX user:1001 "Bob"    # 失敗（0、既存値を保持）
```

### Q3: AWSコンソールでデータを確認できる？

**A:** できません。redis-cliまたはアプリケーション経由で確認します。

### Q4: TTLは必須？

**A:** 必須ではありませんが、推奨です。メモリ枯渇を防げます。

### Q5: TTL -1, -2 の意味は？

**A:**
- `-1`: 有効期限なし（永続）
- `-2`: キーが存在しない

### Q6: データの最大サイズは？

**A:** 512MB（理論値）。ただし実用上は100KB以下を推奨。

### Q7: ElastiCacheとローカルRedisの違いは？

**A:**
- ElastiCache: AWS管理、高可用性、バックアップ自動
- ローカル: 自己管理、開発用途

### Q8: パスワード認証は必要？

**A:** ElastiCacheはVPC内部なので、セキュリティグループで保護。認証は任意。

---

## 参考リンク

- [Redis公式ドキュメント](https://redis.io/documentation)
- [Redis コマンドリファレンス](https://redis.io/commands)
- [AWS ElastiCache ベストプラクティス](https://docs.aws.amazon.com/ja_jp/AmazonElastiCache/latest/red-ug/BestPractices.html)
- [Spring Data Redis](https://spring.io/projects/spring-data-redis)

---

**最終更新:** 2025年10月4日
