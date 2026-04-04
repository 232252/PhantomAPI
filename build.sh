#!/bin/bash
# PhantomAPI 构建脚本

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR"
BUILD_DIR="$PROJECT_DIR/build"
DIST_DIR="$PROJECT_DIR/dist"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查环境
check_environment() {
    log_info "检查构建环境..."
    
    # 检查 Java
    if ! command -v java &> /dev/null; then
        log_error "未找到 Java，请安装 JDK 17+"
        exit 1
    fi
    log_info "Java: $(java -version 2>&1 | head -n 1)"
    
    # 检查 Gradle 或使用 Gradle Wrapper
    if [ -f "$PROJECT_DIR/gradlew" ]; then
        GRADLE="$PROJECT_DIR/gradlew"
    elif command -v gradle &> /dev/null; then
        GRADLE="gradle"
    else
        log_error "未找到 Gradle"
        exit 1
    fi
    log_info "Gradle: $GRADLE"
    
    # 检查 Android SDK
    if [ -z "$ANDROID_HOME" ]; then
        log_warn "ANDROID_HOME 未设置"
        if [ -d "$HOME/Android/Sdk" ]; then
            export ANDROID_HOME="$HOME/Android/Sdk"
            log_info "使用默认 Android SDK: $ANDROID_HOME"
        fi
    fi
}

# 清理构建目录
clean() {
    log_info "清理构建目录..."
    rm -rf "$BUILD_DIR"
    rm -rf "$DIST_DIR"
    rm -rf "$PROJECT_DIR/app/build"
    "$GRADLE" clean
}

# 编译 APK
build_apk() {
    log_info "编译 APK..."
    
    cd "$PROJECT_DIR"
    "$GRADLE" assembleRelease
    
    # 查找生成的 APK
    APK_FILE=$(find "$PROJECT_DIR/app/build/outputs/apk" -name "*.apk" | head -n 1)
    
    if [ -z "$APK_FILE" ]; then
        log_error "APK 编译失败"
        exit 1
    fi
    
    log_info "APK 已生成: $APK_FILE"
    echo "$APK_FILE"
}

# 创建 Magisk 模块
create_magisk_module() {
    APK_FILE="$1"
    
    log_info "创建 Magisk 模块..."
    
    MODULE_DIR="$BUILD_DIR/magisk_module"
    mkdir -p "$MODULE_DIR"
    
    # 复制模块文件
    cp -r "$PROJECT_DIR/magisk_module/"* "$MODULE_DIR/"
    
    # 复制 APK
    mkdir -p "$MODULE_DIR/system/priv-app/PhantomAPI"
    cp "$APK_FILE" "$MODULE_DIR/system/priv-app/PhantomAPI/PhantomAPI.apk"
    
    # 设置权限
    chmod 755 "$MODULE_DIR/post-fs-data.sh"
    chmod 755 "$MODULE_DIR/service.sh"
    chmod 755 "$MODULE_DIR/uninstall.sh"
    chmod 755 "$MODULE_DIR/META-INF/com/google/android/update-binary"
    chmod 644 "$MODULE_DIR/META-INF/com/google/android/updater-script"
    chmod 644 "$MODULE_DIR/module.prop"
    
    # 创建 zip
    mkdir -p "$DIST_DIR"
    VERSION=$(grep_prop version "$MODULE_DIR/module.prop")
    ZIP_FILE="$DIST_DIR/PhantomAPI-${VERSION}.zip"
    
    cd "$MODULE_DIR"
    zip -r "$ZIP_FILE" .
    
    log_info "Magisk 模块已创建: $ZIP_FILE"
    echo "$ZIP_FILE"
}

# 获取属性值
grep_prop() {
    local prop="$1"
    local file="$2"
    grep "^$prop=" "$file" 2>/dev/null | head -n 1 | cut -d= -f2
}

# 主函数
main() {
    local command="${1:-build}"
    
    case "$command" in
        clean)
            clean
            ;;
        apk)
            check_environment
            build_apk
            ;;
        module)
            check_environment
            APK_FILE=$(build_apk)
            create_magisk_module "$APK_FILE"
            ;;
        build)
            check_environment
            APK_FILE=$(build_apk)
            create_magisk_module "$APK_FILE"
            ;;
        all)
            check_environment
            clean
            APK_FILE=$(build_apk)
            create_magisk_module "$APK_FILE"
            ;;
        *)
            echo "用法: $0 {clean|apk|module|build|all}"
            exit 1
            ;;
    esac
}

main "$@"
