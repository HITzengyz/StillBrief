# 发布 StillBrief

2.0 使用 release 构建（不可调试），版本号2.0、versionCode 12。公开下载文件为 `StillBrief-2.0.apk`。

## 构建与签名

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
./gradlew connectedDebugAndroidTest
bash scripts/sign-release.sh /path/to/StillBrief-2.0.apk
```

设置 JDK17、`ANDROID_HOME`，安装 Android SDK35 与 build-tools35.0.0。签名脚本默认使用本地 `.signing/`，也可用 `STILLBRIEF_SIGNING_DIR` 指定私有备份目录。

发布者需要备份整个 `.signing/`，其中包含 `release.jks`、`release-password.txt`、`legacy.keystore` 与 `release.lineage`。这些文件不进入Git，也不作为Release附件。后续版本必须沿用同一组签名材料。

2.0通过 Android 签名轮换从早期本地调试证书迁移到 StillBrief 正式证书：Android9及以上使用新证书，Android8沿用旧证书，以保留既有安装的升级链。使用其他签名（例如 Actions 自动生成的调试证书）安装的版本不在这条升级链内。签名方式依据 [Android apksigner](https://developer.android.com/tools/apksigner)。

## 发布

检查APK版本与签名、覆盖升级后的配置、单次与循环模式。GitHub创建 `v2.0` 正式Release，附件只上传APK及其SHA256校验文件，不勾选预发布。源码由GitHub随标签提供。
