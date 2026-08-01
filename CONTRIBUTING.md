# コントリビューションガイド

PCR ツール 日本語版へのコントリビューションをご検討いただき、ありがとうございます。

このリポジトリは [wthee/pcr-tool](https://github.com/wthee/pcr-tool) の日本語版Forkです。原版への追従性を保つため、日本語版固有の変更範囲とSecretの取り扱いに注意してください。

## 開発ブランチ

- 日本語版の基準ブランチ：`feature/japanese-localization`
- 原版の追跡先：`upstream/master-compose`
- 機能追加や翻訳修正は、`feature/japanese-localization` から作業ブランチを作成してください。
- Pull Requestのマージ先には `feature/japanese-localization` を指定してください。

```powershell
git switch feature/japanese-localization
git pull origin feature/japanese-localization
git switch -c fix/your-change
```

## 開発環境

- JDK 21
- Android SDK Platform 36
- Android SDK Build Tools 36.0.0
- Gradle Wrapper（リポジトリに同梱）

`app/src/main/java/cn/wthee/pcrtool/utils/PrivateConfig.kt` はGit管理外です。ローカルビルド時に次の内容で作成してください。

```kotlin
package cn.wthee.pcrtool.utils

object PrivateConfig {
    const val BUGLY_KEY = ""
}
```

Debug APKをビルドします。

```powershell
.\gradlew.bat assembleOfficialDebug
```

## 日本語リソース

日本語文字列は次のファイルで管理します。

```text
app/src/main/res/values-ja/strings.xml
```

翻訳時の注意事項：

- 原版の `values/strings.xml` と同じ `name` を使用してください。
- `%s`、`%d`、`%1$s` などの書式指定子は削除・変更しないでください。
- `\n`、HTMLタグ、引用符、エスケープを維持してください。
- プリンセスコネクト！Re:Diveの日本版ゲーム内表記を優先してください。
- 直訳が不自然な場合は、画面の用途に合う短く自然な日本語にしてください。
- 新しい原版文字列に日本語リソースがない場合、中国語へフォールバックします。

## アプリ識別子を変更しない

日本語版では次の構成を意図的に使用しています。

```kotlin
namespace = "cn.wthee.pcrtool"
applicationId = "jp.nono2359.pcrtool"
```

- `namespace` はソースコードとの互換性のため維持します。
- `applicationId` は原版との共存と日本語版の更新経路を分離するために使用します。
- 大規模なパッケージ移行を伴わない変更では、この2つを変更しないでください。

## 原版APIとの互換性

日本語版の表示バージョンは `4.0.1-jp.2` のような形式です。一方、原版APIへ送信する `app-version` ヘッダーでは `-jp.N` を除いた `4.0.1` を使用します。

この処理を削除すると、お知らせやオンライン機能が失敗する可能性があります。

## バージョンとRelease

日本語版のバージョン規則：

```text
原版 4.0.1 / 日本語版リビジョン2
versionName = 4.0.1-jp.2
versionCode = 40102
tag         = v4.0.1-jp.2
```

- 通常のコミットやブランチPushではReleaseされません。
- `v*` タグのPushを契機にGitHub Actionsが署名済みAPKを公開します。
- Releaseタグの作成はメンテナーが行います。
- 原版が `4.0.2` へ上がった場合、日本語版は `4.0.2-jp.1` から開始します。
- 公開済みタグの付け替えや上書きは行わず、新しいバージョンを作成してください。

## Secretと署名鍵の取り扱い

次の情報は、理由を問わずコミット、Pull Request、Issue、ログ、スクリーンショットへ含めないでください。

- Release署名鍵：`*.jks`、`*.keystore`、`*.p12`
- キーストアのBase64データ
- ストアパスワード、キーエイリアス、キーパスワード
- `release-signing-credentials.txt`
- 実値を含む `PrivateConfig.kt`
- `local.properties`
- APIキー、アクセストークン、個人用アクセストークン

署名ファイルと認証情報はリポジトリ外へ保存してください。この開発環境では、例として次の場所を使用しています。

```text
C:\androld\pcr-tool-signing\
```

GitHub ActionsのReleaseビルドには、リポジトリの `Settings → Secrets and variables → Actions` で次のRepository Secretsを登録します。

```text
PCR_RELEASE_KEYSTORE_BASE64
PCR_RELEASE_STORE_PASSWORD
PCR_RELEASE_KEY_ALIAS
PCR_RELEASE_KEY_PASSWORD
```

注意事項：

- Secretの値だけを登録し、説明文やメモを値へ含めないでください。
- Secretの値を確認目的でターミナルへ表示しないでください。
- Pull RequestへSecretを貼らないでください。
- ForkからのPull RequestへRelease用Secretを渡すワークフローを作らないでください。
- GitHub ActionsのログへSecretを出力するコマンドを追加しないでください。

Secretを誤って公開した場合、ファイルやコミットを削除するだけでは不十分です。直ちに該当するキー・パスワード・トークンを失効または再発行し、その後にGit履歴から削除してください。

## 原版の更新を取り込む

メンテナーは原版の変更を日本語版へ取り込む前に、作業ツリーがクリーンであることを確認してください。

```powershell
git switch feature/japanese-localization
git fetch upstream
git merge upstream/master-compose
```

確認項目：

- `values/strings.xml` に追加・変更された文字列
- Gradle、Android SDK、JDKの要件
- APIのURL、ヘッダー、レスポンスモデル
- applicationIdや更新URLに関係する変更
- Release署名設定やGitHub Actionsとの競合

競合解消後はDebug版と署名Release版の両方を確認してください。

## 動作確認

最低限、次を確認してください。

- Debug APKのビルドが成功する
- 日本語表示にXMLエラーや書式指定子エラーがない
- キャラクター・装備・専用装備の画面を開ける
- お知らせなどのオンラインデータを取得できる
- 通知メニューとバージョン表示を確認できる
- Release関連の変更では、署名Release APKでも同じ機能を確認する

## Pull Request

Pull Requestには次を記載してください。

- 変更目的
- 変更した画面・機能
- 動作確認方法と結果
- UI変更がある場合はスクリーンショット
- 原版のどの変更に対応するか（該当する場合）

翻訳だけの変更でも、対象画面と確認結果を記載してください。
