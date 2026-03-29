#!/bin/bash

# Mandarine Android 构建验证脚本
# 用于验证Android构建环境是否正确配置

set -e  # 遇到错误立即退出

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 项目路径
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

# 计数器
PASS_COUNT=0
FAIL_COUNT=0
WARN_COUNT=0

# 日志函数
log_info() {
    echo -e "${BLUE}[检查]${NC} $1"
}

log_pass() {
    echo -e "${GREEN}[通过]${NC} $1"
    ((PASS_COUNT++))
}

log_fail() {
    echo -e "${RED}[失败]${NC} $1"
    ((FAIL_COUNT++))
}

log_warn() {
    echo -e "${YELLOW}[警告]${NC} $1"
    ((WARN_COUNT++))
}

echo "=========================================="
echo "  Mandarine Android 构建环境验证"
echo "=========================================="
echo ""

# 检查1：Java环境
log_info "检查Java环境..."
if command -v java &> /dev/null; then
    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | awk -F '"' '{print $2}')
    log_pass "Java已安装: $JAVA_VERSION"

    # 检查Java版本
    JAVA_MAJOR=$(java -version 2>&1 | head -n 1 | awk -F '"' '{print $2}' | cut -d'.' -f1)
    if [ "$JAVA_MAJOR" = "17" ] || [ "$JAVA_MAJOR" = "21" ] || [ "$JAVA_MAJOR" = "23" ]; then
        log_pass "Java版本符合要求（17或更高）"
    else
        log_warn "Java版本可能过低，建议使用JDK 17或更高"
    fi
else
    log_fail "Java未安装"
    log_info "请安装JDK 17或更高版本"
fi

echo ""

# 检查2：Gradle
log_info "检查Gradle..."
if [ -f "./gradlew" ]; then
    chmod +x ./gradlew
    log_pass "Gradle Wrapper存在"

    # 检查Gradle版本
    GRADLE_VERSION=$(./gradlew --version 2>&1 | head -n 2 | tail -n 1 | awk '{print $3}')
    log_info "Gradle版本: $GRADLE_VERSION"
else
    log_fail "Gradle Wrapper不存在"
fi

echo ""

# 检查3：Android SDK
log_info "检查Android SDK..."
if [ -n "$ANDROID_HOME" ]; then
    log_pass "ANDROID_HOME已设置: $ANDROID_HOME"

    if [ -d "$ANDROID_HOME/platforms/android-35" ]; then
        log_pass "Android API 35已安装"
    else
        log_warn "Android API 35未安装，请运行sdkmanager安装"
    fi

    if [ -d "$ANDROID_HOME/build-tools/35.0.0" ]; then
        log_pass "Build Tools 35.0.0已安装"
    else
        log_warn "Build Tools 35.0.0未安装，请运行sdkmanager安装"
    fi
else
    log_fail "ANDROID_HOME未设置"
    log_info "请设置ANDROID_HOME环境变量"
fi

echo ""

# 检查4：Android NDK
log_info "检查Android NDK..."
if [ -n "$ANDROID_NDK_HOME" ]; then
    log_pass "ANDROID_NDK_HOME已设置: $ANDROID_NDK_HOME"

    if [ -d "$ANDROID_NDK_HOME" ]; then
        NDK_VERSION=$(cat "$ANDROID_NDK_HOME/ndk-version" 2>/dev/null || ls "$ANDROID_NDK_HOME" 2>/dev/null | head -n 1)
        log_info "NDK版本: $NDK_VERSION"
        log_pass "NDK目录存在"
    else
        log_fail "NDK目录不存在"
    fi
else
    log_warn "ANDROID_NDK_HOME未设置，Gradle将自动下载"
fi

echo ""

# 检查5：CMake
log_info "检查CMake..."
if command -v cmake &> /dev/null; then
    CMAKE_VERSION=$(cmake --version 2>&1 | head -n 1 | awk '{print $3}')
    log_pass "CMake已安装: $CMAKE_VERSION"
else
    log_warn "CMake未安装，Android Gradle插件将使用内置版本"
fi

echo ""

# 检查6：NDK CCache
log_info "检查CCache..."
if command -v ccache &> /dev/null; then
    CCACHE_VERSION=$(ccache --version 2>&1 | head -n 1 | awk '{print $3}')
    log_pass "CCache已安装: $CCACHE_VERSION"
    log_info "建议启用CCache以加速构建"
else
    log_warn "CCache未安装（可选，建议安装以加速构建）"
fi

echo ""

# 检查7：Git子模块
log_info "检查Git子模块..."
if [ -d "../../.git" ]; then
    # 检查关键子模块
    MISSING_SUBMODULES=0

    for submodule in "externals/dynarmic" "externals/xbyak"; do
        if [ ! -d "../../$submodule" ]; then
            log_warn "子模块 $submodule 未初始化"
            MISSING_SUBMODULES=1
        fi
    done

    if [ $MISSING_SUBMODULES -eq 0 ]; then
        log_pass "所有关键子模块已初始化"
    else
        log_info "请运行: git submodule update --init --recursive"
    fi
fi

echo ""

# 检查8：Gradle配置
log_info "检查Gradle配置..."
if [ -f "gradle.properties" ]; then
    log_pass "gradle.properties存在"

    # 检查关键配置
    if grep -q "org.gradle.caching=true" gradle.properties; then
        log_pass "Gradle缓存已启用"
    else
        log_warn "Gradle缓存未启用，建议启用"
    fi

    if grep -q "org.gradle.parallel=true" gradle.properties; then
        log_pass "并行构建已启用"
    else
        log_warn "并行构建未启用，建议启用"
    fi
else
    log_fail "gradle.properties不存在"
fi

echo ""

# 检查9：Build Tools
log_info "检查Build Tools..."
if [ -d "$ANDROID_HOME/build-tools" ]; then
    INSTALLED_TOOLS=$(ls "$ANDROID_HOME/build-tools" 2>/dev/null | tail -n 5)
    log_info "已安装的Build Tools版本:"
    echo "$INSTALLED_TOOLS" | while read -r tool; do
        echo "    - $tool"
    done

    if [ -d "$ANDROID_HOME/build-tools/35.0.0" ]; then
        log_pass "推荐版本的Build Tools已安装"
    else
        log_warn "推荐版本35.0.0未安装"
    fi
else
    log_fail "Build Tools目录不存在"
fi

echo ""

# 检查10：本地.properties配置
log_info "检查local.properties..."
if [ -f "local.properties" ]; then
    log_pass "local.properties存在"

    if grep -q "sdk.dir" local.properties; then
        SDK_PATH=$(grep "sdk.dir" local.properties | cut -d'=' -f2)
        log_info "SDK路径: $SDK_PATH"

        if [ -d "$SDK_PATH" ]; then
            log_pass "SDK路径有效"
        else
            log_fail "SDK路径无效，请检查sdk.dir配置"
        fi
    else
        log_warn "local.properties中未配置sdk.dir"
    fi
else
    log_warn "local.properties不存在，请创建并配置SDK路径"
    log_info "可以复制local.properties.template作为模板"
fi

echo ""

# 检查11：Gradle同步
log_info "尝试Gradle同步..."
if ./gradlew --stop &> /dev/null; then
    log_pass "Gradle守护进程可以停止"
fi

if ./gradlew tasks --quiet &> /dev/null; then
    log_pass "Gradle同步成功"
else
    log_fail "Gradle同步失败，请检查配置"
fi

echo ""

# 检查12：构建任务
log_info "检查可用构建任务..."
if ./gradlew tasks --group build --quiet 2>&1 | grep -q "assemble"; then
    log_pass "构建任务可用"
else
    log_warn "未找到标准构建任务"
fi

echo ""

# 最终报告
echo "=========================================="
echo "  验证结果报告"
echo "=========================================="
echo -e "通过: ${GREEN}$PASS_COUNT${NC}"
echo -e "失败: ${RED}$FAIL_COUNT${NC}"
echo -e "警告: ${YELLOW}$WARN_COUNT${NC}"
echo ""

if [ $FAIL_COUNT -eq 0 ]; then
    echo -e "${GREEN}✓ 环境配置基本正确，可以开始构建${NC}"
    echo ""
    echo "建议的下一步操作："
    echo "  1. 运行 ./build_android.sh debug 构建Debug版本"
    echo "  2. 测试应用基本功能"
    echo "  3. 如果一切正常，运行 ./build_android.sh release 构建Release版本"
    exit 0
else
    echo -e "${RED}✗ 环境配置存在问题，请先解决上述失败项${NC}"
    echo ""
    echo "常见解决方案："
    echo "  1. 检查并安装缺失的开发工具"
    echo "  2. 运行 ./gradlew --stop 停止所有Gradle守护进程"
    echo "  3. 删除 ~/.gradle 目录并重新同步"
    echo "  4. 查看上方失败项的具体说明"
    exit 1
fi
