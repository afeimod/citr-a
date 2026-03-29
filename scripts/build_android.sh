#!/bin/bash

# Mandarine Android 构建脚本
# 用于在本地环境构建Android应用并发布

set -e  # 遇到错误立即退出
set -u  # 使用未定义的变量时报错

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 项目路径
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

# 构建类型：debug, release, relWithDebInfo
BUILD_TYPE="${1:-relWithDebInfo}"

# 输出目录
OUTPUT_DIR="$PROJECT_DIR/build/outputs"
APK_OUTPUT="$OUTPUT_DIR/apk"
BUNDLE_OUTPUT="$OUTPUT_DIR/bundle"

# 日志函数
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查环境
check_environment() {
    log_info "检查构建环境..."

    # 检查Java
    if ! command -v java &> /dev/null; then
        log_error "Java未安装，请先安装JDK 17或更高版本"
        exit 1
    fi

    # 检查Gradle
    if [ ! -f "./gradlew" ]; then
        log_error "Gradle wrapper未找到"
        exit 1
    fi

    # 检查Android SDK
    if [ -z "$ANDROID_HOME" ] && [ -z "$ANDROID_SDK_ROOT" ]; then
        log_warning "ANDROID_HOME或ANDROID_SDK_ROOT未设置，使用默认路径"
        export ANDROID_HOME="$HOME/Android/Sdk"
    fi

    # 检查NDK
    if [ -z "$ANDROID_NDK_HOME" ]; then
        log_warning "ANDROID_NDK_HOME未设置，Gradle将使用默认NDK版本"
    fi

    log_success "环境检查完成"
}

# 清理构建目录
clean_build() {
    log_info "清理构建目录..."

    rm -rf "$PROJECT_DIR/app/build"
    rm -rf "$PROJECT_DIR/.gradle"
    rm -rf "$OUTPUT_DIR"

    mkdir -p "$APK_OUTPUT"
    mkdir -p "$BUNDLE_OUTPUT"

    log_success "清理完成"
}

# 初始化Gradle wrapper
init_gradle() {
    log_info "初始化Gradle wrapper..."

    chmod +x ./gradlew
    ./gradlew --version

    log_success "Gradle初始化完成"
}

# 构建Debug版本
build_debug() {
    log_info "开始构建Debug版本..."

    ./gradlew assembleDebug --no-daemon --stacktrace

    log_success "Debug版本构建完成"
    log_info "APK位置: $APK_OUTPUT/debug/"
}

# 构建Release版本
build_release() {
    log_info "开始构建Release版本..."

    # 检查签名配置
    if [ -z "$ANDROID_KEYSTORE_FILE" ]; then
        log_warning "未设置签名密钥，使用调试密钥（仅限测试）"
        ./gradlew assembleRelease --no-daemon --stacktrace
    else
        ./gradlew assembleRelease --no-daemon --stacktrace
    fi

    log_success "Release版本构建完成"
    log_info "APK位置: $APK_OUTPUT/release/"
}

# 构建RelWithDebInfo版本（默认）
build_rel_with_deb_info() {
    log_info "开始构建RelWithDebInfo版本（推荐用于测试发布）..."

    ./gradlew assembleRelWithDebInfo --no-daemon --stacktrace

    log_success "RelWithDebInfo版本构建完成"
    log_info "APK位置: $APK_OUTPUT/relWithDebInfo/"
}

# 生成App Bundle
build_bundle() {
    log_info "生成App Bundle..."

    ./gradlew bundleRelease --no-daemon --stacktrace

    log_success "App Bundle生成完成"
    log_info "Bundle位置: $BUNDLE_OUTPUT/"
}

# 构建所有变体
build_all() {
    log_info "构建所有变体..."

    ./gradlew assemble --no-daemon --stacktrace

    log_success "所有变体构建完成"
}

# 运行测试
run_tests() {
    log_info "运行单元测试..."

    ./gradlew test --no-daemon

    log_success "测试完成"
}

# 静态分析
run_lint() {
    log_info "运行Lint静态分析..."

    ./gradlew lint --no-daemon

    log_success "Lint分析完成"
    log_info "报告位置: app/build/reports/lint-results.html"
}

# 发布到本地Maven仓库
publish_to_maven() {
    log_info "发布到本地Maven仓库..."

    ./gradlew publishToMavenLocal --no-daemon

    log_success "发布完成"
    log_info "本地仓库位置: ~/.m2/repository/"
}

# 显示帮助信息
show_help() {
    echo "Mandarine Android 构建脚本"
    echo ""
    echo "用法: $0 [命令]"
    echo ""
    echo "可用命令:"
    echo "  debug           构建Debug版本"
    echo "  release         构建Release版本"
    echo "  relWithDebInfo  构建RelWithDebInfo版本（默认）"
    echo "  bundle          生成App Bundle"
    echo "  all             构建所有变体"
    echo "  clean           清理构建目录"
    echo "  test            运行单元测试"
    echo "  lint            运行静态分析"
    echo "  publish         发布到本地Maven仓库"
    echo "  help            显示帮助信息"
    echo ""
    echo "示例:"
    echo "  $0              # 构建默认版本"
    echo "  $0 debug        # 构建Debug版本"
    echo "  $0 release      # 构建Release版本"
    echo "  $0 bundle       # 生成App Bundle"
}

# 主函数
main() {
    log_info "=========================================="
    log_info "  Mandarine Android 构建系统"
    log_info "=========================================="
    echo ""

    # 检查环境
    check_environment

    # 创建输出目录
    mkdir -p "$OUTPUT_DIR"
    mkdir -p "$APK_OUTPUT"
    mkdir -p "$BUNDLE_OUTPUT"

    # 根据参数执行相应操作
    case "$BUILD_TYPE" in
        "debug")
            build_debug
            ;;
        "release")
            build_release
            ;;
        "relWithDebInfo")
            build_rel_with_deb_info
            ;;
        "bundle")
            build_bundle
            ;;
        "all")
            build_all
            ;;
        "clean")
            clean_build
            ;;
        "test")
            run_tests
            ;;
        "lint")
            run_lint
            ;;
        "publish")
            publish_to_maven
            ;;
        "help"|"--help"|"-h")
            show_help
            ;;
        *)
            log_error "未知命令: $BUILD_TYPE"
            echo ""
            show_help
            exit 1
            ;;
    esac

    echo ""
    log_success "构建流程完成！"
}

# 执行主函数
main
