"""ABEJA-CC-JA の JSONL から範囲を切り出した断片を、1 行 1 文のテキストにする。
使い方: python3 -I web_corpus.py <出力> <断片>...
断片の先頭と末尾の行は途中で切れているので捨てる。同じ文は最初の 1 回だけ残す。
"""
import hashlib
import json
import re
import sys

JA = re.compile(r'[ぁ-ゖァ-ヺ一-鿿]')
SENTENCE_END = re.compile(r'(?<=[。！？!?])')


def main():
    seen = set()
    docs = kept = chars = 0
    with open(sys.argv[1], 'w', encoding='utf-8') as out:
        for path in sys.argv[2:]:
            with open(path, 'rb') as f:
                lines = f.read().split(b'\n')[1:-1]
            for raw in lines:
                try:
                    content = json.loads(raw)['content']
                except (ValueError, KeyError):
                    continue
                docs += 1
                for line in content.split('\n'):
                    line = line.strip()
                    # 見出し、表、箇条書き、URL を含む行は文になっていないことが多い
                    if not line or line[0] in '#|*-<>[' or 'http' in line:
                        continue
                    for s in SENTENCE_END.split(line):
                        s = s.strip()
                        # 短すぎる文と、かな・漢字の少ない文 (英語、記号、コード) は捨てる
                        if not 8 <= len(s) <= 200 or len(JA.findall(s)) < len(s) * 0.6:
                            continue
                        # ナビゲーションや署名など、ページをまたいで繰り返す定型文を数えすぎないよう 1 回だけ残す
                        h = hashlib.blake2b(s.encode(), digest_size=8).digest()
                        if h in seen:
                            continue
                        seen.add(h)
                        out.write(s + '\n')
                        kept += 1
                        chars += len(s)
    print(f'{docs} docs, {kept} sentences, {chars} chars', file=sys.stderr)


main()
