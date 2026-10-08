# Lighthouse Keeper 実装依頼

あなたはAndroidアプリ「Lighthouse Keeper」の実装担当です。以下の要件定義は設計相談で確定済みの内容です。

**進め方のルール**
- 実装を始める前に、この要件の理解を要約し、不明点があれば質問してください。私が了承するまでコードは書かないでください。
- 了承後は、下記「実装の順番」に沿って段階的に進め、各段階の終わりで動作確認の方法を提示してください。

---

## 1. 概要
- Valve Lighthouse（SteamVR Base Station 2.0）の電源をBLEでON/スリープ切り替えするAndroidアプリ
- 既存OSS（lighthouse_pm）が時々操作できないなど不安定なため自作する。**最優先の価値は「確実に切り替わること」**
- 機能はON/OFFのみ。SteamVR連動などの自動化はしない。Home Assistant連携もしない

## 2. 技術・プロジェクト設定
| 項目 | 内容 |
|---|---|
| アプリ名 | Lighthouse Keeper（ホーム画面のラベルは「Keeper」） |
| リポジトリ名 | `lighthouse-keeper` |
| パッケージ名 | `io.github.zeusisgood.lighthousekeeper` |
| 構成 | Kotlin Multiplatform + Compose Multiplatform（UI共有） |
| BLE | Kable |
| 対象 | **Androidのみ**（iOSは将来のTODO。ただしBLE/ドメイン層はcommonMainに置き、iOS追加を妨げない構成にする） |
| minSdk | 31（Android 12） |
| 永続化 | DataStore |
| 公開 | 当面は個人利用（private repo想定） |

## 3. BLEプロトコル（実機で確認済み）
### GATT
- Service: `00001523-1212-efde-1523-785feabcd124`
- Power: `00001525-1212-efde-1523-785feabcd124`（READ / WRITE / NOTIFY）
  - 書き込み: `0x01`=ON、`0x00`=スリープ、`0x02`=スタンバイ（MVPでは使わない）
- Identify: `00008421-1212-efde-1523-785feabcd124`（WRITE）: `0x00` を書くとLEDが点滅
- Mode: `00001524-...`（MVPでは使わない）

### 電源状態の値（Read値 = アドバタイズ値）
| 値 | 意味 | アプリでの扱い |
|---|---|---|
| `0x00` | スリープ | OFF確定 |
| `0x02` | スタンバイ | OFF扱い |
| `0x01` | 起動中（この値で止まることがある） | 処理中 |
| `0x09` / `0x0B` | 起動済み | **完全ON** |
| その他 | — | 不明 |

※実機ではスリープから起動しても `0x0B` になった。`0x09`/`0x0B` はまとめて完全ONとして扱う。
※スリープ→完全ONは実測で約5秒。

### アドバタイズ（接続不要で状態が取れる）
- デバイス名: `LHB-XXXXXXXX`
- Manufacturer Specific Data: Company ID `0x055D`（Valve）
- ペイロード（Company IDを除く）: `00 02 01 01 [STATE] 06 00`
  - **インデックス4（0始まり）が電源状態**で、値は上表と同じ
  - 例: スリープ `00 02 01 01 00 06 00` / ON `00 02 01 01 0B 06 00`
- アドバタイズ間隔は約43ms
- MACアドレスはstatic random（再起動後も不変）なので、識別子として使ってよい
- ⚠ この形式は1台でのみ確認済み。パースは防御的に書き、長さ不足や想定外の値は「不明」とする

## 4. 動作仕様
### 状態の取得
- **状態はアドバタイズのみで取得する（GATT Readは使わない）**
- 画面表示中は常時スキャンし、状態をリアルタイムに表示する
- Androidのスキャン開始回数制限（30秒で5回）に引っかからないよう、スキャンは開始しっぱなしで運用し、頻繁にstart/stopしない
- スキャンには必ずフィルター（Manufacturer ID `0x055D`）を付ける（画面オフ時も動かすため）

### ON/OFFの書き込みフロー（1台ごとに独立して並列実行）
1. 接続（**全台並列**）
2. Powerに `0x01`（ON）または `0x00`（OFF）を書き込み
3. **すぐに切断**
4. 以降はアドバタイズの状態値を監視
   - ON: `0x09` か `0x0B` になったら成功
   - OFF: `0x00` になったら成功
5. リトライ
   - 接続失敗: 2秒の固定間隔で再試行
   - 書き込みから10秒たっても値が変わらない、または `0x01` のまま止まっている場合: 再接続して書き直す
   - すべてタイムアウトの範囲内で行う
6. タイムアウト: **1台ごと**、ON/OFF共通、デフォルト60秒（設定で10〜180秒）。超えたら「エラー」

### 「保険」のコードは必須要件（コード削減の対象外）
次のコードは「確実に切り替わること」を担保するための**必須要件**です。コード量を減らす目的で削除・簡略化しないでください。変更が必要だと考えた場合は、提案として私に確認してください。
- 接続リトライ、10秒変化なし時の書き直し、タイムアウト
- アドバタイズの防御的なパース（長さ不足・想定外の値を「不明」にする処理）
- 下記の直列接続デバッグフラグ
- ログ記録とログ画面

この方針は `CLAUDE.md` にも記載してください。

### デバッグ用の保険
- 並列接続で不安定な端末向けに、「接続の確立だけ直列にする」モードを内部フラグとして用意する（ユーザー向け設定にはしない）

### バックグラウンド
- 処理中はアプリを閉じても最後まで続ける（Foreground Service、type: `connectedDevice`）
- 処理中の通知: 例「ベースステーションを起動中… 3台中1台完了」→ 完了したら消す
- **失敗時のみ**、スワイプで消せる通知を残す（例「左: 起動を確認できませんでした」）
- 通知権限が拒否されていても処理は動くようにする

## 5. 画面
### ① 一覧（メイン）
- 上部に「全部ON」「全部OFF」の大きなボタン
- 登録済みデバイスのカード
  - 別名（大きく）＋ `LHB-XXXXXXXX`（小さく）。別名が空なら元の名前のみ
  - 状態: ON / OFF / 処理中（経過秒数） / エラー / 不明
  - 最終確認時刻（最後にアドバタイズを受信した時刻）
  - 個別のON/OFFトグル
- カードをタップでデバイス詳細へ
- 右上に「＋（追加）」「⚙（設定）」
- 0台のときは「デバイスを追加」の大きなボタンを表示

### ② デバイス追加（スキャン）
- 開いたらスキャン開始、`LHB-` で始まるものだけを表示
- 登録済みはグレーアウト
- タップで登録 → デバイス詳細（別名入力はスキップ可）→ 一覧へ

### デバイス詳細（登録直後・一覧のカードタップ）
- 状態と個別のON/OFFトグル（一覧のカードと同じ表示）、MACアドレス
- 別名の入力・保存
- 「点滅させる」ボタン（Identifyに `0x00` を書き込み）。どの実機かを確認できるようにする
- このデバイスのログ（状態遷移・接続・書き込み）
- 削除（確認ダイアログあり）

### ③ 設定
- タイムアウト秒数（10〜180、デフォルト60）
- アプリのバージョン
- ログ閲覧（接続・書き込み・リトライ・状態遷移の記録。不具合調査用）

### 初回起動
1. 権限の説明画面（1枚）: Bluetooth（近くのデバイス）と通知
2. 権限の許可ダイアログ
3. 一覧へ

### 永続化（DataStore）
- 登録デバイス: MACアドレス、元の名前、別名、登録順
- 設定: タイムアウト秒数

## 6. 権限
- `BLUETOOTH_SCAN`（`android:usesPermissionFlags="neverForLocation"`）
- `BLUETOOTH_CONNECT`
- `POST_NOTIFICATIONS`（任意。拒否されても動作する）
- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_CONNECTED_DEVICE`
- 位置情報の権限は不要

## 7. デザイン
- ミニマル・機能的、**ダークモードをデフォルト**
- 配色のたたき台

| 用途 | カラー |
|---|---|
| 背景 | `#121417` |
| カード | `#1E2228` |
| ON | `#4ADE80` |
| OFF / スリープ | `#64748B` |
| 処理中 | `#38BDF8` |
| エラー | `#F87171` |
| 文字 | `#E5E7EB` |

## 8. 実装の順番（提案）
1. プロジェクト雛形（KMP + Compose MP、Android target、minSdk 31）。この資料は `docs/handoff.md` として置く
   - 続けて「10. 設計ドキュメント」の図を作成し、私のレビューを受けてから次へ進む
2. アドバタイズのパーサー＋単体テスト（上記のスリープ/ONの実データをテストケースに使う）
3. スキャンと一覧表示（状態がリアルタイムで変わることを確認）
4. 書き込みフロー（並列接続 → 書き込み → 切断 → アドバタイズで確認 → リトライ/タイムアウト）
5. デバイス追加・別名・Identify・永続化
6. Foreground Serviceと通知
7. 設定画面とログ
8. 初回起動の権限フロー

## 9. MVPに含めないもの（TODO）
- iOS対応
- Lighthouse 1.0（HTC Vive）対応
- スタンバイへの切り替え
- ウィジェット、Quick Settingsタイル
- SteamVR連動などの自動化
- ライセンス・公開範囲の決定

## 10. 設計ドキュメント（Mermaid）
### 置き場所と構成
```
docs/
├── handoff.md              ← この資料
└── architecture/
    ├── README.md           ← 図の一覧と読む順番
    ├── 01-overview.md
    ├── 02-power-flow.md
    ├── 03-state-machine.md
    └── 04-screen-flow.md
```
- 各ファイルはMarkdown＋Mermaidのコードブロック（GitHub上でそのまま図として表示される形）
- 図だけでなく、図の下に要点を数行の文章で添える

### 各図の内容
| ファイル | Mermaidの種類 | 内容 |
|---|---|---|
| `01-overview.md` | flowchart | UI / ドメイン / BLE層（Kable）/ DataStore / Foreground Service の関係。commonMain と androidMain の境界も示す |
| `02-power-flow.md` | sequenceDiagram | ON/OFFの書き込みフロー：並列接続 → 書き込み → 即切断 → アドバタイズで確認。接続リトライ（2秒間隔）、10秒変化なし・`0x01`停止時の書き直し、タイムアウト |
| `03-state-machine.md` | stateDiagram-v2 | 1台ごとの表示状態（不明 / OFF / 処理中 / ON / エラー）と遷移条件（アドバタイズの状態値、書き直し、タイムアウト） |
| `04-screen-flow.md` | flowchart | 初回起動 → 権限説明 → 許可 → 一覧 ⇄ デバイス追加 → デバイス詳細（別名・Identify・ログ・削除）/ 設定（ログ閲覧） |

### 更新ルール
- **図に描かれた挙動（フロー、状態、画面、構成）を変更するコミットでは、同じコミットで該当の図も更新する**
- バージョンを上げる（`versionName` を変更する）ときは、すべての図が実装と一致しているかを確認し、ずれがあれば更新する
- 各ファイルの先頭に「最終更新: 対応バージョン」を1行書き、更新時に書き換える
- この更新ルールは `CLAUDE.md` にも記載し、以降のセッションでも守られるようにする

## 11. 開発環境：Ponytailプラグイン
コード量を必要最小限にするため、Claude Codeプラグイン「Ponytail」（https://github.com/DietrichGebert/ponytail）をプロジェクトスコープで使います。

### 雛形作成時にやること（Claude Code担当）
- `.claude/settings.json` を次の内容で作成してコミットする
```json
{
  "enabledPlugins": {
    "ponytail@ponytail": true
  },
  "extraKnownMarketplaces": {
    "ponytail": {
      "source": { "source": "github", "repo": "DietrichGebert/ponytail" }
    }
  },
  "env": {
    "PONYTAIL_DEFAULT_MODE": "lite"
  }
}
```
- `CLAUDE.md` に次を記載する
  - Ponytailの強さは `lite`
  - 「4. 動作仕様」の**保険のコードは必須要件で、Ponytailの削減対象外**
  - `/ponytail-review` の指摘は提案として私に提示し、自動では採用しない

### 私がやること（人間側）
- 各PCで初回だけ、リポジトリのルートで `claude plugin install ponytail@ponytail --scope project` を実行する（設定で有効になっていても、本体は自動ダウンロードされないため）
- インストール後は `/reload-plugins` で反映し、`/` の候補に ponytail のコマンドが出ることを確認する

### クラウドセッションでの扱い
- クラウドセッションでは、リポジトリの `.claude/settings.json` に書いたプラグインは読み込まれない
- そのためクラウドではPonytailのコマンドは使えない。`CLAUDE.md` に書いた方針（必要最小限のコード、ただし保険のコードは対象外）に従って作業する

## 12. 開発環境：devcontainer
- 開発はWindows + WSL2上のdevcontainer（VS Code）で行う。`.devcontainer/`（Dockerfile / devcontainer.json）は私がリポジトリに同梱済み
- 中身: JDK 17、Android SDK（platform-tools / platform / build-tools）、Node、gh、Claude Code。エミュレーターは入れない
- ビルドとテストはこのコンテナ内で行う（`./gradlew assembleDebug`、`./gradlew test` など）
- **実機テストは私が手動で行う**。各段階の終わりに、ビルドしたAPKのパス（`app/build/outputs/apk/debug/` など）と、実機で確認してほしい操作手順を提示すること
- Compose画面のプレビューは使わない（見た目の確認も実機で行う）
- SDKやJDKのバージョンを変える必要が出た場合は、`.devcontainer/` を勝手に書き換えず、理由を添えて私に提案すること
- Ponytailは `postCreateCommand` で自動インストールされる（「11」の手動インストール手順は、コンテナでは不要）

## 13. ブランチ・PRのルール
- **mainには直接コミット・pushしない**
- 作業のたびに、最新のmainからブランチを切る
  - ブランチ名は作業内容を英語で簡潔に、小文字のケバブケースで書く（例: `add-advertisement-parser`, `fix-retry-timeout`, `update-architecture-diagrams`）
  - 作業前に `git switch main && git pull` で最新にしてから `git switch -c <ブランチ名>`
- 作業が終わったら、mainに向けたPRを `gh pr create` で作成する
  - PRの説明は日本語で、「変更内容」「確認方法（ビルド・テストのコマンド、APKのパス、実機での確認手順）」「図の更新有無」を書く
- **PRのマージは私が行う**。Claude Codeはマージしない
- 1つのPRには1つの作業内容だけを含める（「8. 実装の順番」の1段階＝1PRが目安）
- このルールは `CLAUDE.md` にも記載する

### 誤コミット防止（雛形作成時に設定）
`.claude/settings.json` の `permissions.deny` に、mainへの直接pushを禁止するルールを追加する
```json
"permissions": {
  "deny": [
    "Bash(git push origin main:*)",
    "Bash(git push -f:*)",
    "Bash(git push --force:*)"
  ]
}
```
