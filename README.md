# Lighthouse Keeper

SteamVR ベースステーション（Lighthouse）の電源を、Android から Bluetooth LE で ON / スリープに切り替えるアプリです。
「**確実に切り替わること**」を最優先にしています（接続リトライ・書き直し・タイムアウト・ログ）。

機能は ON/OFF だけです。SteamVR 連動などの自動化はしません。

## 現在の状況

| 項目 | 状況 |
|---|---|
| バージョン | 0.3.0（プレリリース・個人利用向け） |
| Base Station 2.0（Valve Index 世代、`LHB-` で始まる名前） | 対応済み・実機で確認済み |
| Base Station 1.0（HTC Vive 世代、`HTC BS` で始まる名前） | 対応（**実機で未検証**）。状態は読めないため「送信済み」表示 |
| 対応 OS | Android 12 以上（iOS は未対応） |
| ライセンス | 未定 |

実装済みの機能:

- 登録したベースステーションの状態（ON / OFF / 処理中 / エラー / 不明）のリアルタイム表示
- 全部ON / 全部OFF、1台ごとの切り替え（並列に処理）
- 接続リトライ、10秒で変化しなければ書き直し、1台ごとのタイムアウト（10〜180秒）
- アプリを閉じても処理を続ける（Foreground Service）。失敗したときだけ通知を残す
- 別名、点滅（どの実機かの確認。2.0 のみ）、ログ画面

Base Station 1.0 について:

- 本体ID（背面ラベルの8桁。SteamVR のデバイス一覧の `LHB-XXXXXXXX` でも分かります）の入力は任意です。入れておくとスリープが確実になります（先頭4桁だけでも可。あとから詳細画面でも入れられます）
- 1.0 は電源状態を外に出さないため、「コマンドを受け付けた」ことまでしか確認できません。表示は「ON（送信済み）」「OFF（送信済み）」です
- 手元に実機がなく、動作は未確認です。動いた・動かなかったの報告（ログ画面の内容つき）を Issue でもらえると助かります

## インストール

[Releases](https://github.com/zeusisgood/lighthouse-keeper/releases) から APK をダウンロードしてインストールします。

- いまは debug 署名のビルドです。上書きインストールできないときは、一度アンインストールしてから入れ直してください
- 初回起動時に「付近のデバイス（Bluetooth）」と「通知」の権限を求めます。通知は拒否しても動きます。位置情報は使いません

## 使い方

1. 右上の「＋」から、近くのベースステーションをタップして登録する
2. 詳細画面で「点滅させる」を押してどの実機かを確かめ、別名（例: 左）を付ける
3. 一覧の「全部ON」「全部OFF」か、各カードのトグルで切り替える（1.0 と 2.0 は混ぜて操作できます）

## 開発

Kotlin Multiplatform + Compose Multiplatform（現在は Android のみ）、BLE は [Kable](https://github.com/JuulLabs/kable)。JDK 17 / Android SDK 36 が必要です（`.devcontainer/` あり）。

```bash
./gradlew assembleDebug                  # androidApp/build/outputs/apk/debug/androidApp-debug.apk
./gradlew :shared:testAndroidHostTest    # 単体テスト
```

- 要件・BLE プロトコル: [`docs/handoff.md`](docs/handoff.md)
- 設計図（Mermaid）: [`docs/architecture/`](docs/architecture/README.md)
- `v*` タグを push すると、GitHub Actions が APK をビルドして Release を作ります

## 謝辞

既存の OSS を参考にしています。特に Base Station 1.0 のプロトコルは、次のプロジェクトの調査結果に基づいています。

- [jeroen1602/lighthouse_pm](https://github.com/jeroen1602/lighthouse_pm)
- [risa2000/lhctrl](https://github.com/risa2000/lhctrl) / [lh2ctrl](https://github.com/risa2000/lh2ctrl)
- [nairol/LighthouseRedox](https://github.com/nairol/LighthouseRedox)
- [nullstalgia/LighthouseESP](https://github.com/nullstalgia/LighthouseESP)
- [kurotu/OVR-Lighthouse-Manager](https://github.com/kurotu/OVR-Lighthouse-Manager)

Valve、SteamVR、Lighthouse は Valve Corporation の、HTC、Vive は HTC Corporation の商標です。このアプリは各社と関係ありません。
