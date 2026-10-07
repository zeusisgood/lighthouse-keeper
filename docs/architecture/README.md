最終更新: 0.1.0

# 設計図

要件の正本は [`../handoff.md`](../handoff.md)。図はすべて Mermaid で、GitHub 上でそのまま表示される。

## 読む順番

1. [01-overview.md](01-overview.md) — 全体構成（どの層が何を持つか、commonMain / androidMain の境界）
2. [02-power-flow.md](02-power-flow.md) — ON/OFF 書き込みフロー（リトライ・書き直し・タイムアウト）
3. [03-state-machine.md](03-state-machine.md) — 1台ごとの表示状態と遷移条件
4. [04-screen-flow.md](04-screen-flow.md) — 画面遷移

## 更新ルール

- 図に描かれた挙動（フロー、状態、画面、構成）を変えるコミットでは、同じコミットで該当の図も更新する
- `versionName` を上げるときは、すべての図が実装と一致しているか確認する
- 各ファイル先頭の「最終更新: 対応バージョン」を書き換える
