# ゲームAPI共有仕様

この文書は、画面、ゲーム進行、イベント、結果表示、状態保存の担当が共有するAPI契約の正本である。APIの実装方式が変わっても、以下のデータ型、責務、権限、応答の意味は変えない。

## 1. 識別子と共通型

| 型 | 内容 |
| --- | --- |
| `GameId` | ゲームを一意に識別する文字列。サーバー生成のUUID |
| `ParticipantId` | 参加者を一意に識別する文字列。ゲーム内でサーバー生成のUUID |
| `PlayerNumber` | 画面表示順を表す1〜4の整数。ゲーム内で重複しない |
| `Month` | 1以上の整数。現在のゲーム内月 |
| `SquareId` | 1〜100の整数。盤面位置 |
| `Credits` | 0以上の整数。単位数 |
| `Academic` | 0〜100の整数。学力 |
| `Connections` | 0以上の整数。人脈 |
| `Money` | 0以上の整数。円単位の所持金 |
| `Version` | 状態更新の競合を防ぐ0以上の整数。更新成功ごとに1増加 |
| `Action` | `STUDY`、`CIRCLE`、`PART_TIME` のいずれか |
| `GameStatus` | `WAITING`、`PLAYING`、`FINISHED` のいずれか |
| `EnrollmentStatus` | `ENROLLED`、`REMEDIAL`、`GRADUATED` のいずれか |
| `AssessmentResult` | `PENDING`、`PASSED`、`REMEDIAL` のいずれか |

金額・単位・学力・人脈は浮動小数点にしない。確率は内部計算で小数を使ってよいが、応答には判定結果を真偽値で返す。

## 2. ゲーム状態

`GameState` は次の項目を持つ。画面表示用の値も、ゲーム進行担当が使う値もこの状態を正本とする。

```json
{
  "gameId": "GameId",
  "status": "WAITING|PLAYING|FINISHED",
  "month": 1,
  "currentPlayerNumber": 1,
  "turnOrder": ["ParticipantId"],
  "players": [
    {
      "participantId": "ParticipantId",
      "playerNumber": 1,
      "displayName": "string",
      "squareId": 1,
      "credits": 0,
      "academic": 50,
      "connections": 0,
      "money": 10000,
      "circleMember": false,
      "enrollmentStatus": "ENROLLED",
      "repeatYears": 0,
      "lastAssessmentMonth": 0,
      "assessmentResults": [
        { "subjectNumber": 1, "credits": 2, "result": "PENDING" }
      ]
    }
  ],
  "version": 0
}
```

- `currentPlayerNumber` は現在操作できる参加者を表す。
- `lastAssessmentMonth` は最後に半期判定を確定した月で、未判定は0とする。
- `repeatYears` は留年した回数で、卒業後は増えない。
- `assessmentResults` は直近半期の科目別結果を保持する。1科目の合格は2単位を表す。
- `GameState` の入力値をクライアントが直接変更する更新APIは提供しない。

## 3. APIの共通規則

- 形式はJSON。成功時はレスポンス本文に更新後の状態または処理結果を返す。
- `POST` の更新要求には `version` を含める。受信値が現在値と異なる場合は `409 Conflict` とし、状態を変更しない。
- 認証済み利用者はリクエストのセッション情報から識別する。`participantId` を本文で指定して他人になりすますことはできない。
- 不正な形式・範囲は `400 Bad Request`、存在しないゲーム・参加者は `404 Not Found`、権限不足は `403 Forbidden`、手番違い・状態違いは `409 Conflict` とする。
- 同じ `requestId` を再送した場合は最初の処理結果を返し、行動・サイコロ・月を二重に進めない。

## 4. エンドポイント契約

### 4.1 ゲーム作成

`POST /api/games`

- 権限: ログイン済み利用者。作成者が開始権限者になる。
- 画面: ロビーの「ゲームを作成」操作。
- 入力: `displayName`、`requestId`。
- 責任: ゲームID、作成者の参加者ID、初期状態を生成する。
- 応答: `201 Created` と `GameState`。初期状態は `WAITING`、月1、手番未確定。

### 4.2 参加

`POST /api/games/{gameId}/participants`

- 権限: ログイン済みで、まだそのゲームに参加していない利用者。
- 画面: ロビーの「参加」操作。
- 入力: `displayName`、`requestId`。
- 責任: 参加者IDとPlayerNumberを割り当てる。4人を超える参加は受け付けない。
- 応答: `201 Created` と参加者情報、参加後の `GameState`。

### 4.3 開始

`POST /api/games/{gameId}/start`

- 権限: 作成者（開始権限者）のみ。参加者が1人以上必要。
- 画面: ロビーの「ゲーム開始」操作。
- 入力: `version`、`requestId`。
- 責任: 参加順から手番順を確定し、`PLAYING` に遷移する。開始後は参加者を追加できない。
- 応答: `200 OK` と開始後の `GameState`。

### 4.4 状態取得

`GET /api/games/{gameId}`

- 権限: そのゲームの参加者、または開始権限者。
- 画面: ロビー・盤面・結果画面の表示および再読み込み。
- 責任: 保存済みの最新 `GameState` を返す。乱数や状態変更は行わない。
- 応答: `200 OK` と `GameState`。

### 4.5 月次行動と移動

`POST /api/games/{gameId}/turns`

- 権限: `currentPlayerNumber` に対応する本人のみ。
- 画面: 盤面の「勉強」「サークル」「アルバイト」のいずれか1つを選ぶ操作。
- 入力:

```json
{
  "participantId": "ParticipantId",
  "action": "STUDY|CIRCLE|PART_TIME",
  "version": 0,
  "requestId": "string"
}
```

- 責任: 行動効果、忘却、部費処理、サーバー側の6面サイコロ、移動、到着イベントを順番に処理する。処理順は [docs/game-rules.md](game-rules.md) に従う。
- 応答: `200 OK` と次を返す。

```json
{
  "gameState": "GameState",
  "rolledNumber": 1,
  "movedFrom": 1,
  "movedTo": 2,
  "actionResult": {
    "action": "STUDY",
    "circleJoined": false,
    "circleRejected": false,
    "feePaid": 0,
    "academicChange": 10,
    "connectionsChange": 0,
    "moneyChange": 0
  }
}
```

卒業済み参加者の操作、手番違い、同月の二重操作は `409 Conflict` とする。サイコロだけを振るエンドポイントは作らない。

### 4.6 半期判定

`POST /api/games/{gameId}/assessments`

- 権限: ゲーム進行サービスのみ。画面利用者から直接呼び出せない。
- 画面: 直接の操作なし。月次処理が半期末に到達したとき自動実行する。
- 入力: `version`、`requestId`。
- 責任: 8科目の過去問入手、科目別合否、単位加算、マス100到着者の卒業・留年判定を1回だけ行う。
- 応答: `200 OK` と判定後の `GameState`、科目別結果、判定済み月。
- `lastAssessmentMonth` が同じ月の再要求は、保存済みの判定結果を返して単位を再加算しない。

### 4.7 結果取得

`GET /api/games/{gameId}/result`

- 権限: そのゲームの参加者、または開始権限者。
- 画面: 全参加者の卒業状態が確定した後の「結果」画面。
- 責任: 参加者ごとの表示名、単位、留年回数、卒業状態、順位を返す。計算や状態変更は行わない。
- 応答: `200 OK`。全員の状態が確定していない場合は `409 Conflict`。

## 5. 担当境界

| 担当 | 責任 | 担当外 |
| --- | --- | --- |
| A: 参加・ロビー | ゲーム作成、参加、開始権限、PlayerNumber、開始前の表示 | 月次効果、乱数、単位判定 |
| B: ゲーム進行 | 手番、月、行動受付、サイコロ、盤面移動、競合制御 | 科目別合否の式、結果順位の表示 |
| C: 学業・イベント | 学力、人脈、交流、過去問、8科目の合否、単位加算、部費処理 | 認証、参加者追加、画面のルーティング |
| D: 卒業・結果 | マス100到着者の卒業・留年、結果データ、順位確定 | 月次行動の効果、サイコロ、開始前ロビー |

複数担当が同じ状態を更新する場合は、Bの手番・`version` 制御を通し、1リクエストの中で責任範囲を越えて直接更新しない。
