# 配布と署名

## 配布経路

Google Play、F-Droid、直接配布（GitHub Releases）の 3 つで配る方針。2026-10-27 時点で出しているのは直接配布だけ。

## 署名の方針：3 つの経路を同じ署名にそろえる

Android は署名の違うアプリによる上書き更新を受け付けない。経路ごとに署名が違うと、利用者が経路を乗り換えるには
アンインストールが要り、ユーザー辞書と学習結果が消える。そこでアプリ署名鍵を 1 本にし、全経路の APK の署名をそろえる。

| 経路 | 署名のしかた |
|---|---|
| 直接配布 | アプリ署名鍵（`app`）で署名した APK を GitHub Releases に置く。リリースノートに APK と証明書の SHA-256 を載せる |
| Google Play | Play App Signing で「自分のアプリ署名鍵を使う」を選び、最初にこの鍵を PEPK ツールで暗号化して預ける。普段の提出はアップロード鍵（`upload`）で署名した AAB で行う |
| F-Droid | 再現可能ビルド。F-Droid が同じソースからビルドし、中身が私たちの APK と一致すれば、私たちが署名した APK をそのまま配る。メタデータに `Binaries:` と `AllowedAPKSigningKeys:` を書く |

- Play で「自分の鍵を使う」かどうかは、Play に最初に登録するときにしか選べない。
- 代わりの案として、F-Droid には F-Droid 自身の鍵で署名させる方法もある。手軽だが、F-Droid の利用者は他の経路に移れない
  （ユーザー辞書は書き出し機能で持ち出せる）。**どちらにするかは未決**（[roadmap.md](roadmap.md)）。

アプリ署名鍵の証明書 SHA-256（公開してよい値）:

```
73:0B:B1:3F:31:F0:02:BD:F8:42:F4:86:2F:60:8A:8E:5F:27:E0:4B:6F:CC:49:1D:98:17:4E:28:EC:E4:E6:FA
```

## 鍵の扱い

- 鍵は 2 本。設定は `signing.properties`（書式は [signing.properties.example](../signing.properties.example)）に書き、
  鍵の作成（`scripts/create-signing-key.sh`）と release の署名の両方がこのファイルを読む。
  - `app`：アプリ署名鍵。直接配布と F-Droid の APK に署名し、Play にも預ける。なくすと直接配布と F-Droid の更新が二度とできない。
  - `upload`：アップロード鍵。Play に出す AAB に署名する。なくしても Google に申請して差し替えられる。
- `signing.properties` と鍵ファイル（`*.p12`、`*.jks`、`*.keystore`）は `.gitignore` 済み。リポジトリに入れない。
- 2 本の鍵には別々のパスワードを付ける。アップロード鍵は CI などに渡すことがあり、同じだとそこからアプリ署名鍵のパスワードも分かる。
- 鍵ファイルと `signing.properties` は 2 か所以上にバックアップする（パスワードマネージャーと、オフラインの媒体など）。
  鍵の置き場所のディレクトリは本人だけが読める権限にする。
- 作成スクリプトの安全策：`signing.properties` を他のユーザーが読めるとき、鍵ストアがすでにあるとき、項目が足りないとき、
  パスワードが 6 文字未満のときは止まる。パスワードは環境変数で keytool に渡し、プロセス一覧に出さない。
- `signing.properties` が無ければ release は未署名で出力する（F-Droid のビルドサーバー向け）。デバッグ鍵には頼らない。

```bash
./gradlew clean :app:assembleRelease                           # 直接配布・F-Droid 用の APK（app で署名）
./gradlew clean :app:bundleRelease -Piroha.signing.key=upload  # Play 用の AAB（upload で署名）
```

## 再現可能ビルド

F-Droid で私たちの署名を使うには、同じソースから同じ APK ができる必要がある。

- APK に Google の依存関係情報ブロックを入れない（`app/build.gradle.kts` の `dependenciesInfo`）。Google が署名したブロックで、
  F-Droid は中身を確かめられない。Play に出す AAB には残す。
- mozc から取得するファイル（2026-10-27 時点で 35 個、うち 2 個はテスト用）の SHA-256 を `MozcImporter.FILE_SHA256` に固定している。書き換えられていたらビルドが止まる。
- 言語モデルはスクリプトから作り直せる（[engine/lm/README.md](../engine/lm/README.md)）。
- **差分ビルドでは APK が一致しない**（R8 の出力が変わる）。release は必ず `clean` から、リリースはタグからまっさらに clone してビルドする。
- 確認済み（2026-10-27）：パスの違う 2 か所に clone し、Gradle のキャッシュを使わずにビルドした APK はバイト単位で一致した。
  Baseline Profile のファイル（`baseline.prof` / `.profm`）も一致した。
- v0.2.0 では、版を上げるコミットの前の作業ツリーでビルドしたため、APK の `META-INF/version-control-info.textproto` に
  別のコミットが入り、タグからの再ビルドと一致しなかった。タグからビルドし直した APK に差し替えた（差し替え前のダウンロード数は 0）。

## F-Droid で残っている作業

- fdroidserver と同じ環境（Docker イメージ）でビルドし、APK が一致するか確かめる。JDK や build-tools の版で変わることがある。
- fdroiddata に出すメタデータ（レシピ、fastlane 形式の説明文と画面写真）を作る。
- 辞書の元データをビルド中に GitHub から取得していることと、生成済みの言語モデル（`engine/lm/iroha.lm`）をリポジトリに置いていることが、
  審査で問題にならないか確かめる。問題になるなら、mozc をサブモジュールにするなどを検討する。
- 署名の方式を決める（上記）。
