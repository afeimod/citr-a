# Mandarine Android 构建指南

## 概述

本指南详细介绍如何构建和发布Mandarine Android应用程序。Mandarine是一个高性能的Nintendo 3DS模拟器，支持多种Android设备。

## 环境要求

### 系统要求

- **操作系统**：Linux（推荐Ubuntu 22.04或更高版本）、macOS、Windows
- **内存**：至少8GB RAM（推荐16GB）
- **磁盘空间**：至少20GB可用空间
- **网络**：稳定的互联网连接（用于下载依赖）

### 开发工具要求

#### 必需工具

| 工具 | 版本要求 | 说明 |
|------|----------|------|
| JDK | 17或更高 | Android构建必需，推荐使用Temurin发行版 |
| Android SDK | API Level 35 | 包含编译和构建工具 |
| Android NDK | 27.0.12077973 | 用于编译C/C++原生代码 |
| Gradle | 8.10或更高 | 项目使用Gradle Wrapper，无需单独安装 |
| CMake | 3.22.1或兼容版本 | 原生代码构建系统 |

#### 可选工具

| 工具 | 版本要求 | 说明 |
|------|----------|------|
| CCache | 4.9或更高 | 加速重复构建 |
| Android Studio | Ladybug或更高 | 集成开发环境 |

## 项目结构

```
mandarine/
├── src/
│   └── android/              # Android项目目录
│       ├── app/             # Android应用模块
│       │   ├── src/
│       │   │   ├── main/    # 主源代码目录
│       │   │   └── test/    # 测试代码目录
│       │   ├── build.gradle.kts      # 应用模块构建配置
│       │   └── proguard-rules.pro    # ProGuard规则文件
│       ├── build.gradle.kts          # 项目级构建配置
│       ├── settings.gradle.kts       # 项目设置
│       ├── gradle.properties          # Gradle属性配置
│       ├── local.properties          # 本地SDK配置
│       ├── gradlew                   # Gradle Wrapper脚本
│       └── gradlew.bat              # Windows批处理脚本
├── .github/
│   └── workflows/           # GitHub Actions工作流
│       └── android_build.yml # CI/CD配置
├── src/                     # C++源代码
│   ├── core/                # 核心模拟代码
│   ├── video_core/          # 视频核心
│   ├── audio_core/          # 音频核心
│   └── common/              # 公共代码
└── externals/               # 外部依赖库
```

## 构建步骤

### 第一步：克隆代码仓库

首先，克隆Mandarine代码仓库并初始化子模块：

```bash
git clone https://github.com/mandarine3ds/mandarine.git
cd mandarine
git submodule update --init --recursive
```

### 第二步：配置Android SDK

确保正确设置Android SDK环境变量。在Linux或macOS上，编辑`~/.bashrc`或`~/.zshrc`：

```bash
export ANDROID_HOME=/path/to/android/sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/27.0.12077973
export PATH=$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin
```

使配置生效：

```bash
source ~/.bashrc
```

### 第三步：配置本地属性

创建或编辑`src/android/local.properties`文件：

```properties
sdk.dir=/path/to/android/sdk
ndk.dir=/path/to/android/ndk/27.0.12077973
cmake.dir=/path/to/cmake/bin
```

### 第四步：运行构建

#### 方式一：使用构建脚本（推荐）

项目提供了完整的构建脚本，使用方法如下：

```bash
cd src/android

# 赋予执行权限
chmod +x build_android.sh

# 构建推荐版本（RelWithDebInfo）
./build_android.sh

# 构建Debug版本
./build_android.sh debug

# 构建Release版本
./build_android.sh release

# 生成App Bundle
./build_android.sh bundle

# 清理构建目录
./build_android.sh clean

# 运行单元测试
./build_android.sh test

# 运行静态分析
./build_android.sh lint
```

#### 方式二：直接使用Gradle

```bash
cd src/android

# 初始化Gradle Wrapper
chmod +x ./gradlew
./gradlew --version

# 构建Debug APK
./gradlew assembleDebug

# 构建Release APK
./gradlew assembleRelease

# 构建RelWithDebInfo APK（推荐用于测试发布）
./gradlew assembleRelWithDebInfo

# 生成App Bundle
./gradlew bundleRelease

# 运行测试
./gradlew test

# 运行Lint检查
./gradlew lint
```

### 第五步：验证构建结果

构建完成后，APK文件将位于以下目录：

| 构建类型 | 输出路径 |
|----------|----------|
| Debug | `app/build/outputs/apk/debug/app-debug.apk` |
| Release | `app/build/outputs/apk/release/app-release.apk` |
| RelWithDebInfo | `app/build/outputs/apk/relWithDebInfo/app-relWithDebInfo.apk` |
| App Bundle | `app/build/outputs/bundle/release/app-release.aab` |

## 构建变体说明

### Debug版本

- **用途**：开发和调试
- **特性**：包含完整的调试符号，可附加调试器
- **签名**：使用调试密钥自动签名
- **优化**：禁用代码优化，便于调试
- **适用场景**：本地开发、问题排查

### RelWithDebInfo版本（推荐）

- **用途**：测试发布
- **特性**：包含调试信息，同时启用Release级别的优化
- **签名**：使用调试密钥签名（可安装于设备）
- **优化**：启用Release优化，提升性能
- **适用场景**：内部测试、预发布验证

### Release版本

- **用途**：正式发布
- **特性**：完全优化，剥离调试信息
- **签名**：使用发布密钥签名（必需配置）
- **优化**：最高级别优化，最小化APK大小
- **适用场景**：应用商店发布

## 发布配置

### 配置签名密钥

Release版本需要配置签名密钥。可以通过以下两种方式配置：

#### 方式一：环境变量

```bash
export ANDROID_KEYSTORE_FILE=/path/to/keystore.jks
export ANDROID_KEYSTORE_PASS=your_keystore_password
export ANDROID_KEY_ALIAS=your_key_alias
export ANDROID_KEYSTORE_KEY_PASSWORD=your_key_password
```

#### 方式二：在GitHub Secrets中配置

在GitHub仓库的Settings > Secrets中添加以下密钥：

| Secret名称 | 说明 |
|------------|------|
| ANDROID_SIGNING_KEY | Base64编码的密钥库文件 |
| ANDROID_KEYSTORE_PASS | 密钥库密码 |
| ANDROID_KEY_ALIAS | 密钥别名 |
| ANDROID_KEYSTORE_KEY_PASSWORD | 密钥密码 |

### 生成签名密钥

如果还没有签名密钥，可以使用以下命令生成：

```bash
keytool -genkey -v -keystore my-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias my-key-alias
```

生成后，将密钥库文件放置在安全位置，并在环境中配置相应变量。

## CI/CD自动化构建

项目配置了GitHub Actions自动化构建工作流，支持以下功能：

### 触发条件

- 推送到`master`、`main`、`develop`分支
- 创建版本标签（`v*`）
- 手动触发工作流
- Pull Request提交

### 工作流任务

1. **代码检查**：运行代码格式检查和Lint静态分析
2. **单元测试**：执行单元测试确保代码质量
3. **构建任务**：根据触发条件构建Debug或Release版本
4. **自动发布**：版本标签推送时自动创建GitHub Release

### 查看构建状态

访问以下地址查看CI/CD构建状态：

```
https://github.com/mandarine3ds/mandarine/actions
```

## 常见问题

### 问题一：NDK未找到

**症状**：构建时报错找不到NDK。

**解决方案**：

1. 确认NDK已下载：检查`$ANDROID_HOME/ndk/`目录
2. 设置NDK路径：在`local.properties`中添加`ndk.dir=/path/to/ndk`
3. 或设置环境变量：`export ANDROID_NDK_HOME=/path/to/ndk`

### 问题二：Gradle构建失败

**症状**：Gradle构建过程中断。

**解决方案**：

1. 清理缓存：`./gradlew clean --refresh-dependencies`
2. 删除本地缓存：`rm -rf ~/.gradle/caches/`
3. 重新构建：`./gradlew assembleDebug`

### 问题三：CMake编译错误

**症状**：C++代码编译失败。

**解决方案**：

1. 确认CMake版本：需要3.22.1或更高版本
2. 检查NDK版本：确保使用推荐的NDK版本
3. 更新子模块：`git submodule update --init --recursive`

### 问题四：内存不足

**症状**：构建过程被杀或内存溢出。

**解决方案**：

1. 增加Gradle内存：在`gradle.properties`中设置`org.gradle.jvmargs=-Xmx16g`
2. 启用并行构建：`org.gradle.parallel=true`
3. 启用缓存：`org.gradle.caching=true`

### 问题五：签名验证失败

**症状**：Release APK无法安装。

**解决方案**：

1. 确认签名配置正确
2. 检查密钥别名和密码
3. 确保密钥库文件存在且可读

## 性能优化建议

### 加速构建

1. **启用CCache**：配置CCache可以显著加速重复构建
2. **使用本地Gradle缓存**：确保`org.gradle.caching=true`
3. **并行构建**：`org.gradle.parallel=true`
4. **预下载依赖**：在首次构建前运行`./gradlew dependencies`

### 减小APK大小

1. **启用资源压缩**：构建配置中启用`isShrinkResources = true`
2. **启用代码压缩**：构建配置中启用`isMinifyEnabled = true`
3. **使用App Bundle**：使用App Bundle代替APK可以进一步减小安装包大小

## 发布到应用商店

### Google Play Store

1. 创建Google Play开发者账号
2. 配置应用签名密钥（如果使用Play App Signing）
3. 生成Release APK或App Bundle
4. 在Google Play Console创建应用
5. 完成应用信息填写和内容分级
6. 上传APK/AAB并提交审核

### 第三方应用商店

对于F-Droid等第三方应用商店，需要遵守其特定的打包和签名要求。

## 技术支持

- **问题反馈**：https://github.com/mandarine3ds/mandarine/issues
- **社区讨论**：https://github.com/mandarine3ds/mandarine/discussions
- **文档Wiki**：https://github.com/mandarine3ds/mandarine/wiki

## 许可证

Mandarine项目采用GPLv2或更高版本许可证。具体许可证条款请参阅项目根目录的`license.txt`文件。
