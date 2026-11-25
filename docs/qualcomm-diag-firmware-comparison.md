# Samsung Galaxy Qualcomm DIAG ファームウェア比較

調査日: 2026-11-25

対象: Galaxy DIAG `51e9e4e`、Samsung Android 17 純正ファームウェア

手法: 読み取り専用の取得・展開・静的解析。端末、NV/EFS、モデム、セキュリティ状態は変更していない。

## 結論

### 確定

1. Galaxy DIAG の5バイト要求 `11 b0 00 05 01` は「Domestic OEM コマンド」ではない。
   Samsung RIL の OEM Misc ディスパッチャでサブコマンド `0xb0` に分岐し、
   `OemMiscManager::DoOemSetCpUserLog` を呼ぶ **CP User Log 設定要求**である。
2. RIL は最終的に8バイト `08 00 00 00 0a 7a 03 <mode>` をCPへ送る。
   `<mode>` は有効化時 `01`、無効化時 `00` である。
3. SC-54Eがアプリへ返す `-37` はLinux errnoでもraw RIL errnoでもない。
   `ISemTelephony` が `CommandException.Error.INTERNAL_ERR.ordinal()` を負数化した値である。
4. ドコモ版SC-54E EZIHと直販版SM-F741Q EZJDでは、比較対象のAP側DIAG/RIL実行物が
   SHA-256まで一致した。少なくとも「DCM版AP RILに0xb0が未実装」は否定できる。
5. `diag-router` は両Flip6でearly-bootから同一設定で起動する。これはAP側の転送路であり、
   MPSSがQRTR service 4097を登録するかどうかを単独では決めない。
6. Galaxy DIAG自身は鍵取得、認証、復号、DIAG中継、NV/EFS書換え、カーネル変更を行わない。
   固定OEM要求の後にQRTRの公開状態を確認するだけである。
7. 実機で確認済みなのは、直販S26 UltraとドコモZ Flip6の2台だけである。
   直販Z Flip6の実機挙動は未確認で、ファームウェア静的比較だけを行った。
8. 「X80/X85は一律にDIAG暗号化」という理解は不正確である。Qualcommの公式loggerは
   secured loggingを**任意の端末状態**として扱い、`qcom-band-menu` もX80/X85を
   「without encryption」の条件付きで対応する。さらにX75のFlip6 CPにも
   `diag_log_security.c` と `/nv/item_files/diag/diag_encrypt` が存在する。
   Secure Loggingの実装能力と、特定製品での有効状態は別である。
9. 国内直販S24 Ultra EZK2を実取得した。S24のAP RILにもFlip6と同じ0xb0 dispatcher、
   `DoOemSetCpUserLog`、CP IPC `08 00 00 00 0a 7a 03 <mode>` がある。さらにS24 X75 CPにも
   Flip6と同じSecure Logging実装がある。したがって「S24 Ultraは暗号化される」という報告を、
   X75世代やAP側コマンド欠落だけで説明することはできない。

### 有力な推定

- S26 Ultraで0xb0成功後にservice 4097 instance 1/3が現れるため、CP User Logモードが
  MPSSのDIAG CMD/DCI_CMD公開状態を変更している可能性が高い。
- SC-54Eは要求前から同じエンドポイントを公開しているため、`INTERNAL_ERR` は
  「既に公開済みの状態でSETを拒否」「CP世代固有の状態機械」「CP側の一般エラー」の
  いずれかである可能性が高い。AP側のモデル/CSC拒否という旧仮説より証拠に合う。
- S26とFlip6の差は、共通のAP `diag-router` よりもCP/MPSSのビルド、初期設定、
  起動時ポリシーにある可能性が高い。
- S26でNSGが動く理由は「Galaxy DIAGが復号した」からではない。現時点では、
  必要なログが平文、ログ種別ごとの選択的暗号化、NSG内部の鍵交渉、CP User Log設定が
  CP内部ポリシーも変更、の4候補が残る。Galaxy DIAG自身に復号処理がないことだけは確定している。

### 未確認

- SC-54EのCPが0xb0に対して返したraw IPCエラー値と、その分岐条件。
- 直販SM-F741Q、S24 Ultra、S25 Ultraの実機初期公開状態と0xb0の結果。
- S26 Ultraで全DIAGログが平文になるのか、一部のsecure logだけ別処理を要するのか。
- NSGが `SECURITY_KEY_LIST` を動的生成して鍵情報を取得するか。APK内に固定要求列はなく、
  暗号関連コードとfield-name文字列だけでは復号実装の証拠にならない。
- ブートローダー、Verified Boot、Knox Warranty Bitが別のDIAG経路へ与える影響。

## 実機結果と静的解析を分離した比較

| 機種 | 実機の販売形態 | SoC/モデム | 実機初期状態 | 0xb0要求 | NSG | 静的解析 |
|---|---|---|---|---|---|---|
| SM-S948Q S26 Ultra | 国内直販 | SM8850 / X85 | DIAG非公開 | 成功、instance 1/3公開 | 適用後にLTE詳細を表示 | BZI9 AP/CP取得済み |
| SC-54E / SM-F741D Z Flip6 | ドコモ | SM8650 / X75 | DIAG公開済み | `-37` | Galaxy DIAGなしで動作 | EZIH AP/CP取得済み |
| SM-F741Q Z Flip6 | 国内直販 | SM8650 / X75 | **未確認** | **未確認** | **未確認** | EZJD AP/CP取得済み |
| SM-S928Q S24 Ultra | 国内直販 | SM8650 / X75 | 未確認 | 未確認 | 未確認 | EZK2 AP/CP取得・静的解析済み |
| SM-S938Q S25 Ultra | 国内直販 | SM8750 / X80 | 未確認 | 未確認 | 未確認 | 公開メタデータのみ確認 |

両実機ともブートローダーはロック状態である。それでも初期公開状態が異なるため、
ブートローダーのロック有無だけをDIAG公開の必要十分条件にはできない。

## 一見した不整合を解く5層モデル

「DIAGが開く」「ログが読める」「暗号化される」を同じON/OFF状態として扱うと、今回の
実機結果は矛盾して見える。実装上は少なくとも次の5層が独立している。

| 層 | 判定内容 | S26 Ultra実機 | SC-54E実機 |
|---|---|---|---|
| A. endpoint公開 | QRTR service 4097のCMD/DCI_CMDが登録されるか | 0xb0前は非公開、後に公開 | 起動時から公開 |
| B. OEM setter | CP User LogのSETを受理するか | 成功 | `INTERNAL_ERR` |
| C. log生成 | maskで要求したradio logをCPが生成するか | NSGに必要な範囲は生成 | NSGに必要な範囲は生成 |
| D. secure logging | 生成payloadを暗号化するか | ログ種別別の実測が未完 | ログ種別別の実測が未完 |
| E. host解析 | NSGが鍵交渉・復号・世代別schema解析を行えるか | LTE詳細の表示を確認 | Galaxy DIAGなしで表示を確認 |

この分離により、次の現象は両立する。

- SC-54EはAが既にONなのでNSGは使えるが、Bのsetterは失敗できる。
- S26はBを通すことでAがONになり、その後D/Eが許すログだけNSGが読める。
- X75/X80/X85のどれもSecure Logging実装を持ち得るが、製品ごと・ログ種別ごとにDの状態が違い得る。
- 同じSM8650/X75でもS24 UltraとFlip6はCPイメージ、OEM software、MCFG、初期NV/EFSが
  同一とは限らず、A～Dの初期値が同じとは限らない。

## 差を作り得る軸

優先度の高い順に、現在の証拠と整合する差分は次の通りである。

1. **機種別CP/MPSSビルド** — SoC/モデム名が同じでも、Samsungの`qdsp6_oemsw.mbn`、
   MPSS build、service registration policyは別にできる。
2. **CPの初期状態と永続設定** — `/nv/item_files/diag/diag_encrypt`、user-log状態、EFS/NVの
   出荷値や更新履歴がendpoint公開とsecure loggingを変え得る。
3. **carrier/region MCFG** — DCM/SJPのCPとMCFGは実際に異なる。ただしFlip6のAP側RILは
   byte-identicalなので、少なくともAP RILのsales-code分岐で0xb0を拒否する説明は否定された。
   またSJP CPにもDCM/KDDI/SBMの複数carrier policyが同梱され、実行時に選択されるため、
   ZIPのregion名だけから有効ポリシーを決めることもできない。
4. **世代差** — X80/X85ではschemaや4-byte QRTR header等の変更があるが、世代だけで
   暗号化状態を決める証拠はない。
5. **host tool差** — NSG、qcom-band-menu、qcom-efs-browserは要求するDIAG command/log IDが
   異なる。一つが読めることは、全DIAGログが平文であることを意味しない。

したがって「ドコモだから公開」「S24だから暗号化」「X85だから暗号化」という単一軸の結論は、
いずれも現時点の証拠を超える。確定できるのは、DCM/SJP Flip6のAP RILが同一である一方、
CP/MCFGは異なること、そして実機結果はS26直販とSC-54Eの2台に限られることである。

## 取得したファームウェア

| 機種/CSC | ビルド | Android | 取得元 | 復号後ZIP SHA-256 | 状態 |
|---|---|---:|---|---|---|
| SM-S948Q / SJP | S948QOPU1BZI9 | 17 | Samsung FUS | `04fefa53a229e5d1274928874511c4585db16ac6eb4ca0ce681dc9546efc1200` | AP/CP展開済み |
| SC-54E / DCM | SC54EOMU1EZIH | 17 | Samsung FUS | `96cd2edba8785a16de30f9ec7c632c9b726cdee41b6ed1f87fcc0f07f7da0e15` | AP/CP展開済み |
| SM-F741Q / SJP | F741QOPU1EZJD | 17 | Samsung FUS | `a87ee76deb6585a6b212d45760683086b64c64b8b46411b160062335459bdc77` | AP/CP展開済み |
| SM-S928Q / SJP | S928QOPU1EZK2 | 17 | Samsung FUS | `8149c373e58ebe33432409b528dcdaa2f405dccc176c7dd9732336b25f11fde0` | AP/CP展開済み |

SC-54EのFUSアーカイブ名は
`SC-54E_4_20260921143541_kcamerig1l_fac.zip.enc4`、復号後はAP
`SC54EOMU1EZIH`、CP `SC54EOMU1EZIH_CP36479396`、CSC `SC54EQCM1EZIH` を含む。
ZIP全体を `unzip -t` で検査し、全5メンバーが正常だった。

S24 UltraのFUSアーカイブ名は
`SM-S928Q_4_20261103114740_s5jf3o34wr_fac.zip.enc4`、CPは
`S928QOPU1EZK2_CP37198199`である。復号後ZIP全体を`unzip -t`で検査し、全5メンバーが正常だった。
CP `modem.bin` SHA-256は
`15d39aff0b6c8f5b90179dfef4a1aec18f82ab1fd3bd88b6cea797c2f5666d86`である。

S25 UltraについてはSM-S938Q/SJP `S938QOPU1DZJC`（Android 17）の配布記録まで確認したが、
本調査時点で巨大なFUS本体を未取得なので、X80バイナリ比較済みとは扱わない。

## Galaxy DIAGの実際の処理

`RootMain` は次の処理だけを行う。

1. rootで起動された短命プロセスであることを確認する。
2. root/shell UIDを拒否するSamsung APIへ入るためUID/GIDを1000へ変更する。
3. Binder service `isemtelephony` の
   `ISemTelephony.invokeOemRilRequestRawForPhone(0, request, response)` を呼ぶ。
4. 有効化なら `11 b0 00 05 01`、無効化なら `11 b0 00 05 00` を送る。
5. RIL結果が非負なら `/vendor/bin/qrtr-lookup` を実行する。
6. service ID 4097、node 0のinstance 1と3が両方ある場合だけ成功とする。

RILが負数を返すと、その場で `RIL_REJECTED` になりQRTR再確認には進まない。
したがってSC-54Eでは、実際には既にDIAGが公開済みでもアプリ画面上は失敗になる。
これは「RIL結果」と「現在のDIAG公開状態」を同一視したアプリ側の制約である。

## OEM RILコマンドの処理経路

### 入力フレーム

```text
11 b0 00 05 01
│  │  └──┬──┘ └─ CP User Log mode = 1
│  │     └──── total length = 5 (big endian)
│  └────────── OEM Misc subcommand = 0xb0
└───────────── Samsung OEM request group = 0x11
```

`DoOemMisc` はサブコマンドバイトを読み、`0xb0` のジャンプテーブル要素から
`DoOemSetCpUserLog` へ分岐する。`DoOemSetCpUserLog` はpayload先頭1バイトを取り出し、
event 101のMessageを作り、モデム実装へ渡す。

```text
ISemTelephony Binder
  -> SemTelephonyController event 27
  -> Samsung RIL OEM raw request
  -> OemMiscManager::DoOemMisc
  -> OemMiscManager::DoOemSetCpUserLog
  -> ModemProxy::SetCpUserLog
  -> IpcModemImplMisc::SetCpUserLog
  -> IpcProtocol41Misc::IpcTxSetCpUserLog
  -> CP IPC: 08 00 00 00 0a 7a 03 <mode>
```

### 関数とアドレス

| バイナリ/機種 | 関数 | VA |
|---|---|---:|
| `libsec-ril.so` Flip6 DCM/SJP | `OemMiscManager::DoOemMisc` | `0x3b8e60` |
| 同上 | `OemMiscManager::DoOemSetCpUserLog` | `0x3b8b90` |
| 同上 | `OemMiscManager::OnOemMiscDone` | `0x3b94f4` |
| 同上 | `ModemProxy::SetCpUserLog` | `0x38e110` |
| 同上 | `IpcModemImplMisc::SetCpUserLog` | `0x505544` |
| 同上 | `IpcProtocol41Misc::IpcTxSetCpUserLog` | `0x4a4e8c` |
| `libsec-ril.so` S26 | `OemMiscManager::DoOemSetCpUserLog` | `0x3d30a4` |
| 同上 | `OemMiscManager::DoOemMisc` | `0x3d3324` |
| 同上 | `OemMiscManager::OnOemMiscDone` | `0x3d3818` |
| 同上 | `ModemProxy::SetCpUserLog` | `0x3a720c` |
| 同上 | `IpcModemImplMisc::SetCpUserLog` | `0x526f08` |
| 同上 | `IpcProtocol41Misc::IpcTxSetCpUserLog` | `0x4c524c` |

Flip6の `IpcTxSetCpUserLog` は64-bit定数をスタックへ格納した後、offset 7をmodeで上書きする。

```asm
mov   x8, #0x8
movk  x8, #0x7a0a, lsl #32
movk  x8, #0x3,    lsl #48
str   x8, [sp]
strb  w19, [sp, #7]
```

little endianの実データは `08 00 00 00 0a 7a 03 <mode>` になる。
正式な各CP IPCフィールド名は公開ヘッダで確認できていないため、値以上の命名は行わない。

## ドコモ版Flip6と直販Flip6のバイナリ比較

### AP側は一致

| ファイル | DCM EZIH / SJP EZJD SHA-256 |
|---|---|
| `vendor/lib64/libsec-ril.so` | `5058ab0e7ed5caa094298c521bdcb588b8cc56c215ff4e6e1250d66d9764e54a` |
| `vendor/lib64/libsec_semRil.so` | `e196ce0a7f3b85d5eac50b62a0a214bf9012a1feb299e2936c8dac04ee11c8c0` |
| `vendor/lib64/libdiag.so` | `97cabc8aa4a37353080cca1e0c858ffe1ac6c4dc6f1d5368dfd72da295e8c3fb` |
| `vendor/bin/diag-router` | `1604dad01e9e1a32f681152c496aafbd90d5f231b8da791674df56bd47b02ab3` |
| `vendor/bin/diag_mdlog` | `d6546d8c04e534e7bcd413d763d6a35ed52e7cef6776b8d65bebdde3d5c3bac4` |
| `vendor/bin/qrtr-lookup` | `257e842d9925d629cc7249d3b829f1c440acef7953ef49f4caf1da7e55ed2d45` |
| `vendor/etc/init/vendor.qti.diag.rc` | `231110da4819424204e7a96162e9a211b9a5d635dfa7faf10ff3183bc7212846` |

両Telephony JAR全体のハッシュはビルド差で異なるが、逆コンパイルした
`SemTelephonyController.java` と `CommandException.java` はファイル単位で完全一致した。

### CP側は差がある

| 項目 | SC-54E DCM EZIH | SM-F741Q SJP EZJD |
|---|---|---|
| `modem.bin` SHA-256 | `00c8e7f2191371a6fb5fb5769f0fc5216365fe56e578b145e900909151bff27b` | `2914b9df2a0d2638034ac19da3d4693bd0ba5a17481566a2714890f526b35b61` |
| MPSS Build ID | `MPSS.DE.5.0-02520.3-LANAI_GEN_PACK-1.62174.441` | 同じ |
| timestamp | 2026-09-19 | 2026-11-02 |
| `qdsp6_oemsw.mbn` | 異なる | 異なる |
| APAC/DCM `mcfg_sw.mbn` | 異なる | 異なる |

全carrier MCFGとhardware MCFGも再ビルドで変化しており、EZIH対EZJDの差だけから
「DCM sales codeが拒否条件」と断定することはできない。同一CPビルドでDCM/SJPを比較するか、
SC-54E実機のCP応答を取得する必要がある。

## S24 UltraとFlip6: 同じX75でも同一製品ではない

国内直販S24 Ultra EZK2を実取得し、Flip6 DCM/SJPと同じ手順で比較した。

### AP側

| 項目 | S24 Ultra EZK2 | Flip6 EZIH/EZJDとの関係 |
|---|---|---|
| `libsec-ril.so` SHA-256 | `3a8a7aab10c70e75a3cab6277a8e4d0bd9aa6a935f143286e37ff5fb1f1d6f0e` | build全体は異なる |
| `libsec_semRil.so` | `e196ce0a...e11c8c0` | 完全一致 |
| `libdiag.so` | `97cabc8a...e8c3fb` | 完全一致 |
| `diag-router` | `1604dad0...b02ab3` | 完全一致 |
| `diag_mdlog` | `d6546d8c...c3bac4` | 完全一致 |
| `qrtr-lookup` | `257e842d...ed2d45` | 完全一致 |
| `vendor.qti.diag.rc` | `231110da...12846` | 完全一致 |

S24の`libsec-ril.so`には次の関数が存在する。

| 関数 | S24 VA |
|---|---:|
| `OemMiscManager::DoOemSetCpUserLog` | `0x3b8b9c` |
| `OemMiscManager::DoOemMisc` | `0x3b8e6c` |
| `ModemProxy::SetCpUserLog` | `0x38e11c` |
| `IpcModemImplMisc::SetCpUserLog` | `0x505554` |
| `IpcProtocol41Misc::IpcTxSetCpUserLog` | `0x4a4e98` |

`IpcTxSetCpUserLog`の命令列もFlip6と同じく定数`0x00037a0a00000008`を格納し、最後の1バイトを
modeで上書きする。すなわちAPからCPへ送る8バイトは同じである。S24でDIAGがロックされるという
実機報告が正しい場合でも、原因を「S24 AP RILに解除コマンドがない」とは説明できない。

### CP側

S24の外側メタデータはFlip6と同じ`MPSS.DE.5.0-02520.3-LANAI_GEN_PACK-1.62174.441`系を示すが、
S24 `modem.bin`、`qdsp6_oemsw.mbn`、MCFGはFlip6とbyte-identicalではない。S24 CPにも
`diag_log_security.c`、`diag_encrypt`、ORANGE、wrapped key、QWES/attestation実装が入る。

一方、S26の`qdsp6_oemsw.mbn`で見つかる`oemapi_misc_user_log_set`文字列はS24 CPでは
見つからなかった。これはCP OEM softwareの差を示す補助証拠だが、文字列がないだけでは
handler不存在を確定できない。strip、inlining、別module、名称変更を除外できないためである。

以上から、S24とFlip6の違いは同じSM8650/X75でも、機種別CP OEM software、出荷時EFS/NV、
service初期状態、log class別のSecure Logging policyで生じ得る。S24実機でのendpoint一覧、
0xb0結果、security-key/log packet採取なしに、どの一つが原因かは確定できない。

## `RIL_RESULT=-37` の正確な意味

Android 17の `SemTelephonyController.invokeOemRilRequestRawForPhone` は例外時に次を返す。

```java
int ordinal = ((CommandException) exception).getCommandError().ordinal();
return ordinal > 0 ? -ordinal : ordinal;
```

このビルドのenumではordinal 37は `INTERNAL_ERR` である。対して
`CommandException.fromRilErrno(37)` は `NO_MEMORY` へ変換する。したがって、表示された
`-37` をLinux errno、raw RIL error 37、または「コマンド未実装」の番号として解釈するのは誤りである。

`INTERNAL_ERR` は広いラッパー結果なので、CPのraw errorや分岐条件はまだ確定していない。
AP側の対象コードがDCM/SJPで一致するため、APのCSC分岐やコマンド欠落は支持されない。

## DIAG公開制限の所在

`vendor.qti.diag.rc` は両Flip6で次の同一設定を持つ。

```rc
on early-boot
    start vendor.diag-router

service vendor.diag-router /vendor/bin/diag-router
    class hal
    user system
    group system
    capabilities KILL
    disabled
```

`diag-router` は既にQRTRへ登録されたリモートDIAGをAPへ中継するが、CP自身のサービス登録を
作り出すものではない。S26で0xb0の後にservice 4097 instance 1/3が現れる事実は、公開状態の
最終制御がCP/MPSS側にあることを示す。SC-54Eでは0xb0前から公開済みなので、RIL拒否と
公開状態は独立に扱う必要がある。

## DIAG公開と暗号化は別の仕組み

最低でも次の層を分ける必要がある。

1. QRTR上にDIAG endpointが存在するか
2. endpointへ接続できるか
3. 個々のDIAGコマンドが許可されるか
4. log maskで要求したログが生成されるか
5. 生成されたログpayloadがsecure logging形式か
6. host側が鍵情報とログ形式を解釈できるか

`DIAG_LOG_SEC_F` はDIAG command ID `0x9e` のSecure Logging packetである。
Qualcomm公式 `qcom-log-collector` は、別コマンド
`DIAG_SERV/SECURITY_KEY_LIST`（subsystem `0x12`, subcommand `0x0833`）で公開鍵とwrapped keyの
メタデータを取得する設計を持つ。同公式User Guideも `.qlc` のSecurity keysを
「if device uses secured logging」と条件付きで記載する。これはendpoint公開とは別の
ハンドシェイクであり、全X80/X85製品で常時必須という仕様ではない。

Galaxy DIAGの5バイト要求は0x9eも0x0833も含まず、鍵素材もトークンも運ばない。
したがってGalaxy DIAG自身が暗号化を解除または復号しているという証拠はない。
S26でNSGがLTE詳細を表示できる事実は、少なくともNSGが必要としたログ経路が利用可能になった
ことを示すが、全ログが平文だとは証明しない。

`qcom-band-menu` の公開リリースはX60～X75を通常対応とし、X80/X85を明示的に
「without encryption」の条件付き対応としている。これは、少なくとも同じ世代に暗号化ON/OFFの
実機が共存し得るという開発者側の観測であり、「X80/X85は必ず暗号化」とは整合しない。
また、`qcom-efs-browser` はSM8850でDIAG command/replyによるEFS readを記録している。
これも全てのasync radio logが平文だという証明ではないが、X85のDIAG transport全体が
一律に復号不能という説明を否定する。

### CPファームウェア内のSecure Logging実装

Flip6 X75の`modem.bin`には次の文字列が存在した。

```text
diag_log_security.c
/nv/item_files/diag/diag_encrypt
diag_encryption_config_init
tm_ue_setting_protocol_sim_private_secure_logging
ORANGE Encryption failure while draining log code
diag_key_generate_log
SECURITY_KEY_LIST
```

したがってSecure Loggingのコードと設定はX75世代にも既にある。「X80で初実装」という意味での
世代説は否定できる。同じbinaryにはwrapped/public key、QWES license、secure boot、attestation、
log classifier別の暗号化処理も含まれる。これらは**機能の存在**を示すだけで、SC-54E実機で
暗号化がONか、どのlog ID/classに適用されるかまでは示さない。

S26 X85の`qdsp6_oemsw.mbn`には次の文字列がある。

```text
diag_services_enabled=%x
oemapi_misc_user_log_set
```

Samsung AP RILが送るCP User Log要求とCP側の`oemapi_misc_user_log_set`が名前・機能上で対応し、
同じOEM software内にDIAG service enable状態が存在することを裏付ける。ただし、S26側で
`diag_encrypt`文字列が見つからなかったことを暗号化機能不存在の証拠にはできない。モジュール分割、
圧縮、strip、名称変更の可能性がある。

### NSGがS26で読めることから言える範囲

取得したNSG APKのnative libraryにはOpenSSL/暗号関連コードと`Wrapped Key`、`public_key`等の
field-name文字列がある一方、固定列`12 33 08 01 00 00 00`を含むsecurity-key要求は見つからなかった。
要求の動的生成や難読化は除外できず、field-name辞書だけで「NSGが復号している」とも
「していない」とも確定できない。現在の候補は次の4つである。

1. S26/SJPではNSGが使うradio log IDが平文である。
2. Secure Loggingは一部ログだけに適用され、NSGは平文経路を使う。
3. NSG自身が鍵情報を取得し、内部で復号する。
4. CP User Log setterがendpoint公開と同時にCP内部のlogging policyを変更する。

Galaxy DIAGの要求に鍵・token・暗号packetがないため、どの候補でも
「Galaxy DIAGアプリ自身が復号する」という説明にはならない。決着には0xb0前後で同一log maskを設定し、
raw packetに`0x9e`が出るか、`SECURITY_KEY_LIST`応答が変わるかを読み取り専用で比較する必要がある。

## 旧資料の誤り

旧 `flip6-sc54e-analysis.md` にあった次の記述は撤回した。

- SC-54EのFUSを取得できない: **誤り**。公式FUSからEZIHを取得・検証できた。
- 5バイト要求はDomestic OEM: **誤り**。OEM MiscのCP User Logである。
- DCM sales code gateで拒否: **証拠なし**。AP側実行物はSJPと同一だった。
- `00 05`がサブコマンド: **誤り**。`00 05`は要求全長、サブコマンドは`b0`。
- `diag_mdlog`起動で非公開endpointが現れる: **根拠なし**。clientは未登録serviceを生成しない。
- `/dev/diag`を開けばQRTR非公開を回避できる: **この内蔵モデムについて未実証**。

## 再現可能な解析手順

公開リポジトリにはファームウェアやFUS認証情報を置かない。復号済み純正ZIPを私有作業領域へ
保存し、ハッシュを記録してから次の順で処理した。

```sh
unzip -t firmware.zip
unzip -Z1 firmware.zip
shasum -a 256 firmware.zip
unzip firmware.zip 'AP_*' 'CP_*' -d odin
tar -xf odin/CP_*.tar.md5 -C cp modem.bin.lz4
lz4 -d cp/modem.bin.lz4 cp/modem.bin
tar -xf odin/AP_*.tar.md5 -C ap super.img.lz4
lz4 -d ap/super.img.lz4 ap/super.img
python3 tools/extract_lp_partition.py ap/super.img parts \
  system vendor system_ext product odm
fsck.erofs --extract=libsec-ril.so \
  --path=/lib64/libsec-ril.so parts/vendor.img
objdump -T -C libsec-ril.so | rg 'CpUserLog|DoOemMisc'
objdump -d -C --start-address=0x3b8b90 --stop-address=0x3b95c0 libsec-ril.so
jadx -d jadx-tcommon telephony-common.jar
```

`tools/extract_lp_partition.py` はSamsungの巨大なsuper全体を別コピーせず、指定partitionのextent
だけを読み出す。今回のSC-54E実superでsystem/vendor/system_ext/product/odmを抽出して検証した。

## 追加検証が必要な事項

1. SC-54Eで公開済み状態を保持したまま、RIL/CP応答のraw errorと0xb0前後のログを採る。
2. 同一CPビルドに近いDCM/SJPを揃え、`qdsp6_oemsw.mbn` とcarrier MCFGの差を関数単位で比較する。
3. 直販SM-F741Qの実機で、初期QRTR状態と0xb0結果を初めて測定する。
4. S24実機とS25 AP/CPを調べ、同じアドレスではなくシンボル/XREF/命令列で対応関数を照合する。
5. S26上でDIAG 0x9eと0x0833の有無を受動的に記録し、公開とsecure logを分離する。
6. NSGについてはDCI受信と全画面・全ログ種別の復号対応を区別する。

## 参照

- [Samsung Galaxy S26 Ultra BZI9 firmware](https://www.sammobile.com/samsung/galaxy-s26-ultra/firmware/SM-S948Q/SJP/download/S948QOPU1BZI9/2190453/)
- [Samsung Galaxy Z Flip6 SC-54E EZIH firmware](https://www.sammobile.com/samsung/galaxy-z-flip-6/firmware/SC-54E/DCM/download/SC54EOMU1EZIH/2112026/)
- [NTTドコモ SC-54E 製品アップデート](https://www.docomo.ne.jp/support/product_update/sc54e/)
- [Samsung Galaxy S24 Ultra S928QOPU1EZK2 firmware](https://www.sammobile.com/samsung/galaxy-s24-ultra/firmware/SM-S928Q/SJP/download/S928QOPU1EZK2/2218961/)
- [Samsung Galaxy S25 Ultra firmware catalog](https://www.sammobile.com/samsung/galaxy-s25-ultra/firmware/)
- [Qualcomm qcom-log-collector](https://github.com/qualcomm/qcom-log-collector/tree/0a10daffba7556f03ff87786fa7e9b976d622c99)
- [Qualcomm qcom-log-collector User Guide](https://github.com/qualcomm/qcom-log-collector/blob/0a10daffba7556f03ff87786fa7e9b976d622c99/doc/USER_GUIDE.md)
- [qcom-band-menu](https://github.com/Fronsipswu/qcom-band-menu/tree/cddebf02c69f1dda021cf8d61f1a096fc2242278)
- [qcom-band-menu releases](https://github.com/Fronsipswu/qcom-band-menu/releases)
- [qcom-efs-browser](https://github.com/catxeon/qcom-efs-browser/tree/2cb426f655a525776726bcd360c30451c6e92c89)
- [Quectel QLog DIAG command definitions](https://github.com/quectel-official/QLog/blob/main/diagcmd.h)
