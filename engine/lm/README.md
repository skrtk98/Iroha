# iroha.lm

変換で文字の並びの自然さを測る、文字 3-gram の言語モデル（補間 Kneser-Ney、出現 2 回以上の 3-gram だけを収録）。
形式は `LanguageModel.kt` を参照。

## コーパス

| コーパス | ライセンス | 取得日 | SHA-256 |
|---|---|---|---|
| [Tatoeba](https://tatoeba.org/) 日本語の文 (`jpn_sentences.tsv.bz2`) | CC BY 2.0 FR | 2026-10-23 | `9b2f0c18f58a5633a0dfba22b6a5f7a2bbbd55f649aeafcc31107d1be5cc5c2b` |
| [日本語版ウィキニュース](https://ja.wikinews.org/) 記事 (`jawikinews-latest-pages-articles.xml.bz2`, 2026-10-01 版) | CC BY 4.0 | 2026-10-23 | `1ea1c05b2b8f7d5dc729a6e451fa2e5689f4d6617c7f1ac007ec46bbb26340e2` |

配布元が最新版しか置かないため、作ったモデルをリポジトリに置いている。結合したコーパス (`corpus.txt`) の SHA-256 は
`23f9febfeaaca191cfe965b601bb825d6bab768764b145086ff97e2cf57e91d9`。

## 作り直し

```
scripts/lm/prepare_corpus.sh /tmp/iroha-corpus
./gradlew :engine:buildLanguageModel -Piroha.corpus=/tmp/iroha-corpus/corpus.txt
```

作り直したら `:engine:test` の `quality:` の行で精度を比べ、`Converter.LANGUAGE_MODEL_WEIGHT` を見直す。

## 試して採らなかったコーパス

- 国会会議録（2015〜2025 年の各月から 300 発言ずつ、約 1,430 万字）を加えると、品質回帰テストが 491 件から 481 件に下がった。
  重みを 250〜450 で振っても 482 件までしか戻らず、混ぜる量を 1/3・1/6・1/12 に減らしても 485・487・489 件と、
  減らすほど戻るだけだった。「今日給料日」が「供給量日」になるなど、議会の硬い言い回しに引っ張られる。
