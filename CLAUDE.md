# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## プロジェクト概要

Valve Lighthouse（SteamVR Base Station 2.0）の電源をBLEでON/スリープ切り替えするAndroidアプリ「Lighthouse Keeper」。
**最優先の価値は「確実に切り替わること」**。要件の正本は `docs/handoff.md`。仕様の詳細（GATT UUID、状態値、画面、権限、配色）はそちらを参照し、ここには重複させない。

- Kotlin Multiplatform + Compose Multiplatform、BLEはKable、永続化はDataStore
- 対象はAndroidのみ（minSdk 31）。ただしBLE/ドメイン層は `commonMain` に置き、将来のiOS追加を妨げない
- パッケージ名: `io.github.zeusisgood.lighthousekeeper`
- モジュール: `shared`（KMPライブラリ。UI・ドメイン・BLE。`commonMain` 中心）/ `androidApp`（Activity・Manifest・Service などAndroid固有の入口）
- compileSdk は devcontainer の SDK 36 に合わせる。Compose MP 1.12 以降は compileSdk 37 を要求するため 1.11 系に固定している

## コマンド

ビルド・テストはdevcontainer内で行う（JDK 17 / Android SDK 36、エミュレーターなし）。

```bash
./gradlew assembleDebug        # APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
./gradlew test                 # 単体テスト
./gradlew test --tests "*AdvertisementParser*"   # 単一テスト
```

- 実機テストはユーザーが手動で行う。各段階の終わりに **APKのパスと実機での確認手順** を提示する
- Composeプレビューは使わない
- SDK/JDKのバージョン変更が必要なら `.devcontainer/` を書き換えず、理由を添えて提案する

## アーキテクチャの要点（複数ファイルにまたがる設計判断）

- **状態取得はアドバタイズのみ**（GATT Readは使わない）。Manufacturer Data（Company ID `0x055D`）のペイロード インデックス4が電源状態
- スキャンは画面表示中は**開始しっぱなし**（Androidの「30秒で5回」制限回避）。必ずManufacturer IDフィルターを付ける
- 書き込みは1台ごとに独立・並列: 接続 → Powerに書き込み → **即切断** → 以降はアドバタイズで成否を判定
  - ON成功 = `0x09`/`0x0B`、OFF成功 = `0x00`、`0x01` は処理中
  - 接続失敗は2秒間隔で再試行、書き込み後10秒変化なし or `0x01` 停止なら再接続して書き直し、1台ごとのタイムアウト（デフォルト60秒、10〜180）で「エラー」
- 処理はForeground Service（`connectedDevice`）で最後まで続ける。通知権限が拒否されていても動作すること
- 設計図は `docs/architecture/`（01-overview / 02-power-flow / 03-state-machine / 04-screen-flow、Mermaid）

## 「保険」のコードは必須要件（削減対象外）

以下は「確実に切り替わること」を担保する**必須要件**。コード量削減（Ponytail含む）を理由に削除・簡略化しない。変更が必要と考えたら、提案としてユーザーに確認する。

- 接続リトライ、10秒変化なし時の書き直し、タイムアウト
- アドバタイズの防御的パース（長さ不足・想定外の値は「不明」）
- 「接続の確立だけ直列にする」内部デバッグフラグ（ユーザー向け設定にはしない）
- ログ記録とログ画面

## Ponytail

- 強さは `lite`（`PONYTAIL_DEFAULT_MODE=lite`）。必要最小限のコードを書くが、上記の保険のコードは対象外
- `/ponytail-review` の指摘は提案としてユーザーに提示し、自動では採用しない
- クラウドセッションではプラグインが読み込まれないため、この方針に従って作業する

## 設計図の更新ルール

- 図に描かれた挙動（フロー、状態、画面、構成）を変更するコミットでは、**同じコミットで該当の図も更新する**
- `versionName` を上げるときは、すべての図が実装と一致しているか確認し、ずれがあれば更新する
- 各図ファイルの先頭に「最終更新: 対応バージョン」を1行書き、更新時に書き換える

## ブランチ・PRのルール

- **mainには直接コミット・pushしない**。PRのマージはユーザーが行う（Claude Codeはマージしない）
- 作業前に `git switch main && git pull` → `git switch -c <英語の小文字ケバブケース>`（例: `add-advertisement-parser`）
- 終わったら `gh pr create` でmainへPR。説明は日本語で「変更内容」「確認方法（ビルド・テストのコマンド、APKのパス、実機での確認手順）」「図の更新有無」を書く
- 1PR = 1作業内容（handoffの「実装の順番」の1段階が目安）
