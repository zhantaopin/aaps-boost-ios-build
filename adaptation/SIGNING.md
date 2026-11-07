# 签名说明

本仓库**不包含**任何 keystore、密码或凭据。签名材料离线保管。

## 生产签名要求

- 使用私有 keystore，证书不得出现在 AAPS 的吊销列表中（demo/debug 证书会被 AAPS 拒绝）
- 开启 v1 + v2 + v3 签名，保证 Android 8.1 可安装
- keystore 与密码至少两份离线备份

当前产物的证书指纹（仅用于核对，不能用于推导密钥）：

```
SHA-256: ed5fce41021b7aeeff02d4766e7fd2bb510f1d0f691292be4bc3943a2758dfee
```

## 命令模板

```bash
BT="$ANDROID_HOME/build-tools/37.0.0"
KS="/path/to/private_keystore.jks"
ALIAS="<alias>"

"$BT/zipalign" -p -f 4 \
  app/build/outputs/apk/full/release/app-full-release-unsigned.apk \
  /tmp/boost-aligned.apk

"$BT/apksigner" sign \
  --ks "$KS" --ks-key-alias "$ALIAS" \
  --ks-pass env:KS_PASS --key-pass env:KEY_PASS \
  --out Boost-6.3.0-api27-full-release.apk \
  /tmp/boost-aligned.apk

"$BT/apksigner" verify --print-certs Boost-6.3.0-api27-full-release.apk
```

密码通过环境变量传入，避免进入 shell 历史。

## 版本号

`versionCode`/`versionName` 由 `buildSrc/src/main/kotlin/Versions.kt` 控制；升级安装时
`versionCode` 必须不小于已安装版本。
