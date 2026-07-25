# Third-party notices

HyperTweaks 的项目代码采用仓库根目录 `LICENSE` 所述条款。下列第三方项目、工具或依赖继续适用其各自许可证，本文件不替代上游许可证正文。

## 源码与思路来源核查

- Gradle Wrapper 脚本来自 Gradle，脚本头部声明 Apache License 2.0。
- IME 功能方向参考 [Mi_AOSP_IME](https://github.com/Howard20181/Mi_AOSP_IME)。当前仓库未包含该项目的源文件、资源文件或二进制副本；若后续引入或改写上游代码，必须在对应文件和本清单中补充来源、版本、文件范围与许可证。
- LSPosed、libxposed 与 AOSP 用于 API、运行时行为和兼容性参考，不表示其源码被重新许可为 HyperTweaks 项目代码。

## 构建依赖

项目通过 Gradle/Maven 使用 AndroidX、Jetpack Compose、Room、Kotlin、kotlinx.coroutines、libxposed 和 Miuix 等依赖。发布 APK 中包含的第三方二进制及资源适用其上游许可证和 notice。

每次增加复制、改写、内嵌或重新分发的第三方文件时，提交者必须同步更新本文件，并记录：

- 上游项目与版本或 commit；
- 引入或派生的项目相对路径；
- 上游许可证；
- 必需的版权声明与 attribution。
