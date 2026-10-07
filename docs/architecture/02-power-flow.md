最終更新: 0.1.0

# 02. ON/OFF 書き込みフロー

1台分のフロー。「全部ON/OFF」では、これが台数分**独立して並列**に走る（内部デバッグフラグが有効なときは「接続の確立」だけ直列）。

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
                alt 接続失敗
                    Ble-->>Ctrl: 失敗
                    Note over Ctrl: 2秒待って再試行
                end
            end
            Ctrl->>Ble: Power に書き込み（ON: 0x01 / OFF: 0x00）
            Ble->>LH: GATT Write
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

- 書き込みのあとはすぐ切断し、成否は**アドバタイズの状態値だけ**で判定する
- 接続失敗は2秒の固定間隔で再試行する。書き込みから10秒以内に目標値にならなければ、再接続から書き直す。どちらもタイムアウトの範囲内で行う
- 「すでに目標と一致」は、アドバタイズが新しい（不明になっていない）ときだけ。古い・不明なら念のため書き込む
- 処理中に逆の操作をされたら、その台の処理をキャンセルして新しい目標でやり直す（タイムアウトも取り直す）
- 接続・書き込み・リトライ・状態の変化はすべてログに記録する
