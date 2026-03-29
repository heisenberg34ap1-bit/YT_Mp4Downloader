# MP4 Downloader

Android 向けのシンプルな動画ダウンローダーです。  
直接取得可能な `.mp4` URL、または YouTube URL を入力し、保存先をユーザーが選んで端末へ保存します。

## 概要

- `.mp4` で終わる HTTP/HTTPS URL のダウンロードに対応
- YouTube URL を入力すると `NewPipeExtractor` で動画ストリームを解決
- Android の `CreateDocument` を使い、保存先とファイル名をユーザーが選択
- ダウンロード本体は `OkHttp` で実行

## 動作仕様

1. ユーザーが URL を入力してダウンロードを開始
2. URL が YouTube の場合は動画ストリームを抽出
3. それ以外は `.mp4` URL のみ受け付け
4. 保存ダイアログを表示
5. 選択した保存先へ動画ファイルを書き込み

YouTube の場合は、利用可能な動画ストリームのうち以下を優先して 1 件を選びます。

- 音声付き
- MP4 形式
- ビットレートが高いもの

## 技術スタック

- Kotlin
- Android SDK 34
- Min SDK 24
- Android Gradle Plugin `8.13.2`
- Kotlin Android Plugin `2.0.21`
- OkHttp `4.12.0`
- NewPipeExtractor `v0.25.2`

## セットアップ

### 前提

- Android Studio
- JDK 17
- Android SDK 34

### 起動

Android Studio でこのプロジェクトを開き、`app` モジュールを実行してください。

CLI でビルドする場合の例:

```powershell
$env:GRADLE_USER_HOME="$PWD/.gradle-local"
$env:ANDROID_USER_HOME="$PWD/.android-local"
./gradlew.bat assembleDebug
```

## 使い方

1. アプリを起動
2. 入力欄に URL を貼り付け
3. ダウンロードボタンを押す
4. 保存先とファイル名を選ぶ
5. 完了トーストを確認

入力可能な URL:

- `https://example.com/sample.mp4`
- `https://www.youtube.com/watch?v=...`
- `https://youtu.be/...`

## 主要構成

- `app/src/main/java/com/example/mp4downloader/MainActivity.kt`
  URL 検証、保存先選択、ダウンロード開始の UI 制御
- `app/src/main/java/com/example/mp4downloader/YoutubeStreamResolver.kt`
  YouTube の動画ストリーム選定とファイル名決定
- `app/src/main/java/com/example/mp4downloader/VideoFileDownloader.kt`
  実ファイルの HTTP ダウンロード処理
- `app/src/main/java/com/example/mp4downloader/DownloaderImpl.kt`
  `NewPipeExtractor` 用の HTTP ダウンローダー実装
- `app/src/main/java/com/example/mp4downloader/App.kt`
  `OkHttp` と `NewPipe` の初期化

## 権限

- `INTERNET`
- `ACCESS_NETWORK_STATE`

保存先は Storage Access Framework を利用するため、広いストレージ権限は要求していません。

## テスト

単体テストは以下で実行できます。

```powershell
$env:GRADLE_USER_HOME="$PWD/.gradle-local"
$env:ANDROID_USER_HOME="$PWD/.android-local"
./gradlew.bat test
```

確認結果:

- `testDebugUnitTest` は通過
- `testReleaseUnitTest` は `YoutubeStreamResolverTest` で失敗
- このテストは実際の YouTube URL 解決に依存しており、ネットワークや対象動画の状態に影響されます

また、サンドボックス環境では Kotlin daemon がローカル書き込み制限の警告を出すことがありますが、コンパイル自体はフォールバック動作します。

## 制約と注意

- `.mp4` 判定は URL の拡張子ベースです
- YouTube 以外のストリーミングサイト解析には未対応です
- YouTube 側の仕様変更により抽出処理が壊れる可能性があります
- 利用対象コンテンツの権利処理、利用規約、法令順守は利用者責任です

## 今後の改善候補

- 進捗表示
- キャンセル機能
- ダウンロード履歴
- MIME 判定の強化
- YouTube 依存テストのモック化
