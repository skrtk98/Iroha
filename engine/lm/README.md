# iroha.lm

変換で文字の並びの自然さを測る、文字 3-gram の言語モデル（補間 Kneser-Ney、出現 2 回以上の 3-gram だけを収録）。
形式は `LanguageModel.kt` を参照。

## コーパス

| コーパス | ライセンス | 取得日 | SHA-256 |
|---|---|---|---|
| [Tatoeba](https://tatoeba.org/) 日本語の文 (`jpn_sentences.tsv.bz2`) | CC BY 2.0 FR | 2026-10-23 | `9b2f0c18f58a5633a0dfba22b6a5f7a2bbbd55f649aeafcc31107d1be5cc5c2b` |
| [日本語版ウィキニュース](https://ja.wikinews.org/) 記事 (`jawikinews-latest-pages-articles.xml.bz2`, 2026-10-01 版) | CC BY 4.0 | 2026-10-23 | `1ea1c05b2b8f7d5dc729a6e451fa2e5689f4d6617c7f1ac007ec46bbb26340e2` |
| [ABEJA-CC-JA](https://registry.opendata.aws/abeja-cc-ja/) の 10 か所（各 30MB）から取り出した文 (`web.txt`) | [Common Crawl Terms of Use](https://commoncrawl.org/terms-of-use/) | 2026-10-27 | `240bb70b794e53c2bb9cffacf2bf36c3228b77514dc990e42c25e74e1f7b208a` |

配布元が最新版しか置かないため、作ったモデルをリポジトリに置いている。結合したコーパス (`corpus.txt`) の SHA-256 は
`23f9febfeaaca191cfe965b601bb825d6bab768764b145086ff97e2cf57e91d9`。

Tatoeba とウィキニュースは書き言葉の整った文、ABEJA-CC-JA は量の多い今のウェブの文（約 258 万文、8,540 万字）として使う。
ウェブの文だけを足すと「行かなければ」がひらがなのままになるなど、書き方がウェブに寄るので、`corpus.txt` を 3 回渡して重みを上げている。
ABEJA-CC-JA からは、`prepare_corpus.sh` に書いたファイル番号とバイト位置の範囲を取り出し、`web_corpus.py` で文に分けて重複を除く。

## 作り直し

```
scripts/lm/prepare_corpus.sh /tmp/iroha-corpus
./gradlew :engine:buildLanguageModel \
    -Piroha.corpus=/tmp/iroha-corpus/corpus.txt,/tmp/iroha-corpus/corpus.txt,/tmp/iroha-corpus/corpus.txt,/tmp/iroha-corpus/web.txt
```

作り直したら `:engine:test` の `quality:` の行で精度を比べ、`Converter.LANGUAGE_MODEL_WEIGHT` を見直す。
手元に Microsoft Research IME Corpus があれば、`-Diroha.msImeDir=<展開した場所>` を付けると `msime` の行で新聞記事 6,000 文の精度も比べられる。

## ウェブの文を加えたときの精度

重み 350 での比較。品質回帰テストは mozc の 678 件、MS IME は Microsoft Research IME Corpus の 6,000 文。

| コーパス | モデルの大きさ | 品質回帰テスト | MS IME の完全一致 | MS IME の文字誤り率 |
|---|---|---|---|---|
| `corpus.txt` だけ | 3.3MB | 505 | 1,349 | 5.53% |
| `corpus.txt` ＋ `web.txt` | 20.8MB | 502 | 1,512 | 5.26% |
| `corpus.txt` ×3 ＋ `web.txt`（採用） | 21.8MB | 508 | 1,521 | 5.18% |
| `corpus.txt` ×3 ＋ ウェブの文 4 億字（3 回以上出た 3-gram だけ） | 38.4MB | 505 | 1,619 | 5.09% |

ウェブの文を増やすほど MS IME の精度は上がるが、アプリの大きさとの兼ね合いで 8,540 万字にとどめた。

## 試して採らなかったコーパス

- 国会会議録（2015〜2025 年の各月から 300 発言ずつ、約 1,430 万字）を加えると、品質回帰テストが 491 件から 481 件に下がった。
  重みを 250〜450 で振っても 482 件までしか戻らず、混ぜる量を 1/3・1/6・1/12 に減らしても 485・487・489 件と、
  減らすほど戻るだけだった。「今日給料日」が「供給量日」になるなど、議会の硬い言い回しに引っ張られる。
