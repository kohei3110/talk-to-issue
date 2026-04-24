---
name: workiq
description: "Microsoft Work IQ reference and best practices. Use when: implementing Work IQ MCP server integration, querying Microsoft 365 data (emails, meetings, documents, Teams messages, people), setting up WorkIQ CLI or MCP server, using workiq plugins, building productivity tools with Work IQ."
---

# Microsoft Work IQ

Microsoft Work IQ は GitHub Copilot 向けの公式プラグインコレクションで、MCP サーバー・スキル・ツールを通じて AI アシスタントを Microsoft 365 データに接続する。

> ⚠️ Public Preview: 機能や API は変更される可能性がある。

## 概要

- **リポジトリ**: https://github.com/microsoft/work-iq
- **パッケージ**: `@microsoft/workiq`
- **対応プラットフォーム**: win_x64, win_arm64, linux_x64, linux_arm64, osx_x64, osx_arm64
- **前提条件**: Node.js 18+

## クエリ可能なデータ

| カテゴリ | 例 |
|----------|-----|
| メール | 「John が提案書について何を言ったか？」 |
| 会議 | 「明日のカレンダーは？」 |
| ドキュメント | 「最近の PowerPoint プレゼンテーションを探して」 |
| Teams | 「Engineering チャンネルの今日のメッセージを要約して」 |
| 人 | 「Project Alpha に取り組んでいるのは誰？」 |

## MCP サーバーセットアップ

### VS Code での設定

`mcp.json` または `settings.json` に以下を追加:

```json
{
  "workiq": {
    "command": "npx",
    "args": ["-y", "@microsoft/workiq@latest", "mcp"],
    "tools": ["*"]
  }
}
```

### スタンドアロンインストール

```bash
# グローバルインストール
npm install -g @microsoft/workiq

# MCP サーバー起動
workiq mcp

# npx で直接実行（常に最新版を取得）
npx -y @microsoft/workiq mcp
```

### テナント管理者の承認

Microsoft 365 テナントデータにアクセスするには、テナント管理者による承認が必要。初回アクセス時に承認ダイアログが表示される。管理者でない場合はテナント管理者に連絡する。

## CLI リファレンス

| コマンド | 説明 |
|----------|------|
| `workiq accept-eula` | EULA の承認（初回使用時に必須） |
| `workiq ask` | エージェントに質問、またはインタラクティブモード |
| `workiq mcp` | MCP stdio サーバーを起動 |
| `workiq version` | バージョン情報の表示 |

### グローバルオプション

| オプション | 説明 |
|------------|------|
| `-t, --tenant-id <tenant-id>` | 認証に使用する Entra テナント ID |
| `--version` | バージョン情報の表示 |
| `-?, -h, --help` | ヘルプの表示 |

### 使用例

```bash
# EULA の承認
workiq accept-eula

# インタラクティブモード
workiq ask

# 特定の質問
workiq ask -q "明日の会議は？"

# テナント指定
workiq ask -t "your-tenant-id" -q "メールを表示"
```

## プラグイン一覧

### 1. workiq

Microsoft 365 データを自然言語でクエリ。メール、会議、ドキュメント、Teams メッセージなど。

- **MCP ツール**: `ask_work_iq`, `accept_eula`, `get_debug_link`
- **インストール**: `/plugin install workiq@work-iq`

### 2. microsoft-365-agents-toolkit

M365 Copilot 宣言的エージェント構築ツールキット。スキャフォールディング、JSON マニフェスト作成、機能構成、デプロイ。

- **スキル**: `install-atk`, `declarative-agent-developer`, `ui-widget-developer`
- **インストール**: `/plugin install microsoft-365-agents-toolkit@work-iq`

### 3. workiq-productivity

読み取り専用の生産性インサイト。メール、会議、Teams、SharePoint、プロジェクト、人に関する 9 つのスキル。

| スキル | 説明 |
|--------|------|
| `action-item-extractor` | アクションアイテムの抽出（担当者、期限、優先度） |
| `daily-outlook-triage` | 受信トレイとカレンダーの日次サマリー |
| `email-analytics` | メールパターン分析（量、送信者、応答時間） |
| `meeting-cost-calculator` | 会議の時間とコストの計算 |
| `org-chart` | 指定した人物の ASCII 組織図 |
| `multi-plan-search` | 全 Planner プランからのタスク検索 |
| `site-explorer` | SharePoint サイト、リスト、ライブラリの閲覧 |
| `channel-audit` | 非アクティブチャンネルの監査 |
| `channel-digest` | 複数チャンネルのアクティビティ要約 |

- **インストール**: `/plugin install workiq-productivity@work-iq`

## ベストプラクティス

1. **npx を使う**: `npx -y @microsoft/workiq@latest mcp` で常に最新版を使用する
2. **テナント ID を明示する**: 複数テナントがある場合は `-t` オプションで指定する
3. **EULA は初回に承認**: `workiq accept-eula` を最初に実行する
4. **管理者承認を事前に取得**: テナントデータへのアクセスには管理者承認が必要
5. **read-only を意識する**: workiq-productivity プラグインのスキルはすべて読み取り専用