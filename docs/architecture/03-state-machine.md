最終更新: 0.2.0

# 03. 1台ごとの表示状態

```mermaid
stateDiagram-v2
    [*] --> 不明

    state "不明" as Unknown
    state "OFF" as Off
    state "ON" as On
    state "処理中（経過秒数）" as Busy
    state "エラー" as Error

    Unknown --> Off: アドバタイズ 0x00 / 0x02
    Unknown --> On: アドバタイズ 0x09 / 0x0B
    Off --> On: アドバタイズ 0x09 / 0x0B
    On --> Off: アドバタイズ 0x00 / 0x02
    Off --> Unknown: 10秒受信なし / 想定外の値・長さ不足
    On --> Unknown: 10秒受信なし / 想定外の値・長さ不足

    Unknown --> Busy: ON/OFF 操作
    Off --> Busy: ON 操作
    On --> Busy: OFF 操作
    Busy --> Busy: 逆の操作（キャンセルしてやり直し）<br/>接続リトライ / 10秒で書き直し
    Busy --> On: 目標ON かつ 0x09 / 0x0B
    Busy --> Off: 目標OFF かつ 0x00
    Busy --> Error: タイムアウト（1台ごと）

    Error --> Busy: ON/OFF 操作
    Error --> On: 目標ON だった台で 0x09 / 0x0B を受信
    Error --> Off: 目標OFF だった台で 0x00 を受信
```

- 処理していない台の表示はアドバタイズの値そのまま。`0x01`（起動中）を処理外で受信したときは「処理中」ではなく直前の表示のままにする
- 10秒アドバタイズを受信しなければ「不明」に戻す。最終確認時刻（最後に受信した時刻）はカードに常に表示する
- 処理中は、目標値を受信するまで「処理中（経過秒数）」を表示し続ける。途中で `0x01` を受信しても処理中のまま
- 「エラー」は次の操作をするか、もともとの目標値をアドバタイズで受信するまで保持する
- すでに目標と一致している台への操作は、処理中を経由せずそのまま（02 を参照）
