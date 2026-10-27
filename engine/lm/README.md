# iroha.lm

変換で文字の並びの自然さを測る、文字 3-gram の言語モデル（補間 Kneser-Ney、出現 2 回以上の 3-gram だけを収録）。
形式は `LanguageModel.kt` を参照。

## コーパス

| コーパス | ライセンス | 取得日 | SHA-256 |
|---|---|---|---|
| [Tatoeba](https://tatoeba.org/) 日本語の文 (`jpn_sentences.tsv.bz2`) | CC BY 2.0 FR | 2026-10-23 | `9b2f0c18f58a5633a0dfba22b6a5f7a2bbbd55f649aeafcc31107d1be5cc5c2b` |
| [日本語版ウィキニュース](https://ja.wikinews.org/) 記事 (`jawikinews-latest-pages-articles.xml.bz2`, 2026-10-01 版) | CC BY 4.0 | 2026-10-23 | `1ea1c05b2b8f7d5dc729a6e451fa2e5689f4d6617c7f1ac007ec46bbb26340e2` |
| [ABEJA-CC-JA](https://registry.opendata.aws/abeja-cc-ja/) の 48 か所（各 30MB）から取り出した文 (`web.txt`) | [Common Crawl Terms of Use](https://commoncrawl.org/terms-of-use/) | 2026-10-27 | `d1d1bce4e7a6d5557420579915c9b2f8b707645ef8de06d9bec8b8dd454f26df` |

配布元が最新版しか置かないため、作ったモデルをリポジトリに置いている。結合したコーパス (`corpus.txt`) の SHA-256 は
`23f9febfeaaca191cfe965b601bb825d6bab768764b145086ff97e2cf57e91d9`。

Tatoeba とウィキニュースは整った文、ABEJA-CC-JA は量の多い今のウェブの文として使う。
ABEJA-CC-JA からは、`prepare_corpus.sh` に書いたファイル番号とバイト位置の範囲を取り出し、`web_corpus.py` で文に分けて重複を除く
（約 1,222 万文、4 億 626 万字）。そのまま全部使うとモデルが大きくなり、書き方もウェブに寄る
（「来るわぁ」がひらがなのまま、「風呂はいる」など）ので、`:engine:selectWebSentences` で書き言葉に近い文を 8,500 万字ぶん選ぶ
（約 275 万文、`selected.txt` の SHA-256 は `eba60ed62258a37c13616445e3896c07601ae945884f0f30e5ea298e3b7e869a`）。
選び方は Moore-Lewis 法で、`corpus.txt` のモデルとウェブの文の一部（`corpus.txt` と同じくらいの量を文の指紋で選ぶ）のモデルで各文を採点し、
1 文字あたりで `corpus.txt` のモデルのほうがよく当てはまる文から順に取る。口コミの定型文、顔文字、宣伝の文などが外れる。
`corpus.txt` は 3 回渡して重みを上げる。1 回だと Tatoeba の会話文の重みが下がり、「今日給料日！」が「供給料日！」になるなど、くだけた文が弱くなる。

## 作り直し

```
scripts/lm/prepare_corpus.sh /tmp/iroha-corpus
./gradlew :engine:selectWebSentences \
    -Piroha.select=/tmp/iroha-corpus/selected.txt,85000000,/tmp/iroha-corpus/corpus.txt,/tmp/iroha-corpus/web.txt
./gradlew :engine:buildLanguageModel \
    -Piroha.corpus=/tmp/iroha-corpus/corpus.txt,/tmp/iroha-corpus/corpus.txt,/tmp/iroha-corpus/corpus.txt,/tmp/iroha-corpus/selected.txt
```

ABEJA-CC-JA から約 1.4GB を取り出す。`corpus.txt` が上の SHA-256 と同じなら、`iroha.lm` も同じものができる。

作り直したら `:engine:test` の `quality:` の行で精度を比べ、`Converter.LANGUAGE_MODEL_WEIGHT` を見直す。
手元に Microsoft Research IME Corpus があれば、`-Diroha.msImeDir=<展開した場所>` を付けると `msime` の行で新聞記事 6,000 文の精度も比べられる。

## ウェブの文を加えたときの精度

採用したモデル以外は重み 350 での比較。品質回帰テストは mozc の 678 件、MS IME は Microsoft Research IME Corpus の 6,000 文。

| コーパス | モデルの大きさ | 品質回帰テスト | MS IME の完全一致 | MS IME の文字誤り率 |
|---|---|---|---|---|
| `corpus.txt` だけ | 3.3MB | 505 | 1,349 | 5.53% |
| `corpus.txt` ＋ ウェブの文から無作為に 8,540 万字 | 20.8MB | 502 | 1,512 | 5.26% |
| `corpus.txt` ×3 ＋ ウェブの文から無作為に 8,540 万字 | 21.8MB | 508 | 1,521 | 5.18% |
| `corpus.txt` ×3 ＋ ウェブの文 4 億字（3 回以上出た 3-gram だけ） | 38.4MB | 505 | 1,619 | 5.09% |
| `corpus.txt` ＋ `selected.txt` | 20.0MB | 510 | 1,637 | 4.86% |
| `corpus.txt` ×3 ＋ `selected.txt` | 20.8MB | 512 | 1,630 | 4.82% |
| `corpus.txt` ×3 ＋ `selected.txt`、重み 400（採用） | 20.8MB | 512 | 1,641 | 4.80% |

mozc の評価セット（79 件）は、重み 350 で `selected.txt` を使ったモデルが 78 件、ほかは 77 件。採用したモデルは重み 400 で 77 件。
採用したモデルで重みを振ると、300・350・400・450 で品質回帰テストが 515・512・512・510 件、MS IME の完全一致が 1,612・1,630・1,641・1,647 文。
350 以下では「蚊に噛まれる」が「家に噛まれる」になるので 400 にした。選んだ文は、無作為に同じ量を取るより、同じ大きさのまま
どの評価でも良い。ただし `corpus.txt` にはウィキニュースが入っているので、新聞記事の MS IME は選び方の恩恵を受けやすい。

## 試して採らなかったコーパス

- 国会会議録（2015〜2025 年の各月から 300 発言ずつ、約 1,430 万字）を加えると、品質回帰テストが 491 件から 481 件に下がった。
  重みを 250〜450 で振っても 482 件までしか戻らず、混ぜる量を 1/3・1/6・1/12 に減らしても 485・487・489 件と、
  減らすほど戻るだけだった。「今日給料日」が「供給量日」になるなど、議会の硬い言い回しに引っ張られる。
- 青空文庫（[aozorabunko-clean](https://huggingface.co/datasets/globis-university/aozorabunko-clean) の新字新仮名の 10,246 作品、約 1 億 6,400 万字）を
  `corpus.txt` ×3 ＋ ウェブの文 8,540 万字のモデルに加えると、1,500 万字・4,100 万字・全部で品質回帰テストが 508・510・506 件、
  MS IME の完全一致が 1,521 文から 1,487・1,489・1,476 文になった。「藪から棒に」などは直るが、「眼の前」「舟」のように表記が古くなる。
