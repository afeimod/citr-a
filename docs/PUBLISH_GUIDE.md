# Mandarine Android 发布指南

## 发布前准备

### 版本规划

在开始发布之前，请确保完成以下准备工作：

1. **版本号管理**：遵循语义化版本规范（SemVer）
   - 主版本号：重大架构变更
   - 次版本号：新功能添加
   - 修订版本号：问题修复和优化

2. **变更日志**：更新`CHANGELOG.md`记录所有变更内容

3. **测试验证**：确保所有功能经过充分测试

### 环境验证清单

- [ ] Android SDK API 35已安装
- [ ] Android NDK 27.0.12077973已配置
- [ ] JDK 17或更高版本已安装
- [ ] Gradle Wrapper版本正确
- [ ] 所有子模块已初始化
- [ ] 签名密钥已准备就绪

## 发布类型

### 测试发布（RelWithDebInfo）

适用于内部测试和预发布验证：

```bash
cd src/android
./build_android.sh relWithDebInfo
```

特点：
- 包含调试符号便于问题排查
- 启用Release级别优化
- 使用调试密钥签名
- 可直接在设备上安装测试

### 正式发布（Release）

适用于应用商店正式发布：

```bash
cd src/android
./build_android.sh release
```

特点：
- 完全优化，剥离调试信息
- 最小化APK体积
- 必须使用正式签名密钥
- 需通过签名验证

### App Bundle发布

适用于Google Play Store发布：

```bash
cd src/android
./build_android.sh bundle
```

特点：
- 根据设备架构生成最优APK
- 显著减小安装包大小
- 必须使用Play App Signing或自有签名

## 发布流程

### 第一阶段：本地构建验证

#### 构建Debug版本进行初步验证

```bash
cd src/android
./build_android.sh debug
```

在设备上安装并测试基本功能：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

#### 构建RelWithDebInfo进行深度测试

```bash
./build_android.sh relWithDebInfo
```

安装并执行全面测试：

```bash
adb install -r app/build/outputs/apk/relWithDebInfo/app-relWithDebInfo.apk
```

### 第二阶段：性能优化验证

#### 启用ProGuard代码压缩

确保`build.gradle.kts`中ProGuard配置正确：

```kotlin
buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(
            getDefaultProguardFile("proguard-android.txt"),
            "proguard-rules.pro"
        )
    }
}
```

#### 验证优化后的构建

```bash
./build_android.sh release
```

检查APK大小和性能指标。

### 第三阶段：签名配置

#### 方式一：使用环境变量

```bash
export ANDROID_KEYSTORE_FILE=/path/to/keystore.jks
export ANDROID_KEYSTORE_PASS=your_keystore_password
export ANDROID_KEY_ALIAS=your_key_alias
export ANDROID_KEYSTORE_KEY_PASSWORD=your_key_password

./build_android.sh release
```

#### 方式二：使用GitHub Secrets（推荐用于CI/CD）

在GitHub仓库设置中添加以下密钥：

| Secret名称 | 内容说明 |
|------------|----------|
| ANDROID_SIGNING_KEY | Base64编码的.jks文件 |
| ANDROID_KEYSTORE_PASS | 密钥库密码 |
| ANDROID_KEY_ALIAS | 密钥别名 |
| ANDROID_KEYSTORE_KEY_PASSWORD | 密钥密码 |

### 第四阶段：生成发布产物

#### 完整发布构建

```bash
# 清理旧构建
./build_android.sh clean

# 构建Release版本
./build_android.sh release

# 构建App Bundle
./build_android.sh bundle
```

#### 验证构建产物

```bash
# 检查APK信息
$ANDROID_HOME/build-tools/35.0.0/apksigner verify -v app/build/outputs/apk/release/app-release.apk

# 检查Bundle信息
$ANDROID_HOME/build-tools/35.0.0/bundletool validate-bundle --bundle app/build/outputs/bundle/release/app-release.aab
```

### 第五阶段：发布到GitHub

#### 创建版本标签

```bash
git tag -a v1.0.0 -m "Release version 1.0.0"
git push origin v1.0.0
```

GitHub Actions将自动触发构建并创建Release。

#### 手动创建Release

1. 访问GitHub Releases页面
2. 点击"Draft a new release"
3. 选择版本标签
4. 填写发布说明
5. 上传APK/AAB文件
6. 发布Release

### 第六阶段：应用商店发布

#### Google Play Store发布

**前提条件**：

- Google Play开发者账号（一次性费用$25）
- 符合Play Store政策的内容
- 准备好应用图标、截图、描述等素材

**发布步骤**：

1. 登录Google Play Console
2. 创建新应用
3. 完成应用信息：
   - 应用名称和描述
   - 应用图标（512x512 PNG）
   - 屏幕截图（手机、平板、TV）
   - 功能图形和宣传图形
   - 隐私政策URL
4. 设置内容分级问卷
5. 完成价格和分发配置
6. 上传App Bundle或APK
7. 提交审核

**审核时间**：通常1-7天

#### F-Droid发布

F-Droid是开源应用的安全仓库，发布步骤如下：

1. 在F-Droid提交你的应用
2. 创建应用元数据文件
3. 配置自动构建或提交构建产物
4. 等待社区审核

### 第七阶段：发布后验证

#### 验证分发渠道

- [ ] 确认APK可在所有目标设备上安装
- [ ] 验证签名与发布配置一致
- [ ] 检查应用商店审核状态
- [ ] 监控用户反馈和崩溃报告

#### 监控构建状态

```bash
# 监控GitHub Actions构建
gh run watch
```

## 持续集成配置

### GitHub Actions工作流

项目已配置完整的CI/CD流程，包含以下任务：

1. **代码检查**：自动化代码质量检查
2. **单元测试**：确保代码质量
3. **构建验证**：多变体构建验证
4. **自动发布**：版本标签触发自动发布

### 自定义构建配置

编辑`.github/workflows/android_build.yml`自定义工作流行为：

```yaml
env:
  ANDROID_COMPILE_SDK: 35
  ANDROID_BUILD_TOOLS: 35.0.0
  ANDROID_NDK_VERSION: 27.0.12077973
```

## 回滚流程

### 紧急回滚步骤

如果发布后发现严重问题，需要紧急回滚：

1. **GitHub回滚**：删除问题版本标签

```bash
git tag -d v1.0.0
git push origin :refs/tags/v1.0.0
```

2. **应用商店回滚**：
   - Google Play Console：发布上一版本APK
   - 启用分级 rollout，仅分发给测试用户

3. **通知用户**：发布问题公告和解决时间表

### 版本号回退

如果需要重新发布同一主版本：

```bash
git tag -a v1.0.1 -m "Hotfix version 1.0.1"
git push origin v1.0.1
```

## 发布清单

### 发布前检查清单

- [ ] 所有功能测试通过
- [ ] 性能测试满足要求
- [ ] 内存占用在合理范围
- [ ] APK大小符合预期
- [ ] 签名配置正确无误
- [ ] 变更日志已更新
- [ ] 应用商店素材已准备
- [ ] 隐私政策已发布
- [ ] 备份签名密钥

### 发布后检查清单

- [ ] APK/AAB成功生成
- [ ] 签名验证通过
- [ ] GitHub Release已创建
- [ ] 应用商店审核通过
- [ ] 用户反馈监控已设置
- [ ] 崩溃报告已配置
- [ ] 文档已更新

## 常见问题处理

### 构建失败问题

**症状**：Release构建失败但Debug成功

**可能原因**：

1. ProGuard规则不完整
2. 签名配置错误
3. 资源压缩冲突

**解决方案**：

```bash
# 查看详细错误
./gradlew assembleRelease --stacktrace

# 禁用压缩进行测试
# 修改build.gradle.kts中的isMinifyEnabled = false

# 验证ProGuard规则
./gradlew proguardRelease
```

### 签名验证失败

**症状**：APK无法安装，提示签名验证失败

**解决方案**：

1. 检查密钥配置
2. 确认密钥库路径正确
3. 验证密钥别名和密码
4. 确保使用正确的签名配置

### 发布审核被拒

**常见原因及解决方案**：

| 审核问题 | 解决方案 |
|----------|----------|
| 权限使用不当 | 明确声明所有权限用途 |
| 隐私政策缺失 | 添加有效的隐私政策URL |
| 广告不合规 | 遵循广告政策要求 |
| 内容分级不符 | 重新填写分级问卷 |

## 安全注意事项

### 密钥安全

- 绝不将签名密钥提交到代码仓库
- 使用强密码保护密钥库
- 定期轮换签名密钥
- 在安全位置存储密钥备份

### 发布验证

- 发布前进行安全扫描
- 检查依赖库安全性
- 确保APK未包含调试符号
- 验证权限申请最小化

## 联系与支持

- **技术支持**：访问项目Issues页面
- **社区讨论**：参与GitHub Discussions
- **紧急问题**：发送邮件至项目维护者

## 更新日志

| 日期 | 版本 | 更新内容 |
|------|------|----------|
| 2026-03-29 | 1.0.0 | 初始发布指南 |
