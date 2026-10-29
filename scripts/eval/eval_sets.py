#!/usr/bin/env python3
"""手元だけで使う評価データ (読みと正解の組) を作る。

  chat   RealPersonaChat の雑談の発話から文を選び、Sudachi で読みを付ける
  ajimee AJIMEE-Bench (日本語 Wikipedia 入力誤りデータセット由来) の読みと正解をそのまま並べる

出力は 1 行 1 件の TSV で、1 列目が読み (ひらがな)、2 列目以降が正解 (複数あればどれでも正解)。
Iroha の辞書とは別の解析器で読みを付けるのは、評価が Iroha の辞書の読み方に甘くならないようにするため。

使い方:
  eval_sets.py chat <real_persona_chat/dialogues> <出力>
  eval_sets.py ajimee <evaluation_items.json> <出力>
"""
import hashlib
import json
import os
import re
import sys
from pathlib import Path

SENTENCES = 3000
# 文の終わり。区切った文は終わりの記号を含む
SENTENCE_END = re.compile(r"(?<=[。！？])")
# 読みと正解にそのまま残す記号。これ以外の記号 (顔文字、括弧、英数字など) を含む文は使わない
PUNCTUATION = set("、。！？")
KANA = re.compile(r"^[ぁ-ゖァ-ヺー]+$")
KANJI = re.compile(r"[一-鿿々〆]")
ALLOWED = re.compile(r"^[ぁ-ゖァ-ヺー一-鿿々〆、。！？]+$")
# Sudachi の読みが雑談でふつうに打つ読みと違う語。「私」は「わたくし」と読まれる
READINGS = {"私": "わたし", "日本": "にほん", "明日": "あした", "餃子": "ぎょうざ", "金平糖": "こんぺいとう"}


def hiragana(s: str) -> str:
    return "".join(chr(ord(c) - 0x60) if "ァ" <= c <= "ヶ" else c for c in s)


def reading_of(tokenizer, mode, sentence: str) -> str | None:
    parts = []
    for m in tokenizer.tokenize(sentence, mode):
        surface = m.surface()
        if all(c in PUNCTUATION for c in surface):
            parts.append(surface)
        elif surface in READINGS:
            parts.append(READINGS[surface])
        elif KANA.match(surface):
            # かなの語は書いたとおりに打つ (助詞の「は」「を」も、長音もそのまま)
            parts.append(hiragana(surface))
        else:
            reading = m.reading_form()
            if not KANA.match(reading):
                return None  # 読みの分からない語を含む文は使わない
            parts.append(hiragana(reading))
    return "".join(parts)


def chat(dialogues: Path, output: Path) -> None:
    from sudachipy import Dictionary, SplitMode

    tokenizer = Dictionary(dict="core").create()
    sentences = set()
    for path in sorted(dialogues.glob("*.json")):
        with path.open(encoding="utf-8") as f:
            dialogue = json.load(f)
        for utterance in dialogue["utterances"]:
            for s in SENTENCE_END.split(utterance["text"].strip()):
                s = s.strip()
                if 6 <= len(s) <= 40 and ALLOWED.match(s) and KANJI.search(s):
                    sentences.add(s)
    # 文の指紋の順に並べて先頭から取る。乱数を使わず、何度作っても同じ文になる
    ordered = sorted(sentences, key=lambda s: hashlib.blake2b(s.encode(), digest_size=8).digest())
    rows = []
    for s in ordered:
        reading = reading_of(tokenizer, SplitMode.C, s)
        if reading is not None:
            rows.append((reading, s))
            if len(rows) == SENTENCES:
                break
    if len(rows) < SENTENCES:
        sys.exit(f"only {len(rows)} sentences")
    write(output, [[r, s] for r, s in rows])


def ajimee(items: Path, output: Path) -> None:
    with items.open(encoding="utf-8") as f:
        data = json.load(f)
    rows = [[hiragana(item["input"]), *dict.fromkeys(item["expected_output"])] for item in data]
    for row in rows:
        if any("\t" in c or "\n" in c or not c for c in row):
            sys.exit(f"bad item: {row}")
    write(output, rows)


def write(output: Path, rows: list[list[str]]) -> None:
    temp = output.with_name(output.name + ".tmp")
    with temp.open("w", encoding="utf-8") as f:
        for row in rows:
            f.write("\t".join(row) + "\n")
    os.replace(temp, output)
    print(f"{len(rows)} items -> {output}")


if __name__ == "__main__":
    if len(sys.argv) != 4 or sys.argv[1] not in ("chat", "ajimee"):
        sys.exit(__doc__)
    {"chat": chat, "ajimee": ajimee}[sys.argv[1]](Path(sys.argv[2]), Path(sys.argv[3]))
