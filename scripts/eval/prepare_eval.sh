#!/bin/bash
# 変換精度の評価データ (手元だけで使う) を作る。使い方: prepare_eval.sh <作業ディレクトリ>
# <作業ディレクトリ>/sets/ に chat.tsv と ajimee.tsv ができる。:engine:test に -Diroha.evalDir=<作業ディレクトリ>/sets を付けて測る。
#
#   chat.tsv   RealPersonaChat v1.0.0 (CC BY-SA 4.0) の雑談の発話から選んだ 3,000 文。読みは Sudachi で付ける
#   ajimee.tsv AJIMEE-Bench (CC BY-SA 3.0、日本語 Wikipedia 入力誤りデータセット由来) の 200 件
#
# どちらも言語モデルの学習にも重みの調整にも使っていない。評価データはリポジトリにも APK にも入れない。
# 読み付けには sudachipy 0.7.0 と sudachidict_core 20261015 を使う (版が違うと読みが変わりうる)。例えば:
#   uv venv <venv> && VIRTUAL_ENV=<venv> uv pip install sudachipy==0.7.0 sudachidict_core==20261015
#   PYTHON=<venv>/bin/python scripts/eval/prepare_eval.sh <作業ディレクトリ>
set -euo pipefail
dir="$1"
here="$(cd "$(dirname "$0")" && pwd)"
python="${PYTHON:-python3}"
mkdir -p "$dir"
curl -fsSL -o "$dir/real-persona-chat-1.0.0.zip" https://github.com/nu-dialogue/real-persona-chat/archive/refs/tags/v1.0.0.zip
curl -fsSL -o "$dir/ajimee.json" \
    https://raw.githubusercontent.com/azooKey/AJIMEE-Bench/401666cd56d1a570c2021798b64b6da4396bfd45/JWTD_v2/v1/evaluation_items.json
sha256sum -c - <<EOF
9b831fe9d117f8ab5b1ce23c122393a039e468eeeaf4e7fd0404d8873ac87140  $dir/real-persona-chat-1.0.0.zip
e9eb668fd6aa14b1e26436f429b5550108af0a1dfd443b8cea0bcb3ab3028fca  $dir/ajimee.json
EOF
rm -rf "$dir/real-persona-chat"
python3 -I -m zipfile -e "$dir/real-persona-chat-1.0.0.zip" "$dir/real-persona-chat"
# いったん sets.new に作り、取得元と同じ評価データになったと確かめてから sets に置き換える。
# 読みの付け方が変わって違うものができたときは、前の sets を残して止まる
rm -rf "$dir/sets.new"
mkdir "$dir/sets.new"
"$python" -I -W ignore "$here/eval_sets.py" chat \
    "$dir/real-persona-chat/real-persona-chat-1.0.0/real_persona_chat/dialogues" "$dir/sets.new/chat.tsv"
"$python" -I "$here/eval_sets.py" ajimee "$dir/ajimee.json" "$dir/sets.new/ajimee.tsv"
sha256sum -c - <<EOF
3f1ccaafb66948ca100f2148ae232ed856086be08f75666e297edd95d6b492dd  $dir/sets.new/chat.tsv
1ec5c5d4cff4bbb64aa32ab1b47c5542f07e34696b7fef15f223b4620c102c51  $dir/sets.new/ajimee.tsv
EOF
rm -rf "$dir/sets"
mv "$dir/sets.new" "$dir/sets"
