# リリース手順

v0.2.0 で確立し、v0.3.0 で補足した手順。

1. `dev/release-X.Y.Z` で `app/build.gradle.kts` の `versionCode` と `versionName` を上げ、`chore: bump version to X.Y.Z` でコミットする。
   テストと `lintRelease` を通す。
   - 版の決め方：不具合の修正と小さな改善はパッチ（0.1.x）、変換の中身が大きく変わるものはマイナー（0.2.0、0.3.0）。
2. `--no-ff` で `dev/release-X.Y.Z` → `dev/main` → `test/release` → `main` とマージする
   （`main` へのメッセージは「Merge dev/main into main for vX.Y.Z」）。`git tag -a vX.Y.Z -m "Iroha X.Y.Z"`。
3. **APK はタグからまっさらに clone してビルドする。** 作業ツリーではビルドしない（[distribution.md](distribution.md#再現可能ビルド)）。
   - `git clone --branch vX.Y.Z <リポジトリ> <作業用の場所>` し、`local.properties` だけコピーする。
   - `./gradlew :app:assembleRelease -Piroha.signing.file=<signing.properties の絶対パス>`
   - `apksigner verify --print-certs` で証明書の SHA-256 が `73:0B:B1:3F:…:E6:FA` であること、
     APK の `META-INF/version-control-info.textproto` の revision がタグのコミットであることを確かめる。
4. `git push origin main vX.Y.Z`（push するのは `main` とタグだけ）。
5. **GitHub Release を作る。** タグの push だけではダウンロードできない。
   `gh release create vX.Y.Z iroha-X.Y.Z.apk --title "Iroha X.Y.Z" --notes-file <ノート> --verify-tag`
   公開後にダウンロードし直し、SHA-256 がノートの値と一致することを確かめる。

## リリースノートの書式

過去のリリースに合わせる。

- 1 行目：どんな更新か、前の版から上書きで更新できるか、APK の大きさが大きく変わったか。
- 見出し：「変換の改善」「キーボード」「改善」「不具合の修正」「速さ」など、あるものだけ。
- 最後に「検証用」として APK の SHA-256 と署名証明書の SHA-256（「これまでと同じ」と添える）。
- 変換の例を書くときは、**出荷するモデルと重みで実際にそうなることを確かめる**。v0.3.0 では「ローマ字文を書く」を例にしかけたが、
  実際には外れていたので「蚊に噛まれる」（テストで確かめている例）に差し替えた。
- 利用者の見た目が変わるもの（例：数字の段をオンにしていた人の日英の面が低くなる）は書いておく。

## 実機での計測

変換の速さは `app/src/androidTest/.../data/ConversionBenchmark.kt` で測る。打鍵ごとの予測変換を、読みを 1 文字ずつ延ばしながら、
言語モデルのあり・なしを交互に測り、中央値・遅い側 5%・最大と、モデルの読み込み時間を `IrohaBench` のタグでログに出す。

- **`connectedAndroidTest` は使わない。** 終わった後にアプリをアンインストールするので、端末のユーザー辞書と学習結果が消える。
- 代わりに、debug の APK と androidTest の APK を `adb install -r` で入れ、次で走らせる。

  ```bash
  adb shell am instrument -w -e class io.github.skrtk98.iroha.data.ConversionBenchmark \
      io.github.skrtk98.iroha.test/androidx.test.runner.AndroidJUnitRunner
  adb logcat -s IrohaBench
  ```

- 出荷版に近い数字を取るときは、一時的に debug の `isDebuggable` を false にして測り、終わったら戻す（コミットしない）。
- 端末にリリース版が入っていると、署名が違うので debug 版で上書きできない。debug 版を入れるにはアンインストールが要り、データが消える。

### 計測の記録

| 時点 | 端末 | 1 打鍵の中央値 / 遅い側 5% | 33 文字の文 | モデルの読み込み |
|---|---|---|---|---|
| 0.2.0 前（言語モデル 3.3MB、高速化前） | HUAWEI nova（Android 7.0、Snapdragon 625） | 17.8 / 74ms | 92ms | — |
| 0.2.0 前（高速化後） | 同上 | 11.4 / 42ms | 46ms | 67ms |
| 0.2.0 前（高速化後） | Galaxy A21 SCV49（Android 11、Exynos 7884B） | 6.3〜7.4 / 22〜28ms | 28〜35ms | 51〜140ms |
| 0.3.0（言語モデル 20.8MB） | Galaxy A21 SCV49 | 8.4 / 33.4ms | 42ms | 181ms（0.2.0 のモデルは 48ms） |
| 0.3.0 の後（索引をファイルに持たせた、21.4MB） | 同上 | 8.1〜8.6 / 32.7〜33.4ms | 39〜42ms | 10.6〜11.5ms（同じ日の変更前は 177〜181ms） |

- 言語モデルなしでは、nova で中央値 6.5ms、SCV49 で 3.3〜3.9ms。
- 索引をファイルに持たせた後の値は 4 回の幅。JIT 前の最初の変換（約 150ms）は変更の前後で変わらない。
  APK を入れた直後に続けて測っているので、モデルはページキャッシュに載っていると考えられる。端末を再起動した直後の値は測っていない。
- 高速化の中身：言語モデルのコストは 0 以上なので、モデルを引く前にすでに最良に負けている経路は引かずに捨てる。結果は変わらない。
  負の重みやコストを持つモデルは読み込み時に拒む。
  モデルの表を読み込み時にメモリーへ写す方法（1 割しか速くならない）と、前の語をコスト順に並べる方法（効果なし）は見送った。
- nova は Iroha の対象（Android 8.0 以上）より古いので、測るときだけ一時的に `minSdk` を下げた（コミットしていない）。

### 起動直後の 1 回目（Baseline Profile）

IME を開いた直後の 1 回目の変換は、コードが最適化される前なので遅い（SCV49 のインストール直後で 593ms、2 回目の起動で 184ms）。
`app/src/main/baseline-prof.txt` に変換エンジンと IME 本体のメソッドの規則を書き、Baseline Profile で縮めた。
アンインストールしてから入れ直し、毎回まっさらな状態で SCV49 で測った結果：

| | 1 回目の変換 | モデルの読み込み |
|---|---|---|
| Profile なし | 108〜110ms | 約 46ms |
| ライブラリが元から持つ Profile だけ | 110〜122ms | 約 45ms |
| 規則を追加（エンジンと IME に絞った版） | 26.5〜26.8ms | 約 22ms |

- 最初はアプリ全体（2,005 メソッド）を対象にしたが、使う機会の少ない設定画面とライセンス画面を外して 1,665 メソッドに絞った。値は変わらなかった。
- Play 以外から入れた場合、インストール直後には効かない。ProfileInstaller が Profile を書き込んだ後、端末が充電中などに行う
  バックグラウンドの最適化で効く。
