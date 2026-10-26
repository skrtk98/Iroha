#!/bin/bash
# 言語モデル (engine/lm/iroha.lm) のコーパスを用意する。使い方: prepare_corpus.sh <作業ディレクトリ>
# Tatoeba の日本語の文と日本語版ウィキニュースの記事から、1 行 1 文のテキスト corpus.txt を作る。
# どちらも配布元が最新版しか置かないため、取得した日によって中身が変わる。
set -euo pipefail
dir="$1"
here="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$dir"
curl -fsSL -o "$dir/jpn_sentences.tsv.bz2" https://downloads.tatoeba.org/exports/per_language/jpn/jpn_sentences.tsv.bz2
curl -fsSL -o "$dir/jawikinews.xml.bz2" https://dumps.wikimedia.org/jawikinews/latest/jawikinews-latest-pages-articles.xml.bz2
bunzip2 -c "$dir/jpn_sentences.tsv.bz2" | cut -f3 > "$dir/corpus.txt"
python3 -I "$here/wikitext.py" "$dir/jawikinews.xml.bz2" "$dir/wikinews.txt"
cat "$dir/wikinews.txt" >> "$dir/corpus.txt"
sha256sum "$dir"/*.bz2 "$dir/corpus.txt"
echo "次を実行: ./gradlew :engine:buildLanguageModel -Piroha.corpus=$dir/corpus.txt"
