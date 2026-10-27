# 開発の進め方

## ブランチ

```
dev/<機能>  →  dev/main  →  test/release  →  main（タグ vX.Y.Z）
```

- 機能ごとに `dev/<機能>` を切り、終わったら `--no-ff` で `dev/main` に入れる。
- Concept.md では統合用のブランチを `dev` としていたが、Git では `dev` と `dev/*` を同時に作れないので `dev/main` にした。
- `test/release` でクリーンビルド・テスト・lint を通してから `main` に入れる。リリースの詳細は [release.md](release.md)。
- リモートに push するのは `main` とタグだけ。`dev/*` と `test/*` は手元にだけ置く。

## コミット

- 変更内容ごとに 1 コミット。メッセージは Conventional Commits の形式（`feat(engine): …`、`fix(app): …`、`docs: …`）。
- コミットの前にテストか検証コマンドを通す。

## 検証

| 対象 | コマンド |
|---|---|
| 変換エンジンの単体テスト・精度 | `./gradlew :engine:test` |
| アプリの単体テスト | `./gradlew :app:testDebugUnitTest` |
| lint | `./gradlew :app:lintRelease` |
| 精度の数字 | テスト結果の XML に出る `quality:`（品質回帰テスト）、`mozc evaluation:`（評価セット）、`msime`（手元だけ）の行 |

変換の精度を測るテストの詳細は [conversion.md](conversion.md#評価のしかた)。

## レビュー

- 実装の後に、別系統のモデル（Codex）で敵対的レビューを取る。指摘はそのまま受け入れず、再現や計測で確かめてから直す。
  直さなかった指摘は、理由をコミットか文書に残す（例：[keyboard-design.md](keyboard-design.md) のパネル中の日英ボタン）。
- 署名やパスワードを扱う変更は、外部に送らずローカルのモデルでレビューする。
- 日本語の文書は校正を通し、数値・コマンド・バージョンなどの主張は一次情報で確かめる。
