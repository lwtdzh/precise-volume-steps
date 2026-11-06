# 1.5.4 发布包构建核对

主应用：1.5.4/code32。发布文件：
`dist/precise-volume-steps-1.5.4.apk`，SHA-256：
`cf3cf384ec0b5a6db9584dba363ccf29397256268cf02f6c0e80494756912913`。

内置助手仍为1.4，SHA-256：
`bfd940d48df27d3a7226d3bc35635477977fd459706718db47a4db93e0e1979f`。
从主 APK 的 `assets/volume-helper.apk` 解出的字节与本轮 helper APK 一致。
主/helper 证书与已分发1.5.3一致，可覆盖升级；签名材料未加入仓库。

## 本轮验证

使用 JDK21、Android `platforms;android-37.0` 和 build-tools36.0.0，
从 `0ed2984` 加本次 Root 状态色、版本号及文档差异构建。源码差异与发布文件
包含在1.5.4的发布提交中；APK中的构建元数据不能替代源码差异记录。

```sh
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
bash gradlew --offline :assembleRelease :lintRelease :testReleaseUnitTest \
  :compatibility-helper:lintRelease :compatibility-helper:testReleaseUnitTest \
  --rerun-tasks
```

113个 Gradle task 本轮实际执行。真实单测数量来自 XML：

| 模块 | tests | failures | errors | skipped | lint Error/Fatal | 非致命 lint 诊断 |
|---|---:|---:|---:|---:|---:|---:|
| main release | 67 | 0 | 0 | 0 | 0 | 30 |
| helper release | 3 | 0 | 0 | 0 | 0 | 1 |

`aapt2 dump badging` 确认实际 APK 为1.5.4/code32。
`apksigner verify --print-certs` 核对本轮main/helper与此前分发主包证书相同。
发布文件的SHA-256已登记到 [SHA256SUMS.txt](SHA256SUMS.txt)；
GitHub发布流程还会下载远端附件，再核对这个hash。

构建原输出保留在Git外。本报告记录构建、既有单测和打包核对，
不计为模拟器界面或物理按键测试。

## 真机记录与未验证项

本轮发布前，Root状态色修复的1.5.3/code31开发包已安装到Vivo V2419A，
其hash为 `6af6e3db0291e79d0c95a4596f71393e68fb05828c1110963fa7bb93c5613255`。
安装Success、签名/实际APK字节和服务恢复均核对；当时保留Shizuku/50阶、
121/150、idle及原完整无障碍服务。没有切Root或实际检查Root就绪文字颜色。
1.5.4/code32未在本轮安装或重跑真机矩阵。

按键修复的较早开发包真机结果见
[11月6日验证报告](verification-20261106-volume-keys.md)。
该报告的通过数量和物理边沿不记为1.5.4全套重跑；无播放漏键/快捷接管、
Root原超时反例、pending物理开屏和其他重启/route/方向/时长组合仍保留。

本轮137项计划没有全量执行。后续按
[迭代计划](../.trae/skills/precise-volume-regression/references/test-plan.md)
和[执行模板](../.trae/skills/precise-volume-regression/references/test-run-template.md)
逐组合记录。Root颜色反馈UF-084已映射到PV-D07，当前共84项历史反馈。
