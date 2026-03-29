# Mandarine Android 构建配置说明

## 项目概述

Mandarine是一个高性能的Nintendo 3DS模拟器，本项目包含完整的Android平台构建配置，支持从开发调试到正式发布的所有构建场景。

## 目录结构

```
src/android/
├── 构建配置
│   ├── build.gradle.kts          # 项目级构建配置
│   ├── settings.gradle.kts       # Gradle项目设置
│   ├── gradle.properties         # Gradle构建属性
│   ├── app/
│   │   └── build.gradle.kts      # 应用模块构建配置
│   └── app/
│       └── proguard-rules.pro    # 代码混淆规则
│
├── 构建脚本
│   ├── build_android.sh          # 完整构建脚本
│   └── verify_build.sh           # 环境验证脚本
│
├── CI/CD配置
│   └── ../../.github/workflows/
│       └── android_build.yml     # GitHub Actions工作流
│
├── 文档
│   ├── BUILD_GUIDE.md            # 详细构建指南
│   └── PUBLISH_GUIDE.md          # 发布指南
│
└── 配置模板
    └── local.properties.template # 本地配置模板
```

## 快速开始

### 环境要求

- JDK 17或更高版本
- Android SDK API 35
- Android NDK 27.0.12077973
- Gradle 8.10（通过Wrapper自动管理）

### 构建步骤

#### 第一步：配置环境

```bash
# 克隆项目
git clone https://github.com/mandarine3ds/mandarine.git
cd mandarine

# 初始化子模块
git submodule update --init --recursive

# 进入Android目录
cd src/android

# 配置local.properties（复制模板）
cp local.properties.template local.properties
# 编辑local.properties，设置正确的SDK路径
```

#### 第二步：验证环境

```bash
# 运行验证脚本
./verify_build.sh

# 修复所有失败项后再继续
```

#### 第三步：执行构建

```bash
# 方式一：使用构建脚本（推荐）
./build_android.sh debug      # 构建Debug版本
./build_android.sh release     # 构建Release版本
./build_android.sh bundle      # 生成App Bundle

# 方式二：直接使用Gradle
./gradlew assembleDebug        # Debug构建
./gradlew assembleRelease      # Release构建
```

## 构建变体说明

### Debug版本

- **用途**：本地开发和调试
- **特性**：完整调试符号，可附加调试器
- **签名**：自动使用调试密钥
- **优化**：禁用，便于调试
- **APK位置**：`app/build/outputs/apk/debug/`

### RelWithDebInfo版本

- **用途**：测试发布（推荐）
- **特性**：调试信息+Release优化
- **签名**：调试密钥
- **优化**：启用，性能接近Release
- **APK位置**：`app/build/outputs/apk/relWithDebInfo/`

### Release版本

- **用途**：正式发布
- **特性**：完全优化，最小化体积
- **签名**：发布密钥（必需）
- **优化**：最高级别
- **APK位置**：`app/build/outputs/apk/release/`

## 自动化构建

### GitHub Actions

项目配置了完整的CI/CD工作流，包括：

- **代码检查**：自动化代码质量检查
- **单元测试**：确保代码质量
- **构建验证**：多变体构建验证
- **自动发布**：版本标签触发自动Release创建

### 触发条件

- 推送到`master`、`main`、`develop`分支
- 创建版本标签（`v*`）
- Pull Request提交
- 手动触发

## 发布配置

### 签名密钥配置

Release构建需要配置签名密钥：

```bash
# 方式一：环境变量
export ANDROID_KEYSTORE_FILE=/path/to/keystore.jks
export ANDROID_KEYSTORE_PASS=your_password
export ANDROID_KEY_ALIAS=your_alias
export ANDROID_KEYSTORE_KEY_PASSWORD=your_key_password

# 方式二：GitHub Secrets
# 在仓库Settings中添加以下Secret：
# - ANDROID_SIGNING_KEY
# - ANDROID_KEYSTORE_PASS
# - ANDROID_KEY_ALIAS
# - ANDROID_KEYSTORE_KEY_PASSWORD
```

### 生成签名密钥

```bash
keytool -genkey -v -keystore my-release-key.jks \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -alias my-key-alias
```

## 构建产物

### APK和Bundle

| 构建类型 | 文件位置 | 用途 |
|----------|----------|------|
| Debug | `app/build/outputs/apk/debug/app-debug.apk` | 开发调试 |
| RelWithDebInfo | `app/build/outputs/apk/relWithDebInfo/app-relWithDebInfo.apk` | 测试发布 |
| Release | `app/build/outputs/apk/release/app-release.apk` | 正式发布 |
| App Bundle | `app/build/outputs/bundle/release/app-release.aab` | Google Play |

### 验证构建产物

```bash
# 验证APK签名
apksigner verify -v app/build/outputs/apk/release/app-release.apk

# 验证Bundle
bundletool validate-bundle --bundle app/build/outputs/bundle/release/app-release.aab
```

## 性能优化

### 构建加速

1. **启用CCache**：

```bash
# 安装CCache
apt install ccache

# 配置环境变量
export NDK_CCACHE=$(which ccache)
```

2. **Gradle优化**：项目已配置以下优化选项：

```properties
org.gradle.caching=true
org.gradle.parallel=true
org.gradle.configuration-cache=true
```

### APK优化

1. **代码压缩**：Release构建默认启用ProGuard/R8
2. **资源压缩**：启用`isShrinkResources = true`
3. **App Bundle**：推荐使用App Bundle减少安装包大小

## 常见问题

### 问题一：NDK未找到

```bash
# 检查NDK路径
echo $ANDROID_NDK_HOME

# 设置NDK路径
export ANDROID_NDK_HOME=/path/to/android/ndk/27.0.12077973
```

### 问题二：Gradle构建失败

```bash
# 清理缓存
./gradlew clean --refresh-dependencies
rm -rf ~/.gradle/caches/
./gradlew --stop

# 重新构建
./build_android.sh debug
```

### 问题三：内存不足

```bash
# 增加Gradle内存
# 在gradle.properties中添加：
org.gradle.jvmargs=-Xmx16g -XX:+UseParallelGC
```

## 相关文档

- **构建指南**：`BUILD_GUIDE.md` - 详细的构建步骤和环境配置说明
- **发布指南**：`PUBLISH_GUIDE.md` - 完整的应用商店发布流程

## 技术支持

- **问题反馈**：https://github.com/mandarine3ds/mandarine/issues
- **社区讨论**：https://github.com/mandarine3ds/mandarine/discussions
- **构建验证**：运行`./verify_build.sh`检查环境配置

## 更新日志

| 日期 | 版本 | 更新内容 |
|------|------|----------|
| 2026-03-29 | 1.0.0 | 初始构建配置 |

## 许可证

Mandarine项目采用GPLv2或更高版本许可证发布。
