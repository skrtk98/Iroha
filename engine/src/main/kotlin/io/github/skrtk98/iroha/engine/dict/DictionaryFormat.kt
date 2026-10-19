package io.github.skrtk98.iroha.engine.dict

/**
 * システム辞書ファイル (iroha.dic) の配置。すべてビッグエンディアン。
 *
 * ```
 * header   : magic, version, 各セクションの要素数 (HEADER_SIZE バイト)
 * chars    : u16 × charCount        文字コード表 (CharCodec)
 * pos      : u8 × posCount          品詞 ID ごとの PosClass
 * userPos  : u16 × userPosCount      UserPos ごとの品詞 ID
 * matrix   : rid ごとの行。u32 × dim の行オフセットの後に各行が続く
 * blocks   : u32 × blockCount       BLOCK_SIZE 件ごとの先頭レコードの data 内オフセット
 * data     : レコード列             読みの昇順。読みは前方一致圧縮。
 * ```
 *
 * レコード: prefixLen u8, suffixLen u8, suffix, entriesLength varint, entryCount varint, entry...
 * エントリ: flags u8, lid u16, [rid u16], cost u16, [surfaceLength varint, surface]
 *
 * 連接行列の行 (mozc と同じく最頻値以外だけを持つ):
 * mode u8, bitmap u64 × words, rank u16 × words, value u8 × (bitmap の立っている数)
 * 値はすべて連接コスト / resolution。
 */
internal object DictionaryFormat {
    const val MAGIC = 0x49524844 // "IRHD"
    const val VERSION = 3
    const val HEADER_SIZE = 48
    const val BLOCK_SIZE = 16
    const val RESOLUTION = 64

    const val FLAG_RID_DIFFERS = 0x01
    const val SURFACE_SHIFT = 1
    const val SURFACE_MASK = 0x03
    const val SURFACE_HIRAGANA = 0
    const val SURFACE_KATAKANA = 1
    const val SURFACE_EXPLICIT = 2
}
