# FEELING THE WAY — Android ビルド一式

PCなしで、スマホから GitHub Actions でビルドできる構成になっています。
ゲーム本体は `app/src/main/assets/` の HTML と MP3 の2ファイルだけです。

---

## 1. 広告IDの状態

いただいた分はもう入れてあります。

| 種類 | 状態 |
|---|---|
| アプリID | ✅ `ca-app-pub-1657354520055854~9617122214`（Manifest に記入済み） |
| バナー | ✅ `ca-app-pub-1657354520055854/9604784977`（strings.xml に記入済み） |
| インタースティシャル | ⚠️ **未作成**。いまは Google のテストIDのまま |
| リワード | ⚠️ **未作成**。いまは Google のテストIDのまま |

残り2つは AdMob の管理画面（アプリ → 広告ユニット → 広告ユニットを追加）で
「インタースティシャル」と「リワード」を1つずつ作り、
`app/src/main/res/values/strings.xml` の該当行に貼るだけです。
**テストIDのまま公開するとポリシー違反**になるので、公開前に必ず差し替えてください。

### パッケージ名

`app/build.gradle` の `applicationId` はいま `com.okm.feelingtheway` です。
変える場合は `MainActivity.java` の1行目の `package` と、フォルダ名
`app/src/main/java/com/okm/feelingtheway/` も同じ名前に合わせてください。
**Play に一度出すと変更できません。**

---

## 2. スマホだけでビルドする

1. GitHub でリポジトリを作り、このフォルダの中身をそのまま置く
2. **Actions** タブ → `Build Android` → **Run workflow**
3. 5〜8分待つ → 実行結果の **Artifacts** から `feeling-the-way-debug-apk` を落として端末にインストール

これで実機確認ができます。署名は不要です。

### 公開用（署名付き AAB）

署名鍵が必要ですが、これもスマホだけで作れます。

1. **Actions** → `Make signing key` → **Run workflow**
   （alias とパスワードを入れる。パスワードは必ず控える）
2. Artifacts から `release.jks.base64.txt` を開き、**中身の文字列を全部コピー**
3. リポジトリの **Settings → Secrets and variables → Actions** に4つ登録

   | Secret 名 | 中身 |
   |---|---|
   | `KEYSTORE_BASE64` | 2でコピーした文字列 |
   | `KEYSTORE_PASSWORD` | 1で入れたパスワード |
   | `KEY_ALIAS` | 1で入れた alias |
   | `KEY_PASSWORD` | 1で入れたパスワード |

4. Artifacts の鍵ファイルは**ダウンロードして安全な場所に保管し、Artifactは削除**する
   （この鍵を失うとアプリを更新できなくなります）
5. もう一度 `Build Android` を実行すると `feeling-the-way-release` に `.aab` が入ります

`.aab` を Play Console に、`.apk` を Galaxy Store にアップロードします。

---

## 3. 広告がどこで出るか

| 種別 | タイミング | 実装 |
|---|---|---|
| バナー | 常時、画面下部 | ネイティブの `AdView`。WebView の下に別ビューとして置いてあるので、ゲーム画面に重なりません |
| インタースティシャル | ステージ解禁のときだけ（10レベルごと、初回のみ） | プレイ中には一切割り込みません |
| リワード | ヒントが切れた状態でヒントボタンを押したとき | 視聴でヒント+1 |

ページ側は `window.Android` があるかどうかだけを見ています。
ブラウザで開いたときは自動的にプレースホルダに切り替わるので、HTML単体でも普通に遊べます。

やり取りしているのはこれだけです。

```
ページ → アプリ :  Android.setBanner(true/false)
                  Android.showInterstitial('stage')
                  Android.showRewarded('hint')

アプリ → ページ :  window.onAdRewarded('hint')   報酬を付与
                  window.onAdClosed(tag)        広告が閉じた
                  window.onAdFailed(tag)        出せなかった（ゲームは止めずに続行）
```

広告が読み込めなかった場合もゲームは止まりません。ステージ解禁はそのまま進み、
リワードは「AD UNAVAILABLE」と出して閉じます。

---

## 4. ストア提出時のチェック

- **プライバシーポリシーのURLが必須**です（広告を出すため）。GitHub Pages に1枚置けば足ります
- Play Console の **データセーフティ**：広告ID（AAID）の収集を「はい」にする
- **広告が含まれるか** → はい
- **ターゲット年齢**：全年齢向けにする場合、AdMob 側でも「子ども向け」設定の整合を取ること
- Galaxy Store は審査でスクリーンショットの端末指定があります。`store/` の6枚（1080×1920）がそのまま使えます

## 5. 素材

`store/` に入っています。

- `play-icon-512.png` — ストア用アイコン
- `play-feature-1024x500.png` — Play のフィーチャーグラフィック
- `screen-1〜6` — スクリーンショット 1080×1920

アプリ内アイコンは `res/mipmap-*/` に生成済み（通常・丸・アダプティブ）です。

---

## 6. フォントについて

いまは Google Fonts（PT Serif / IBM Plex Mono）をネットから読んでいます。
オフラインや初回起動時は、フォールバックの端末内蔵フォントで表示されます
（見た目はほぼ同じになるよう指定してあります）。

完全に固定したい場合は、2つの woff2 を `assets/` に置いて、HTML の `<link>` を
`@font-face` に書き換えてください。ファイルは合計 100KB 程度増えます。

---

## 7. ファイルの場所

ゲーム本体（ブラウザでもそのまま動く2ファイル）は

```
app/src/main/assets/feeling-the-way.html
app/src/main/assets/feeling-the-way.mp3
```

にあります。この2つを同じフォルダに置けば、ブラウザでも実機のWebViewでも同じように動きます。
HTMLを直したときは、ここを差し替えて再ビルドするだけです。

---

## 8. リポジトリの作り方とプライバシーポリシーの公開

### リポジトリ作成時の設定

| 項目 | 設定 | 理由 |
|---|---|---|
| Repository name | `feeling-the-way`（**小文字・ハイフン**） | Pages のURLがそのまま小文字になり、打ち間違いが起きません |
| Visibility | **Public** | Actions の実行時間が無制限になり、GitHub Pages（無料枠）でポリシーを公開できます |
| Add README | **Off のまま** | このzipに README.md が入っているので、二重になるのを避けます |
| Add .gitignore | **No .gitignore のまま** | 同じく同梱済みです（鍵ファイルを除外する設定が入っています） |
| Add license | **No license のまま** | 商用ゲームなので、権利を保持したままにします |

Public にすると HTML のソースは誰でも読めます。気になる場合は Private でも構いませんが、
その場合は**プライバシーポリシー用に公開リポジトリをもう1つ**作って `docs/index.html` だけを
置いてください（ポリシーのURLは誰でも開ける必要があります）。

なお **鍵ファイル（.jks）とパスワードは絶対にコミットしないでください。**
同梱の `.gitignore` で除外済みで、パスワードは GitHub の Secrets に入れる運用にしてあります。

### プライバシーポリシーを公開する

広告を出すアプリは、ストア審査でプライバシーポリシーのURLが必須です。
`docs/index.html` にそのまま使える日本語・英語併記のページを入れてあります。

1. リポジトリの **Settings → Pages** を開く
2. Source を **Deploy from a branch**
3. Branch を **main**、フォルダを **/docs** にして Save
4. 1〜2分待つと、このURLで公開されます

```
https://<ユーザー名>.github.io/feeling-the-way/
```

このURLを Play Console とGalaxy Storeの「プライバシーポリシー」欄に貼ります。

内容を変えたい場合は `docs/index.html` を編集してください。連絡先は
`okm.ku002@gmail.com` にしてあります。専用アドレスを使うなら2か所（日本語版・英語版）を
差し替えてください。

### 同意画面（EU向け）

Google の User Messaging Platform を組み込んであります。EEA・英国・スイスの利用者には
初回起動時に同意画面が出て、同意が取れるまで広告をリクエストしません。
それ以外の地域では何も表示されず、そのままゲームが始まります。

AdMob の管理画面で **プライバシーとメッセージ → GDPR** のメッセージを1つ作成しておいてください。
作っていないと、EU圏で広告が出ません。
