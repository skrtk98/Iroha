"""Extracts Japanese sentences from a MediaWiki XML dump (main namespace), one per line."""
import bz2, re, sys, xml.etree.ElementTree as ET

src, dst = sys.argv[1], sys.argv[2]
ns = None
tpl = re.compile(r'\{\{[^{}]*\}\}')
tbl = re.compile(r'\{\|.*?\|\}', re.S)
ref = re.compile(r'<ref[^>]*/>|<ref.*?</ref>', re.S)
tag = re.compile(r'<[^>]+>')
link = re.compile(r'\[\[(?:[^\[\]|]*\|)?([^\[\]]*)\]\]')
ext = re.compile(r'\[https?://\S+\s*([^\]]*)\]')
jp = re.compile(r'[぀-ヿ一-鿿]')
count = 0
with bz2.open(src, 'rt', encoding='utf-8') as f, open(dst, 'w', encoding='utf-8') as out:
    for _, el in ET.iterparse(f):
        if not el.tag.endswith('}page'):
            continue
        q = el.tag[:-4]
        if el.findtext(q + 'ns') != '0' or el.find(q + 'redirect') is not None:
            el.clear(); continue
        text = el.findtext(f'{q}revision/{q}text') or ''
        el.clear()
        text = ref.sub('', text)
        text = re.sub(r'<!--.*?-->', '', text, flags=re.S)
        for _ in range(3):
            text = tpl.sub('', text)
        text = tbl.sub('', text)
        text = re.sub(r'\[\[(?:Category|カテゴリ|File|ファイル|Image|画像):[^\]]*\]\]', '', text)
        text = link.sub(r'\1', text)
        text = ext.sub(r'\1', text)
        text = tag.sub('', text)
        text = text.replace("'''", '').replace("''", '')
        for line in text.split('\n'):
            line = line.strip()
            if not line or line[0] in '=*#:;|!{}' or len(jp.findall(line)) < 10:
                continue
            for sentence in re.findall(r'[^。！？]+[。！？]?', line):
                sentence = sentence.strip()
                if len(jp.findall(sentence)) >= 5:
                    out.write(sentence + '\n'); count += 1
print(count)
