# 版本号管理指南

## 📍 配置文件位置

**文件**：`app/build.gradle.kts`  
**位置**：第 50-51 行

```kotlin
defaultConfig {
    applicationId = "com.mockloc"
    minSdk = 29
    targetSdk = 36
    versionCode = 1        // ← 修改这里
    versionName = "1.0.0"  // ← 修改这里
}
```

---

## 🔢 版本号规则

### **versionCode（版本代码）**
- **类型**：整数（Int）
- **作用**：Android 系统判断版本新旧的唯一标识
- **规则**：
  - ✅ 每次发布必须**递增**（1 → 2 → 3 → ...）
  - ✅ 必须是正整数
  - ❌ 不能减小或保持不变
  - ❌ 不能使用小数

### **versionName（版本名称）**
- **类型**：字符串（String）
- **作用**：显示给用户的版本号
- **格式**：遵循[语义化版本规范](https://semver.org/lang/zh-CN/)
  - `主版本.次版本.修订版本`
  - 例如：`1.0.0`、`1.1.0`、`2.0.0`

---

## 📝 版本号更新策略

### **场景对照表**

| 变更类型 | versionCode | versionName | 说明 |
|---------|-------------|-------------|------|
| **Bug 修复** | +1 | x.x.**+1** | 修复错误，无新功能 |
| **新增功能** | +1 | x.**+1**.0 | 向后兼容的新功能 |
| **重大更新** | +1 | **+1**.0.0 | 不兼容的重大变更 |
| **测试版本** | +1 | x.x.x-**beta** | 内测/公测版本 |

### **实际示例**

#### **初始版本**
```kotlin
versionCode = 1
versionName = "1.0.0"
```

#### **第一次 Bug 修复**
```kotlin
versionCode = 2
versionName = "1.0.1"  // 修复了夜间模式主题同步问题
```

#### **第一次功能更新**
```kotlin
versionCode = 3
versionName = "1.1.0"  // 新增远程更新功能
```

#### **重大重构**
```kotlin
versionCode = 4
versionName = "2.0.0"  // 架构重构，UI 全面升级
```

---

## 🚀 发布流程

### **步骤 1：确定新版本号**
根据本次更新的性质，决定版本号：
- 小修复 → 修订版本 +1（1.0.0 → 1.0.1）
- 新功能 → 次版本 +1（1.0.0 → 1.1.0）
- 大改动 → 主版本 +1（1.0.0 → 2.0.0）

### **步骤 2：修改 build.gradle.kts**
```kotlin
defaultConfig {
    versionCode = 2        // 递增
    versionName = "1.0.1"  // 更新版本名
}
```

### **步骤 3：更新更新日志**
在 GitHub Release 或 update.json 中记录变更内容。

### **步骤 4：构建并测试**
```bash
# 清理项目
Build → Clean Project

# 重新构建
Build → Rebuild Project

# 生成签名 APK
Build → Generate Signed Bundle / APK
```

### **步骤 5：验证版本号**
安装 APK 后，在 App 的"关于"页面检查版本号是否正确显示。

---

## 📱 用户看到的版本号

### **在 App 内显示**
通常在"设置" → "关于"页面：
```
当前版本：1.0.0 (1)
         ↑      ↑
     versionName  versionCode
```

### **在 Google Play / 应用商店**
- 显示 `versionName`（1.0.0）
- 系统使用 `versionCode` 判断是否需要更新

### **在系统设置中**
```
设置 → 应用 → MockLocations
版本：1.0.0
```

---

## ⚠️ 注意事项

### **1. versionCode 必须递增**
```kotlin
// ✅ 正确
versionCode = 1  // 第一次发布
versionCode = 2  // 第二次发布

// ❌ 错误
versionCode = 2  // 第一次发布
versionCode = 1  // 第二次发布（会导致安装失败）
```

### **2. 不要跳过 versionCode**
```kotlin
// ✅ 推荐：连续递增
versionCode = 1, 2, 3, 4, 5

// ⚠️ 可以但不推荐：跳跃递增
versionCode = 1, 5, 10, 15
```

### **3. versionName 可以自由命名**
```kotlin
// 这些都是合法的
versionName = "1.0.0"
versionName = "1.0.0-beta"
versionName = "1.0.0-rc1"
versionName = "v1.0.0"
```

### **4. 测试版本标记**
```kotlin
// 内测版本
versionCode = 1
versionName = "1.0.0-beta"

// 候选版本
versionCode = 2
versionName = "1.0.0-rc1"

// 正式发布
versionCode = 3
versionName = "1.0.0"
```

---

## 🔍 查看当前版本号

### **方法 1：在代码中获取**
```kotlin
val packageInfo = packageManager.getPackageInfo(packageName, 0)
val versionCode = packageInfo.versionCode
val versionName = packageInfo.versionName
```

### **方法 2：通过 BuildConfig**
```kotlin
val versionCode = BuildConfig.VERSION_CODE
val versionName = BuildConfig.VERSION_NAME
```

### **方法 3：命令行查看 APK**
```bash
aapt dump badging app-release.apk | findstr version
```

---

## 📊 版本历史示例

| 发布日期 | versionCode | versionName | 更新内容 |
|---------|-------------|-------------|---------|
| 2026-04-24 | 1 | 1.0.0 | 首个正式版本 |
| 2026-04-25 | 2 | 1.0.1 | 修复夜间模式主题同步 |
| 2026-04-26 | 3 | 1.1.0 | 新增远程更新功能 |
| 2026-04-27 | 4 | 1.1.1 | 修复下载进度显示 |
| 2026-05-01 | 5 | 2.0.0 | UI 全面重构 |

---

## 🎯 最佳实践

1. **每次发布前检查版本号**
   - 确认 versionCode 已递增
   - 确认 versionName 符合语义化规范

2. **在 Git Commit 中记录版本变更**
   ```bash
   git commit -m "Bump version to 1.0.1"
   ```

3. **创建 Git Tag**
   ```bash
   git tag v1.0.1
   git push origin v1.0.1
   ```

4. **更新 CHANGELOG.md**
   记录每个版本的变更内容

5. **同步更新 update.json**
   如果使用远程更新功能，记得更新服务器上的配置文件

---

## 💡 快速参考

### **当前配置**
```kotlin
versionCode = 1
versionName = "1.0.0"
```

### **下次发布建议**
```kotlin
// 如果是小修复
versionCode = 2
versionName = "1.0.1"

// 如果有新功能
versionCode = 2
versionName = "1.1.0"
```

---

需要我帮你准备下一个版本的版本号吗？或者有其他问题？
