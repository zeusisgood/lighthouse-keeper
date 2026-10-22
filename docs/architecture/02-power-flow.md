最終更新: 0.3.0

# 02. ON/OFF 書き込みフロー

1台分のフロー。「全部ON/OFF」では、これが台数分**独立して並列**に走る（内部デバッグフラグが有効なときは「接続の確立」だけ直列）。1.0 と 2.0 を混ぜて操作してよい。

## 2.0

```mermaid
sequenceDiagram
    autonumber
    actor User as ユーザー
    participant Ctrl as PowerController<br/>（1台分）
    participant Ble as BLE層（Kable）
    participant LH as Lighthouse
    participant Scan as 常時スキャン

    User->>Ctrl: ON / OFF
    Note over Ctrl: タイムアウト開始（1台ごと、デフォルト60秒、10〜180）
    alt 表示状態がすでに目標と一致（アドバタイズが新しい）
        Ctrl-->>User: 書き込まずに成功
    else それ以外
        loop 成功 or タイムアウトまで
            loop 接続できるまで
                Ctrl->>Ble: 接続
                Ble->>LH: connect
                alt 接続失敗（10秒で接続できない場合も含む）
                    Ble-->>Ctrl: 失敗
                    Note over Ctrl: 2秒待って再試行
                end
            end
            Ctrl->>Ble: Power に書き込み（ON: 0x01 / OFF: 0x00）
            Ble->>LH: GATT Write
            Note over Ctrl: 書き込み失敗も接続失敗と同じく<br/>切断して2秒後に接続から再試行
            Ctrl->>Ble: すぐに切断
            Ble->>LH: disconnect
            loop 書き込みから最大10秒
                LH-->>Scan: アドバタイズ（index 4 = 状態値）
                Scan-->>Ctrl: 状態値
            end
            alt 目標値になった（ON: 0x09/0x0B、OFF: 0x00）
                Ctrl-->>User: 成功
            else 10秒たっても目標値にならない（変化なし / 0x01 で停止 を含む）
                Note over Ctrl: 再接続して書き直す（ループ先頭へ）
            end
        end
    end
    opt タイムアウト
        Ctrl-->>User: エラー
    end
```

## 1.0（実機未検証）

```mermaid
sequenceDiagram
    autonumber
    actor User as ユーザー
    participant Ctrl as PowerController<br/>（1台分）
    participant Ble as BLE層（Kable）
    participant LH as Lighthouse 1.0

    User->>Ctrl: ON / OFF
    Note over Ctrl: タイムアウト開始（2.0 と同じ）
    loop 受け付けを確認できる or タイムアウトまで
        Ctrl->>Ble: 接続（10秒で接続できなければ失敗）
        Ble->>LH: connect
        Ctrl->>Ble: 0xCB01 に20バイト書き込み<br/>ON: 12 00 00 00 FFFFFFFF / OFF: 12 02 00 01 [ID]
        Ble->>LH: GATT Write
        Ctrl->>Ble: 同じ接続で読み戻し
        Ble->>LH: GATT Read
        LH-->>Ctrl: [エラー] 12 [タイムアウト秒]
        Ctrl->>Ble: 切断
        alt ON: 00 12 00 00 / OFF: 00 12 00 01
            Ctrl-->>User: 成功（送信済み）
        else 接続・書き込み・読み戻しの失敗 / 想定外の値
            Note over Ctrl: 2秒待って接続から再試行
        end
    end
    opt タイムアウト
        Ctrl-->>User: エラー
    end
```

## 要点

- 2.0 は書き込みのあとすぐ切断し、成否は**アドバタイズの状態値だけ**で判定する
- 1.0 はアドバタイズに状態が出ないので、読み戻しで「コマンドを受け付けた」ことだけを確認する。10秒変化なし時の書き直しはない
- 接続失敗は2秒の固定間隔で再試行する。`connect()` が10秒で終わらなければ失敗扱い（Android 既定の約30秒を待つとタイムアウト内の再試行回数が減るため）。書き込みから10秒以内に目標値にならなければ、再接続から書き直す。どちらもタイムアウトの範囲内で行う
- 「すでに目標と一致」は、アドバタイズが新しい（不明になっていない）ときだけ。古い・不明なら念のため書き込む
- 処理中に逆の操作をされたら、その台の処理をキャンセルして新しい目標でやり直す（タイムアウトも取り直す）
- 接続・書き込み・リトライ・状態の変化はすべてログに記録する
