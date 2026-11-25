# SC-54E（Galaxy Z Flip6 ドコモ版）DIAG解析

旧版に含まれていた「Domestic OEM」「DCMのsales code gate」「FUS取得不可」という仮説は、
SC-54Eの純正EZIHファームウェアを実際に取得して比較した結果、撤回しました。

現在の結論、ハッシュ、関数アドレス、逆アセンブル結果、未確認事項、再現手順は
[Samsung Galaxy Qualcomm DIAG ファームウェア比較](qualcomm-diag-firmware-comparison.md)を参照してください。

重要な訂正は次の3点です。

- `11 b0 00 05 01` はDomestic OEMではなく、OEM MiscのCP User Log設定です。
- `RIL_RESULT=-37` はLinux errnoではなく、Samsung Telephonyが返す`INTERNAL_ERR`の負の序数です。
- SC-54Eは要求前からDIAGを公開しているため、RIL拒否とDIAG公開状態は同じ意味ではありません。
