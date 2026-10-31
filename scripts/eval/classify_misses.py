#!/usr/bin/env python3
"""EvaluationSetTest の外れを、Sudachi で正規化すると正解と一致するものとそれ以外に分けて数える。

  :engine:test に -Diroha.evalDir=… -Diroha.evalMisses=true を付けて走らせた後、テスト結果の XML を渡す。
  classify_misses.py engine/build/test-results/test/TEST-*EvaluationSetTest.xml

出力と正解を Sudachi で語に分け、正規化した表記 (綺麗/きれい、色々/いろいろ など) の並びが同じものを「正規化一致」とする。
多くは表記だけの差だが、同じ読みで意味の違う語 (引かれる/惹かれる、焼く/妬く) や活用の違い (行け/行く) も一致してしまう。
逆に Sudachi が正規化しない表記の差 (無くなる/なくなる など) は「それ以外」に入る。どちらも目で確かめてから解釈すること。
"""
import collections
import html
import random
import re
import sys

from sudachipy import Dictionary, SplitMode

MISS = re.compile(r"eval miss (\w+): (.*) \(want (.*)\)$")


def main(path: str) -> None:
    tokenizer = Dictionary(dict="core").create()

    def normalized(s: str) -> list[str]:
        return [m.normalized_form() for m in tokenizer.tokenize(s, SplitMode.C)]

    groups = collections.defaultdict(lambda: ([], []))
    with open(path, encoding="utf-8") as f:
        for line in html.unescape(f.read()).splitlines():
            m = MISS.search(line.strip())
            if m:
                label, output, reference = m.groups()
                variant = normalized(output) == normalized(reference)
                groups[label][0 if variant else 1].append((output, reference))
    random.seed(0)
    for label, (variants, others) in sorted(groups.items()):
        print(f"{label}: 正規化一致 {len(variants)}、それ以外 {len(others)}")
        for output, reference in random.sample(others, min(20, len(others))):
            print(f"  {output}  (want {reference})")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
