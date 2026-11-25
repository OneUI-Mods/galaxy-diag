# Samsung Galaxy Qualcomm DIAG ファームウェア比較

調査日: 2026-11-25

対象: Galaxy DIAG `51e9e4e`、Samsung Android 17 純正ファームウェア

手法: FWは読み取り専用で取得・展開・静的解析した。S26実機ではCP User Logを一度OFF→ONにし、
QRTR登録を100 ms間隔で記録した。NV/EFS書換え、モデム/OS再起動、セキュリティ設定変更は行わず、
調査後はDIAG公開状態と前面アプリを開始前へ戻した。

## 結論

### 確定

1. Galaxy DIAG の5バイト要求 `11 b0 00 05 01` は「Domestic OEM コマンド」ではない。
   Samsung RIL の OEM Misc ディスパッチャでサブコマンド `0xb0` に分岐し、
   `OemMiscManager::DoOemSetCpUserLog` を呼ぶ **CP User Log 設定要求**である。
2. RIL は最終的に8バイト `08 00 00 00 0a 7a 03 <mode>` をCPへ送る。
   `<mode>` は有効化時 `01`、無効化時 `00` である。
3. SC-54Eがアプリへ返す `-37` はLinux errnoでもraw RIL errnoでもない。
   `ISemTelephony` が `CommandException.Error.INTERNAL_ERR.ordinal()` を負数化した値である。
4. 日本直販SJPとドコモDCMの比較対象AP側DIAG/RIL実行物は、Flip6、S24 Ultra、
   S25 Ultra、S26 Ultraの全4世代でSHA-256まで一致した。少なくとも「DCM版AP RILだけ0xb0が未実装」や
   「SJP/DCMでdiag-routerの起動設定が違う」という説明は否定できる。
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
10. X75の実装をGhidraで追跡し、`/nv/item_files/diag/diag_encrypt` の値0〜3を
    2系統の内部security設定へ変換することを確認した。ファイルが存在しない場合は
    `Config file does not exist`を記録して設定を変更せず戻る。個々の値の正式名称は
    シンボルがないため断定しないが、実行時状態が端末固有のモデムEFSに依存することは確定した。
11. Flip6、S24、S25、S26のAPAC/DCM carrier MCFGをSJPパッケージとDCMパッケージの双方から
    展開した。`parsed_nv_files` と `parsed_nv_items` は各世代で完全一致し、
    `diag_encrypt` やDIAG公開設定は含まれない。DCM carrier MCFGは今回の差の制御点ではない。
12. 同一リリースのS26 Ultra BZI9では、SJP/DCMのAP側7ファイルが全て一致し、CPの主要segmentも
    `modem.b00`〜`b26`が一致する。一方、`b27`、`qdsp6_oemsw.mbn`、RF/HW MCFGは異なる。
    これはリージョン共通のQualcomm MPSS本体と、機種・無線構成別OEM層を分離した構成を示す。
13. S26実機でロックされている対象を確定した。OFF時にもMODEMのservice 4097
    `CNTL`/`DATA`/`DCI`（instance 0/2/4）は残り、`CMD`/`DCI_CMD`（instance 1/3）だけが消える。
    OEM ONでは要求から約0.87秒後にinstance 1/3が登録され、OFFでは約0.99秒後に同じ2本だけが
    解除された。制御点はAPの`diag-router`ではなく、CP側のcommand endpoint登録である。
14. 解放後のS26実機へ通常のEFS2 DIAG commandで接続し、
    `/nv/item_files/diag/diag_encrypt`が存在しない（EFS errno 2）ことを確認した。従って少なくとも
    この実機状態では、Galaxy DIAG成功を同ファイルの復号設定変更では説明できない。
15. S26 SJP/DCMの`qdsp6m.qdb`はGUIDとrecord順序こそ異なるが、全74,458件をtoken列を除いて
    正規化・sortすると完全一致した。`oemapi_misc.c:9731`の`status : %d`も両方ともhash
    `124124`である。CP OEM binaryの大差をそのまま機能差と数えることはできず、少なくとも
    QShrinkのsource/log定義は同一である。
16. 上記hashを参照するCP handlerはSJP/DCMとも`0x386e9a58`の同一関数で、命令列と
    逆コンパイル結果も同一だった。requestの`+0x1c`からmodeを読み、初回または値変更時に同じ
    再計算処理へ進む。SJP/DCM差をこのhandlerのリージョン分岐や実装欠落では説明できない。

### 有力な推定

- S26 SJP/DCMのCP OEM handlerは同じ実装であるため、DCM側に差がある場合はhandlerの有無や
  分岐定数ではなく、factory EFS/NV、起動時の永続状態、RF/HW feature入力の可能性が高い。
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
- S26 Ultraで全async DIAGログが平文になるのか、一部のsecure logだけ別処理を要するのか。
- NSGが `SECURITY_KEY_LIST` を動的生成して鍵情報を取得するか。APK内に固定要求列はなく、
  暗号関連コードとfield-name文字列だけでは復号実装の証拠にならない。
- ブートローダー、Verified Boot、Knox Warranty Bitが別のDIAG経路へ与える影響。

## SJP直販版とドコモDCM版の比較表

実機結果と静的FW解析は分けて記載する。「Secure Logging実装あり」と
「その端末で実行時に暗号化ON」は別の状態である。

| 世代 | SJP直販 | ドコモDCM | AP側DIAG/RIL | CP/MCFG静的比較 | 実行時の結論 |
|---|---|---|---|---|---|
| **Z Flip6 / X75** | SM-F741Q EZJD | SC-54E EZIH | 対象7ファイルは全て一致 | MPSS Build IDは同じ。CP/OEM SWはビルド日差で異なる。DCM carrier MCFGの意味内容は一致しDIAG項目なし | DCM実機は初期公開・0xb0は`-37`。SJP実機状態はFUSだけからは決まらない |
| **S24 Ultra / X75** | SM-S928Q EZK2 | SC-52E EZIF | 対象7ファイルは全て一致 | CP/OEM SWはビルド日差で異なる。DCM carrier MCFGの意味内容は一致しDIAG項目なし。両方にX75 Secure Logging実装あり | 両方ともfactory EFSがFUSに含まれないため、初期ON/OFFは静的FWからは決まらない |
| **S25 Ultra / X80** | SM-S938Q DZIF | SC-52F DZIF | 対象7ファイルは全て一致 | MPSS Build IDは同一。SJP/DCMでCP本体・OEM SW・RF構成に差。DCM carrier MCFGの意味内容は一致しDIAG項目なし | factory EFSがFUSに含まれないため、初期ON/OFFは静的FWからは決まらない |
| **S26 Ultra / X85** | SM-S948Q BZI9 | SC-53G BZI9 | 対象7ファイルは全て一致 | MPSS Build ID同一、`b00`〜`b26`一致。OEM `b27`/`qdsp6_oemsw`とRF構成は別だが、OEM QDB 74,458件の意味内容は一致。DCM carrier MCFGの意味内容も一致 | SJP実機はCMD/DCI_CMDだけ初期非公開→0xb0で公開・NSG可。実機`diag_encrypt`は不在。DCMのfactory EFS状態はFUSにはない |

この表は日本向けSJP/DCMだけを扱う。海外版の推測は比較対象から除外した。

「初期DIAG endpoint」と「実行時の暗号化ON/OFF」がFW欄から埋まらない箇所は、調査不足による
単なる`?`ではない。Ghidraで特定した通り、X75ではモデムEFSの
`/nv/item_files/diag/diag_encrypt`を起動時に読み、そのファイルはFUSのAP/CP/CSCへ含まれない。
factory EFSの出荷値は純正FWアーカイブから復元できないため、同一バイナリでも端末の出荷設定・
更新履歴で状態が変わり得る。これが静的解析で確定できる境界である。

この表で「全async logが暗号化されていない」と確定できるSamsung機種はまだない。NSGで値が見えることは、
全async logが平文である証明にならない。一方で「X80/X85だから全DIAGが暗号化」も誤りで、
X85 S26では通常のEFS DIAG command/replyが実機で利用でき、公開プロジェクトもX80/X85を
**without encryptionという条件付き**で対応している。

リージョンについて実機から断定できるのは次の2点だけである。

- DCM版Flip6はDIAG初期ロックされていない。
- SJP版S26 Ultraは初期ロックされているが、比較対象となる別リージョンS26実機がないため、
  「SJPリージョンロック」ではなく「このS26/CP構成での初期ロック」と表現するのが正確である。

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
3. **carrier/region MCFG** — 署名を含むMCFGファイル全体のhashはDCM/SJPで異なるが、
   各世代のDCM profileを展開した意味内容は完全一致し、DIAG項目もない。さらに全4世代の
   AP側RILは同一機種のSJP/DCMでbyte-identicalだった。従ってcarrier MCFGとAP sales-code分岐は、
   今回観測したendpoint初期状態の直接原因ではない。
4. **世代差** — X80/X85ではschemaや4-byte QRTR header等の変更があるが、世代だけで
   暗号化状態を決める証拠はない。
5. **host tool差** — NSG、qcom-band-menu、qcom-efs-browserは要求するDIAG command/log IDが
   異なる。一つが読めることは、全DIAGログが平文であることを意味しない。

したがって「ドコモだから公開」「S24だから暗号化」「X85だから暗号化」という単一軸の結論は、
いずれも現時点の証拠を超える。確定できるのは、全4世代で同一機種のSJP/DCM AP経路が同一、
carrier MCFGの意味内容も同一であり、差が残るのは機種別CP OEM層とFUS外のfactory EFSであること、
そして実機結果はS26直販とSC-54Eの2台に限られることである。

## 取得したファームウェア

| 機種/CSC | ビルド | Android | 取得元 | 復号後ZIP SHA-256 | 状態 |
|---|---|---:|---|---|---|
| SM-S948Q / SJP | S948QOPU1BZI9 | 17 | Samsung FUS | `04fefa53a229e5d1274928874511c4585db16ac6eb4ca0ce681dc9546efc1200` | AP/CP展開済み |
| SC-54E / DCM | SC54EOMU1EZIH | 17 | Samsung FUS | `96cd2edba8785a16de30f9ec7c632c9b726cdee41b6ed1f87fcc0f07f7da0e15` | AP/CP展開済み |
| SM-F741Q / SJP | F741QOPU1EZJD | 17 | Samsung FUS | `a87ee76deb6585a6b212d45760683086b64c64b8b46411b160062335459bdc77` | AP/CP展開済み |
| SM-S928Q / SJP | S928QOPU1EZK2 | 17 | Samsung FUS | `8149c373e58ebe33432409b528dcdaa2f405dccc176c7dd9732336b25f11fde0` | AP/CP展開済み |
| SC-52E / DCM | SC52EOMU1EZIF | 17 | Samsung FUS | `3a574588cd9df0aa76c58b4e3b5c19898dabab48c3a88e0eb5ff0c30b3a1f507` | AP/CP展開済み |
| SM-S938Q / SJP | S938QOPU1DZIF | 17 | Samsung FUS | `c79be7c4406ebc9296af277260f5fbc12301ea37dbbea43ddd29bb85cbe59139` | AP/CP展開済み |
| SC-52F / DCM | SC52FOMU1DZIF | 17 | Samsung FUS | `aa52e77ee9b8412b3fc83febd23c41fd0b26a35d34bcf4109aa2e7667fca0441` | AP/CP展開済み |
| SC-53G / DCM | SC53GOMU1BZI9 | 17 | Samsung FUS | `e01a377deb07b8e56a66bcc14e696ca4c13d97d09c2b867a317e96cf2e2065cc` | AP/CP展開済み |

SC-54EのFUSアーカイブ名は
`SC-54E_4_20260921143541_kcamerig1l_fac.zip.enc4`、復号後はAP
`SC54EOMU1EZIH`、CP `SC54EOMU1EZIH_CP36479396`、CSC `SC54EQCM1EZIH` を含む。
ZIP全体を `unzip -t` で検査し、全5メンバーが正常だった。

S24 UltraのFUSアーカイブ名は
`SM-S928Q_4_20261103114740_s5jf3o34wr_fac.zip.enc4`、CPは
`S928QOPU1EZK2_CP37198199`である。復号後ZIP全体を`unzip -t`で検査し、全5メンバーが正常だった。
CP `modem.bin` SHA-256は
`15d39aff0b6c8f5b90179dfef4a1aec18f82ab1fd3bd88b6cea797c2f5666d86`である。

S25 UltraはSJP/DCMとも同じ`DZIF`、S26 UltraもSJP/DCMとも同じ`BZI9`を選び、
Android版、セキュリティパッチ、Samsung MQB/changelistを揃えた。Flip6とS24 Ultraは
FUS上で同日同一ビルドの対が得られなかったため、APの対象ファイル一致とCPの意味比較を分けて扱う。

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

## S26実機で確定したCP側の公開制御

対象はSM-S948Q / SJP / `S948QOPU1BZI9`、SELinux Enforcing、ブートローダーロック状態である。
Galaxy DIAG 0.2.4のOEM ON/OFFを各1回だけ実行し、`qrtr-lookup 4097`と`diag-router`ログを
同時に記録した。端末、SystemUI、RIL、モデムの再起動は行っていない。

### OFF状態

OFFでもデータ経路全体が消えるわけではなく、次の3本はAPと同じnode 1上に残った。

```text
instance 0  node 1  MODEM:CNTL
instance 2  node 1  MODEM:DATA
instance 4  node 1  MODEM:DCI
```

一方、EFS2 commandを送るために必要なCMD endpointがないため、rootやAF_QIPCRTR socketが
利用可能でもEFS commandは送れなかった。これは「service 4097が1本でも見えればDIAG公開済み」
という判定が誤りであることも示す。

### OEM ON

```text
21:17:28.191  SEM_RILJ  OEM_HOOK_RAW[11B0000501]
21:17:28.191  RILD      DoOemSetCpUserLog - status : 1
21:17:29.064  Diag-Router  new server on cmd channel
21:17:29.067  Diag-Router  new server on dci cmd channel
```

要求から約0.87秒後、次の2本だけがnode 0へ新規登録された。portは再登録のたびに変わるため
policy値ではなくruntime割当である。

```text
instance 1  node 0  MODEM:CMD
instance 3  node 0  MODEM:DCI_CMD
```

`diag-router`はNEW_SERVER通知を受けてから接続・diag-id交換を開始しており、公開を作る側ではない。

### OEM OFF

```text
21:16:45.350  SEM_RILJ  OEM_HOOK_RAW[11B0000500]
21:16:45.357  RILD      DoOemSetCpUserLog - status : 0
21:16:46.341  Diag-Router  DEL_SERVER / bye on cmd channel
21:16:46.343  Diag-Router  bye on dci cmd channel
```

OFF要求から約0.99秒後にinstance 1/3だけが消え、CNTL/DATA/DCIは残った。従って5バイト要求は
少なくともS26で**CPのCMD/DCI_CMD QRTR server登録を切り替える**。この事実は暗号鍵の取得、
ログpayloadの復号、AP側ルーター起動とは別である。

### 実機EFSと`diag_encrypt`

ON後はMODEM:CMDへ通常のDIAG/EFS2 requestを送り、subsystem `0x13`で応答を得た。
その状態で`/nv/item_files/diag/diag_encrypt`を`stat`/`GET`した結果はEFS errno 2、親directory
`/nv/item_files/diag`も不存在だった。調査はread-onlyのまま行い、EFS/NVへ書き込んでいない。

これはX75 binaryに同ファイルを読む実装があることと矛盾しない。ファイルがない場合は既定値のまま
戻る分岐があり、X85実機ではその不在状態だった、という結果である。少なくとも今回のOEM ONで
同ファイルが作成・変更された事実はなく、解放を「`diag_encrypt`の復号スイッチ」とする説明は棄却できる。

### S26 SJP/DCM CP OEMの正規化比較

`qdsp6_oemsw.mbn`自体はSJPとDCMで異なるが、raw byte差にはbuild番号、link配置、QShrink token順序が
大量に含まれる。対応する`qdsp6m.qdb`を展開すると、両方ともQShrink 00.10.00、74,458件で、
token/hash列を除いてsortしたrecord集合はbyte-for-byte一致した。

```text
Content          40,548
MtraceContent    33,207
QtraceStrContent    703
total            74,458
```

`oemapi_misc_user_log_set`近傍も両方に存在し、`oemapi_misc.c:9731`の`status : %d`は同じhash
`124124`である。さらに、PT_NULL扱いのHexagon実行segmentをGhidraへ追加mapし、同hashの唯一の
参照元をSJP/DCMで独立に探索した結果、両方とも次の同じ場所・同じ命令へ到達した。

```text
function entry  0x386e9a58
hash reference  0x386e9a8c
instruction     combine R1R0,R17,##0xf60f26e0
QShrink hash    124124 (oemapi_misc.c:9731, "status : %d")
```

逆コンパイル結果もSJP/DCMで同一であり、相違はログ用data pointerの配置だけだった。処理本体は、
null requestなら`0x8001`、通常は`0x8000`を返す。requestの`+0x14`が0の場合に`+0x1c`の1-byte
modeを読み、前回値と異なるか初回なら`0x38236fc0`を呼び、その後GP相対`+0x4dd`へmodeを保存する。

```c
if (request == NULL) return 0x8001;
result = 0x8000;
if (*(int8_t *)(request + 0x14) == 0) {
    mode = *(int8_t *)(request + 0x1c);
    log("status : %d", mode);
    if (initialized == 0 || cp_user_log_mode != mode) {
        FUN_38236fc0();
        initialized = 1;
    }
    cp_user_log_mode = mode;
}
return result;
```

従って、S26 BZI9のSJP/DCM間では、少なくともCP User Log要求の受付、mode offset、初回・変更時判定、
状態保存、再計算処理への入口まで完全に同じである。SJP実機の初期非公開状態を、このhandlerの
リージョン別分岐やDCM固有の命令欠落で説明することはできない。差を作り得る残りの候補は、同handlerが
参照する永続/製品状態、factory EFS、または起動時に設定される別のCP状態である。

`0x38236fc0`以降には遅延処理と複数の製品状態を合成する処理があることまで確認したが、ユーザーの指示に
よりここで解析を停止した。最終的なQRTR server登録関数の命名と各bitの正式名称は未解析であり、推測で
補っていない。

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

## 日本向けSJP/DCMの同一機種比較

### AP側

各パッケージの`vendor`から次の7ファイルを直接抽出し、SHA-256と`cmp`で比較した。

```text
lib64/libsec-ril.so
lib64/libsec_semRil.so
lib64/libdiag.so
bin/diag-router
bin/diag_mdlog
bin/qrtr-lookup
etc/init/vendor.qti.diag.rc
```

結果はFlip6、S24 Ultra、S25 Ultra、S26 Ultraの**全4世代で、同一機種のSJP/DCM間が
7/7ファイル完全一致**だった。S25の`libsec-ril.so`は両方
`c837d998726e4e5503c46d7ca63b611543f3acfb7b9fc0743936c46861e8afba`、
S26では既取得SJPとDCMを含め7/7が一致した。このため販売経路別AP RIL、diag-router、
libdiagの差を今回の初期公開状態の原因にはできない。

### CP側

| 世代 | SJP `modem.bin` | DCM `modem.bin` | MPSS Build ID | 主な差 |
|---|---|---|---|---|
| Flip6/X75 | `2914b9df...b35b61` | `00c8e7f2...1bff27b` | 同一 | リリース日、OEM SW、MCFG/RF構成 |
| S24/X75 | `15d39aff...66d86` | `efd71c3c...5756b2` | `MPSS.DE.5.0-02520.3-LANAI_GEN_PACK-1.62174.441`で一致 | リリース日、OEM SW、MCFG/RF構成 |
| S25/X80 | `7d8cb75c...e5e08` | `cfef07fd...0732a2` | `MPSS.DE.7.0-02170.5-PAKALA_GEN_PACK-1.85011.419`で一致 | Qualcomm buildは同じだがMPSS segment、OEM SW、RF/HW構成は別生成 |
| S26/X85 | `a5e0ada5...7c881` | `59848272...760ff` | `MPSS.DE.9.0-01972.5-KAANAPALI_GEN_PACK-1.129782.405`で一致 | `modem.b00`〜`b26`は一致、`b27`とOEM SW、RF/HW構成が異なる |

CPパッケージ全体が異なること自体は、DIAG暗号化状態の差を意味しない。各世代の
`APAC/DCM/Commercial/mcfg_sw.mbn`を`mbn-mcfg-tools`で展開すると、SJP格納版とDCM格納版の
`parsed_nv_files`・`parsed_nv_items`は完全一致した。中身はcarrier policy、緊急番号、
NV 71/880/881/882等で、`diag_encrypt`、DIAG endpoint、CP User Logの設定はない。

したがって、現時点の差分候補はCPの機種別OEM softwareとFUS外のfactory modem EFSへ絞られる。
特にS26ではQualcomm MPSS本体の主要segmentが一致しているため、SJP実機の初期ロックを
「SJPのQualcomm本体だけが暗号化版」と説明することはできない。

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

QDigのQualcomm split-ELF loaderを追加したGhidra 12.2でFlip6 SJPの`modem.mdt`を解析すると、
設定初期化関数は`0xbcdd9278`、security設定反映関数は`0xbcdd7c60`に復元された。
初期化関数の主要処理は次の通りである。

```c
if (!efs_exists("/nv/item_files/diag/diag_encrypt")) {
    log("Config file does not exist");
    return;
}
if (efs_get(path, &value, 1) != 1) {
    log("Error returned by efs_get()");
    return;
}
switch (parse_decimal(value)) {
case 0: security_set(1, {0, 0}); security_set(2, {0, 0, 0, 0}); break;
case 1: security_set(1, {1, 1}); break;
case 2: security_set(2, {1, 0, 0, 0}); break;
case 3: security_set(2, {1, 1, 0, 0}); break;
default: log("Unrecognized value");
}
```

この逆コンパイルから確定できるのは、値0〜3が2系統のsecurity状態を選ぶことと、
ファイルがなければ既定値のまま戻ることまでである。内部setterの第2系統には
`Secure ORANGE bypass settings`というログがあるが、第1系統と各値の正式名称はstrip済みbinaryだけでは
確定できないため、「0=平文、1=暗号化」のような名称付けは行っていない。

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
MTOOLS_SKIP_CHECK=1 mcopy -s -i cp/modem.bin '::*' cp/modem-files/
tar -xf odin/AP_*.tar.md5 -C ap super.img.lz4
lz4 -d ap/super.img.lz4 ap/super.img
python3 tools/extract_lp_partition.py ap/super.img parts \
  system vendor system_ext product odm
fsck.erofs --extract=libsec-ril.so \
  --path=/lib64/libsec-ril.so parts/vendor.img
objdump -T -C libsec-ril.so | rg 'CpUserLog|DoOemMisc'
objdump -d -C --start-address=0x3b8b90 --stop-address=0x3b95c0 libsec-ril.so
jadx -d jadx-tcommon telephony-common.jar
mbn-tool -e \
  cp/modem-files/image/modem_pr/mcfg/configs/mcfg_sw/generic/APAC/DCM/Commercial/mcfg_sw.mbn \
  mcfg-dcm
diff -ru mcfg-sjp/parsed_nv_files mcfg-dcm/parsed_nv_files
diff -ru mcfg-sjp/parsed_nv_items mcfg-dcm/parsed_nv_items
python3 tools/compare_cp_oem_elf.py \
  s26-sjp/image/modem_oem/qdsp6_oemsw.mbn \
  s26-dcm/image/modem_oem/qdsp6_oemsw.mbn
qdb dump s26-sjp/image/modem_oem/qdsp6m.qdb | cut -f2- | LC_ALL=C sort > sjp.qdb.sorted
qdb dump s26-dcm/image/modem_oem/qdsp6m.qdb | cut -f2- | LC_ALL=C sort > dcm.qdb.sorted
cmp sjp.qdb.sorted dcm.qdb.sorted
/opt/homebrew/opt/ghidra/libexec/support/analyzeHeadless PROJECTS Flip6Diag \
  -import cp/modem-files/image/modem.mdt \
  -scriptPath tools/ghidra \
  -postScript DiagStringXrefs.java
/opt/homebrew/opt/ghidra/libexec/support/analyzeHeadless PROJECTS S26OemFull \
  -import s26-sjp/image/modem_oem/qdsp6_oemsw.mbn \
  -scriptPath tools/ghidra \
  -preScript MapEmbeddedExecutableSegments.java \
    s26-sjp/image/modem_oem/qdsp6_oemsw.mbn \
    0xc2fc80:0x38000000:0x7a3ab4 \
    0x13d3734:0x3c000000:0x3d16fc
```

`tools/extract_lp_partition.py` はSamsungの巨大なsuper全体を別コピーせず、指定partitionのextent
だけを読み出す。今回のSC-54E実superでsystem/vendor/system_ext/product/odmを抽出して検証した。

## 追加検証が必要な事項

1. SC-54Eで公開済み状態を保持したまま、RIL/CP応答のraw errorと0xb0前後のログを採る。
2. 直販SM-F741Q、S24 Ultra、S25 Ultraの実機で、初期QRTR状態、0xb0結果、
   `/nv/item_files/diag/diag_encrypt`の存在と値を読み取り専用で測定する。
3. S26上でDIAG 0x9eと0x0833の有無を受動的に記録し、公開とsecure logを分離する。
4. NSGについてはDCI受信と全画面・全ログ種別の復号対応を区別する。
5. X80/X85のSecure Logging初期化関数を、X75と同じくsplit-ELF全体から関数単位で追跡する。

## 参照

- [Samsung Galaxy S26 Ultra BZI9 firmware](https://www.sammobile.com/samsung/galaxy-s26-ultra/firmware/SM-S948Q/SJP/download/S948QOPU1BZI9/2190453/)
- [Samsung Galaxy Z Flip6 SC-54E EZIH firmware](https://www.sammobile.com/samsung/galaxy-z-flip-6/firmware/SC-54E/DCM/download/SC54EOMU1EZIH/2112026/)
- [NTTドコモ SC-54E 製品アップデート](https://www.docomo.ne.jp/support/product_update/sc54e/)
- [Samsung Galaxy S24 Ultra S928QOPU1EZK2 firmware](https://www.sammobile.com/samsung/galaxy-s24-ultra/firmware/SM-S928Q/SJP/download/S928QOPU1EZK2/2218961/)
- [Samsung Galaxy S25 Ultra S938QOPU1DZIF firmware](https://www.sammobile.com/samsung/galaxy-s25-ultra/firmware/SM-S938Q/SJP/download/S938QOPU1DZIF/2190499/)
- [Samsung Galaxy S25 Ultra SC52FOMU1DZIF firmware](https://www.sammobile.com/samsung/galaxy-s25-ultra/firmware/SC-52F/DCM/download/SC52FOMU1DZIF/2030381/)
- [Samsung Galaxy S26 Ultra SC53GOMU1BZI9 firmware](https://www.sammobile.com/samsung/galaxy-s26-ultra/firmware/SC-53G/DCM/download/SC53GOMU1BZI9/2028432/)
- [Qualcomm qcom-log-collector](https://github.com/qualcomm/qcom-log-collector/tree/0a10daffba7556f03ff87786fa7e9b976d622c99)
- [Qualcomm qcom-log-collector User Guide](https://github.com/qualcomm/qcom-log-collector/blob/0a10daffba7556f03ff87786fa7e9b976d622c99/doc/USER_GUIDE.md)
- [qcom-band-menu](https://github.com/Fronsipswu/qcom-band-menu/tree/cddebf02c69f1dda021cf8d61f1a096fc2242278)
- [qcom-band-menu releases](https://github.com/Fronsipswu/qcom-band-menu/releases)
- [qcom-efs-browser](https://github.com/catxeon/qcom-efs-browser/tree/2cb426f655a525776726bcd360c30451c6e92c89)
- [go-qshrink4](https://github.com/W-Floyd/go-qshrink4)
- [QDig](https://github.com/NerdTIV/QDig)
- [Quectel QLog DIAG command definitions](https://github.com/quectel-official/QLog/blob/main/diagcmd.h)
