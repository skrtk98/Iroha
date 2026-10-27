#!/bin/bash
# 言語モデル (engine/lm/iroha.lm) のコーパスを用意する。使い方: prepare_corpus.sh <作業ディレクトリ>
# Tatoeba の日本語の文と日本語版ウィキニュースの記事から、1 行 1 文のテキスト corpus.txt を作る。
# どちらも配布元が最新版しか置かないため、取得した日によって中身が変わる。
# ABEJA-CC-JA (Common Crawl から集めた日本語のウェブ文書) からは、決まった 48 か所を 30MB ずつ (計約 1.4GB) 取り出して
# web.txt を作る。言語モデルには、その中から書き言葉に近い文を :engine:selectWebSentences で選んで使う。
set -euo pipefail
dir="$1"
here="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$dir"
curl -fsSL -o "$dir/jpn_sentences.tsv.bz2" https://downloads.tatoeba.org/exports/per_language/jpn/jpn_sentences.tsv.bz2
curl -fsSL -o "$dir/jawikinews.xml.bz2" https://dumps.wikimedia.org/jawikinews/latest/jawikinews-latest-pages-articles.xml.bz2
bunzip2 -c "$dir/jpn_sentences.tsv.bz2" | cut -f3 > "$dir/corpus.txt"
python3 -I "$here/wikitext.py" "$dir/jawikinews.xml.bz2" "$dir/wikinews.txt"
cat "$dir/wikinews.txt" >> "$dir/corpus.txt"
# ファイル番号とバイト位置。この順に処理する (重なった文は先に出たほうを残す)
parts=(0:3000000000 0:19500000000 0:36500000000 1:17500000000 1:50500000000 2:31000000000
       2:31500000000 2:48500000000 3:12500000000 3:29500000000 4:10500000000 4:29000000000
       4:43500000000 5:24500000000 5:41500000000 6:5500000000 6:22500000000 6:27000000000
       7:3500000000 7:36500000000 8:17500000000 8:25000000000 8:34500000000 9:15500000000
       9:48500000000 10:23000000000 10:29500000000 10:46500000000 11:10500000000 11:27500000000
       12:8500000000 12:21000000000 12:41500000000 13:22500000000 13:39500000000 14:3500000000
       14:19000000000 14:20500000000 15:34500000000 15:51500000000 16:15500000000 16:17000000000
       16:32500000000 17:13500000000 17:46500000000 18:15000000000 18:17500000000 18:34500000000)
files=()
for part in "${parts[@]}"; do
    n="${part%%:*}"
    offset="${part#*:}"
    curl -fsSL -r "$offset-$((offset + 30000000))" -o "$dir/web$n-$offset.jsonl.part" \
        "https://abeja-cc-ja.s3.amazonaws.com/common_crawl_$n.jsonl"
    files+=("$dir/web$n-$offset.jsonl.part")
done
python3 -I "$here/web_corpus.py" "$dir/web.txt" "${files[@]}"
sha256sum "$dir"/*.bz2 "$dir/corpus.txt" "$dir/web.txt"
# ABEJA-CC-JA は版を指定して取れないので、モデルを作ったときと同じ web.txt になったかを確かめる
echo "d1d1bce4e7a6d5557420579915c9b2f8b707645ef8de06d9bec8b8dd454f26df  $dir/web.txt" | sha256sum -c -
echo "次を実行:"
echo "./gradlew :engine:selectWebSentences -Piroha.select=$dir/selected.txt,85000000,$dir/corpus.txt,$dir/web.txt"
echo "./gradlew :engine:buildLanguageModel -Piroha.corpus=$dir/corpus.txt,$dir/corpus.txt,$dir/corpus.txt,$dir/selected.txt"
