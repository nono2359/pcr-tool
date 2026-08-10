# PCR ツール 日本語版

「プリンセスコネクト！Re:Dive」の各種データを閲覧・検索するAndroidアプリ、[wthee/pcr-tool](https://github.com/wthee/pcr-tool) の非公式日本語版です。

原版への敬意とライセンスを維持しながら、日本語リソース、専用のアプリID、独立した更新機能を追加しています。

## ダウンロード

最新版は [GitHub Releases](https://github.com/nono2359/pcr-tool/releases/latest) からダウンロードできます。

1. `app-official-release.apk` をダウンロードします。
2. Android端末でAPKを開きます。
3. 必要に応じて、ブラウザーまたはファイル管理アプリに「不明なアプリのインストール」を許可します。

> 本アプリはGoogle Playでは配布していません。APKは必ずこのリポジトリのReleasesから取得してください。

## 日本語版の主な変更

- アプリ内文字列を日本語化
- アプリ名を「PCR ツール」へ変更
- 日本語版専用アプリID `jp.nono2359.pcrtool` を使用
- ランチャーアイコンへ「JP」バッジを追加
- 日本語版GitHub Releasesを使用した更新確認とAPK更新
- GitHub Actionsによる署名済みRelease APKの自動作成
- 原版APIとの互換性を維持した通信処理
- スキル説明・効果対象・状態異常などを日本語向けに調整
- 日本語表示へ「源暎ラテゴ」を採用し、数値にはAndroid標準フォントを使用
- 長い日本語名や大きなステータス値に対応した画面レイアウト
- キャラTier表の日本語化と、APIで未関連の新キャラIDの補完
- キャラの1コマ漫画を日本版リソースで表示
- 日本版公式4コマ漫画サイトへのリンク
- キャラクター・エネミー・クランバトルボスのSpineモデルをアプリ内で再生
- バトル／ギルドハウスのモーション選択と透過アニメーションGIF保存
- システム／デイ／ナイトのテーマ切り替え

アプリIDが原版の `cn.wthee.pcrtool` と異なるため、原版と日本語版を同じ端末へインストールできます。

## 更新方法

新しい日本語版が公開されると、アプリ内の通知メニューに更新案内が表示されます。案内からAPKをダウンロードして上書き更新できます。

自動更新が利用できない場合は、[Releases](https://github.com/nono2359/pcr-tool/releases) から最新版を手動でインストールしてください。

## 対応環境

- Android 6.0（API 23）以降
- インターネット接続（一部のお知らせ・検索・更新機能）

キャラクター・装備などの基本データはアプリ内データベースから表示されます。一部機能は原版のAPIや外部サービスを利用します。

## ソースからビルド

開発環境の目安：

- JDK 21
- Android SDK Platform 36
- Android SDK Build Tools 36.0.0
- Gradle Wrapper（リポジトリに同梱）

Debug APKのビルド：

```powershell
git clone https://github.com/nono2359/pcr-tool.git
cd pcr-tool
git switch feature/japanese-localization
```

`app/src/main/java/cn/wthee/pcrtool/utils/PrivateConfig.kt` を作成します。Buglyを利用しない場合は空文字列で構いません。

```kotlin
package cn.wthee.pcrtool.utils

object PrivateConfig {
    const val BUGLY_KEY = ""
}
```

その後、ビルドを実行します。

```powershell
.\gradlew.bat assembleOfficialDebug
```

生成先：

```text
app/build/outputs/apk/official/debug/app-official-debug.apk
```

Release版には署名設定が必要です。公開タグ `v*` のPush時には、GitHub Actionsが登録済みのRepository Secretsを使って署名済みAPKを作成します。

## 不具合・提案

日本語訳や日本語版固有の問題は、[Issues](https://github.com/nono2359/pcr-tool/issues/new/choose) からお知らせください。

原版固有の仕様や問題については、まず[原版リポジトリ](https://github.com/wthee/pcr-tool)をご確認ください。

## データ・関連プロジェクト

- リソース取得ツール：[Unity Texture Toolkit](https://github.com/esterTion/unity-texture-toolkit)
- アリーナ検索データ：[プリンセスコネクト！Re:Dive Fan Club](https://pcrdfans.com)
- ランキングデータ：[GameWith](https://gamewith.jp/pricone-re/article/show/93068)
- 参考プロジェクト：[静流筆記 | ShizuruNotes](https://github.com/MalitsPlus/ShizuruNotes)
- ゲームデータ：[pcr-tool-sql-diff](https://github.com/wthee/pcr-tool-sql-diff)

## ドキュメント

- [更新履歴](CHANGELOG.md)
- [使用しているデータテーブル](DATATABLE.md)
- [開発に参加する方へ](CONTRIBUTING.md)

## 家具モーション名マスタ

Spine Viewerの家具名は次のファイルをAPKへ内蔵し、同じファイルのGitHub Raw版を優先取得します。

```text
app/src/main/assets/spine/data/room-motion-names-ja.json
```

名前を追加・修正する場合は管理者用コマンドを実行します。

```powershell
py tools/update_room_motion_master.py --set 002205=家具名
```

複数件をJSONから取り込む場合：

```powershell
py tools/update_room_motion_master.py --source room-motion-name-import-ja.json
```

更新したJSONを `nono2359/pcr-tool` の既定ブランチへcommit/pushすると、アプリがGitHub Rawから取得します。通信失敗時や未push時はAPK内蔵版を使用します。

## ライセンスと謝辞

本プロジェクトは [Apache License 2.0](LICENSE) のもとで公開されています。

原版 `pcr-tool` の作者・コントリビューター、および関連データやツールを公開されている皆様に感謝します。

本プロジェクトは非公式ファンプロジェクトであり、Cygamesおよび「プリンセスコネクト！Re:Dive」の運営各社とは関係ありません。
