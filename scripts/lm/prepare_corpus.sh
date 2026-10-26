#!/bin/bash
# 言語モデル (engine/lm/iroha.lm) のコーパスを用意する。使い方: prepare_corpus.sh <作業ディレクトリ>
# Tatoeba の日本語の文と日本語版ウィキニュースの記事から、1 行 1 文のテキスト corpus.txt を作る。
# どちらも配布元が最新版しか置かないため、取得した日によって中身が変わる。
# ABEJA-CC-JA (Common Crawl から集めた日本語のウェブ文書) からは、決まった 10 か所を 30MB ずつ取り出して web.txt を作る。
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
parts=(0:3000000000 10:23000000000 12:21000000000 14:19000000000 16:17000000000
       18:15000000000 2:31000000000 4:29000000000 6:27000000000 8:25000000000)
files=()
for part in "${parts[@]}"; do
    n="${part%%:*}"
    offset="${part#*:}"
    curl -fsSL -r "$offset-$((offset + 30000000))" -o "$dir/web$n.jsonl.part" \
        "https://abeja-cc-ja.s3.amazonaws.com/common_crawl_$n.jsonl"
    files+=("$dir/web$n.jsonl.part")
done
python3 -I "$here/web_corpus.py" "$dir/web.txt" "${files[@]}"
sha256sum "$dir"/*.bz2 "$dir"/*.jsonl.part "$dir/corpus.txt" "$dir/web.txt"
# ABEJA-CC-JA は版を指定して取れないので、モデルを作ったときと同じ web.txt になったかを確かめる
echo "240bb70b794e53c2bb9cffacf2bf36c3228b77514dc990e42c25e74e1f7b208a  $dir/web.txt" | sha256sum -c -
# 書き言葉の多いもとのコーパスを 3 回渡して、ウェブ文書に引っ張られすぎないようにする
echo "次を実行: ./gradlew :engine:buildLanguageModel -Piroha.corpus=$dir/corpus.txt,$dir/corpus.txt,$dir/corpus.txt,$dir/web.txt"
