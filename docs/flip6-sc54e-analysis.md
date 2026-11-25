# SC-54E（Galaxy Z Flip6 ドコモ版）で RIL_REJECTED になる原因の調査

## 背景

ドコモ版 Galaxy Z Flip6（SC-54E）で Galaxy DIAG を実行すると、接続準備が失敗し、
`RIL_RESULT=-37 / DIAG_ERROR=IllegalStateException:RIL_REJECTED` が返る。
root 許可と Samsung 電話サービスまでは届いており、拒否はモデム／RIL の入口で起きている。

## 解析対象

- ドコモ版（SC-54E / 内部 SM-F741D）は Samsung 公式 FUS から取得できない（全文字列 S01、日本キャリア版の既知制約）。
- 同一機種の直販／SIMフリー版 **SM-F741Q**、ビルド **F741QOPU1EZJD**（Android 17, 2026-11-03, 約14GB）を代用。
  モデム世代とチップが同一のため RIL 解析に使える。この直販モデムにも Docomo/KDDI/SBM の
  carrier policy が含まれる（マルチキャリア）。

## 判明した事実

1. **モデム世代差**：Flip6 のモデムは `MPSS.DE.5.0-...-LANAI_GEN_PACK`（SM8650 世代）。
   S26（SM-S948Q）は SM8850 で、2 世代違う。
2. **送信コマンドの正体**：Galaxy DIAG が送る OEM バイト列 `11 b0 00 05 01` は、
   Samsung の **Domestic OEM コマンド**。`vendor/lib64/libsec-ril.so` に
   `DomesticOemManager::DoDomesticOemRequest`、`BuildDomesticOemResponse`、
   `BuildIpcDomesticGet/Set…`、`DOMESTIC_SECURITY_MODE` / `DOMESTIC_OTA_REG_MODE` /
   `DOMESTIC_GCF_TEST_MODE` などが並ぶ。OEM raw hook の入口は
   `libsec_semRil.so` の `InvokeOemRequestHookRaw`。
3. **拒否のゲート**：`libsec-ril.so` に以下の判定が存在する。
   - `%s - Domestic command is not supported`
   - `%s() mismatched sales code : %s, %s` / `invalid sim operator or sales code`
   - `%s: domesticAllowed %d internationalAllowed %d roam_pref %d`
   - `%s/sales_code.dat`
   - ドコモ固有分岐 `DCM-%s(): isDomesticOtaReg for domesticOtaStart`
   `DoDomesticOemRequest` 冒頭はサブコマンド番号で分岐し、既知テーブルに無い／
   許可条件を満たさないサブコマンドは「Domestic command is not supported」で弾く。

## 結論（確度：高、ただし実機ログ未確認）

Domestic OEM コマンドは、販売コード（sales code）が国内扱いで条件を満たすときだけ通る。
S26（直販/楽天 SJP）は通り、Galaxy DIAG が成立した。SC-54E は **ドコモ（DCM）** で、
`domesticAllowed` か `mismatched sales code` のゲート、もしくは SM8650 世代で
サブコマンド `00 05` が未実装なため、`-37` で拒否されていると考えられる。
どの分岐かの最終確定には実機の `adb logcat` RIL ログが必要（現在、実機なし）。

## 回避法の候補（いずれも実機未検証）

- **A. 別経路で Qualcomm DIAG を直接開く（推奨方向）**：NSG が必要とするのは Qualcomm の
  `/dev/diag`。Samsung OEM RIL の domestic command を経由せず、root で USB の diag
  composition を有効化して `/dev/diag` を NSG に使わせる。RIL のゲートを回避できる。
  Galaxy DIAG 側は、RIL 送信の代わりにこの経路を叩く実装に差し替える。
- **B. 他の domestic サブコマンドを試す**：`DOMESTIC_OTA_REG_MODE` など、
  gate を通る別サブコマンドで DIAG/OTA を開けるか実機で確認する。
- **C. sales code を国内コードへ変更**：`sales_code.dat` を書き換えると domestic gate を
  通せる可能性があるが、CSC/ネットワーク設定に広く影響し副作用とリスクが大きい。非推奨。

A が最も筋が良いが、どれも SC-54E 実機（root + adb）での検証が前提。実機が用意できたら
RIL ログで分岐を確定し、A を実装・検証する。

## 追補：トランスポートは動作、不足はモデム側の DIAG 公開

vendor を展開して確認した結果、QRTR のトランスポート側は動いている。

- `vendor/etc/init/vendor.qti.diag.rc`：`on early-boot start vendor.diag-router`。
  Qualcomm の `diag-router`（`/vendor/bin/diag-router`）が起動し、QRTR に DIAG を中継する。
- RIL には `IpcTxExecDiagScan` / `IpcRxServiceModeDiagScan` / `ModemProxy::ExecDiagScan` があり、
  Service mode 経由の DIAG scan を持つ。

したがって不足しているのは **モデム（MPSS）側が DIAG の CMD / DCI エンドポイントを QRTR に
公開していない**こと。Samsung は出荷時にこれを閉じ、OEM domestic command（`11 b0 00 05 01`）で
開く。ドコモ（DCM）の sales code ではこの domestic command が通らないため、endpoint が
公開されず、`qrtr-lookup` に MODEM:CMD(instance1,node0) / DCI_CMD(instance3,node0) が現れない。

## 回避の方向（更新、実機未検証）

- **A'（最有力）**：Samsung OEM RIL を経由せず、Qualcomm 標準の手段でモデムの DIAG を開く。
  root で `diag_mdlog` / DIAG マスク設定 / 関連 NV・EFS を使って MPSS の DIAG を有効化できれば、
  sales code ゲートを通らずに CMD/DCI endpoint を公開できる可能性がある。NSG はその QRTR/
  `/dev/diag` を使う。Galaxy DIAG 側は、RIL 送信の代わりにこの経路を叩く実装に差し替える。
- B（他の domestic サブコマンド）/ C（sales code 変更）は前述。C は副作用大で非推奨。

いずれも SC-54E 実機（root + adb）での検証が前提。実機の `adb logcat` RIL ログで -37 の分岐を
確定し、A' が MPSS endpoint を公開できるか実機で確認してから実装する。
