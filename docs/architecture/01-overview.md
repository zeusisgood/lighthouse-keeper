最終更新: 0.1.0

# 01. 全体構成

```mermaid
flowchart TB
    subgraph androidApp["androidApp（Android固有の入口）"]
        Activity["MainActivity"]
        Service["PowerService<br/>Foreground Service<br/>type: connectedDevice"]
        Notif["通知<br/>（処理中 / 失敗時のみ残す）"]
    end

    subgraph shared["shared"]
        subgraph commonMain["commonMain"]
            UI["UI（Compose MP）<br/>一覧 / デバイス追加 / 設定・ログ / 権限説明"]
            Domain["ドメイン<br/>PowerController（1台ごとの書き込み・リトライ・タイムアウト）<br/>DeviceState（不明 / OFF / 処理中 / ON / エラー）<br/>Log"]
            Parser["AdvertisementParser<br/>（防御的パース）"]
            Ble["BLE層（Kable）<br/>常時スキャン（0x055D フィルター）<br/>接続 → 書き込み → 切断"]
            Repo["Repository（DataStore）<br/>登録デバイス / タイムアウト秒数"]
        end
        subgraph androidMain["androidMain"]
            Platform["Android 依存の小さな実装<br/>スキャン設定（低遅延）/ 時刻表示 / logcat / 戻るボタン"]
        end
    end

    Lighthouse[("Lighthouse 2.0<br/>BLE")]

    Activity --> UI
    UI --> Domain
    Service --> Domain
    Service --> Notif
    Domain --> Ble
    Domain --> Repo
    Ble --> Parser
    Ble <-->|"アドバタイズ受信<br/>GATT Write"| Lighthouse
    UI --> Platform
    Ble --> Platform
```

- 画面・ドメイン・BLE・永続化はすべて `shared/commonMain` に置く。iOS を足すときは `iosMain` と iOS アプリを追加するだけで済む形にする
- `androidApp` は Activity・Foreground Service・通知と、DataStore のファイルパスの受け渡しだけを持つ。書き込み処理の本体はドメイン層にあり、Service はそれを「アプリを閉じても動かし続ける」ための入れ物
- 状態は BLE 層のスキャン結果（アドバタイズ）だけから得る。GATT Read は使わない
- スキャンは「画面表示中」または「処理中」のどちらかが真の間ずっと開始したまま。画面と Service で同じスキャンを共有し、頻繁に start/stop しない
