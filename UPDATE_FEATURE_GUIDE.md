# 远程更新功能使用指南

## ✅ 已实现的功能

### **核心功能**
- ✅ 检查远程版本更新
- ✅ 显示更新对话框（版本信息、更新日志）
- ✅ 下载 APK 文件（带进度条）
- ✅ 自动触发安装
- ✅ 支持强制更新
- ✅ 支持取消下载

### **技术实现**
- **网络请求**：OkHttp 4.12.0
- **JSON 解析**：Gson 2.11.0
- **异步处理**：Kotlin Coroutines
- **文件存储**：外部文件目录
- **APK 安装**：FileProvider + Intent

---

## 📁 新增文件清单

### **1. 数据模型**
- `app/src/main/java/com/mockloc/util/UpdateInfo.kt` - 更新信息数据类

### **2. 核心逻辑**
- `app/src/main/java/com/mockloc/util/UpdateChecker.kt` - 更新检查器

### **3. UI 组件**
- `app/src/main/java/com/mockloc/ui/update/UpdateDialogFragment.kt` - 更新对话框
- `app/src/main/res/layout/dialog_update.xml` - 对话框布局

### **4. 配置文件**
- `app/src/main/res/xml/file_paths.xml` - FileProvider 路径配置
- `update.json` - 远程更新配置模板

### **5. 依赖添加**
- `app/build.gradle.kts` - 添加 OkHttp 和 Gson 依赖

### **6. Manifest 配置**
- `AndroidManifest.xml` - 添加权限和 FileProvider

---

## 🔧 配置说明

### **1. 修改 update.json 地址**

编辑 `app/src/main/java/com/mockloc/util/UpdateChecker.kt` 第 28 行：

```kotlin
private const val UPDATE_JSON_URL = 
    "https://raw.githubusercontent.com/zhai110/MockLocations/master/update.json"
```

**替换为你的实际地址**，可选方案：

#### **方案 A：GitHub Raw（推荐）**
```
https://raw.githubusercontent.com/用户名/仓库名/分支/update.json
```

#### **方案 B：GitHub Release**
```
https://github.com/用户名/仓库名/releases/latest/download/update.json
```

#### **方案 C：自建服务器**
```
https://your-domain.com/api/update.json
```

---

### **2. 发布新版本流程**

#### **步骤 1：构建新版本 APK**
```bash
# 在 Android Studio 中
Build → Generate Signed Bundle / APK
```

#### **步骤 2：上传到 GitHub Release**
1. 访问：https://github.com/zhai110/MockLocations/releases
2. 点击 **Draft a new release**
3. 填写 Tag version（如 `v1.1.0`）
4. 上传 APK 文件
5. 点击 **Publish release**

#### **步骤 3：更新 update.json**
编辑项目根目录的 `update.json`：

```json
{
  "versionCode": 2,                    // ← 递增
  "versionName": "1.1.0",              // ← 新版本号
  "downloadUrl": "https://github.com/zhai110/MockLocations/releases/download/v1.1.0/app-release.apk",  // ← 新下载地址
  "releaseNotes": "更新内容...",        // ← 更新日志
  "forceUpdate": false,                // 是否强制更新
  "minVersionCode": 1,                 // 最低支持版本
  "fileSize": 15728640,                // 文件大小（字节）
  "md5": "",                           // MD5 校验值（可选）
  "publishTime": "2026-04-24T10:00:00Z"
}
```

#### **步骤 4：提交 update.json 到 Git**
```bash
git add update.json
git commit -m "Update version to 1.1.0"
git push origin master
```

---

## 📱 用户使用流程

### **手动检查更新**
1. 打开 App
2. 进入 **设置** → **关于**
3. 点击 **检查更新**
4. 如果有新版本，显示更新对话框
5. 点击 **立即更新**
6. 等待下载完成
7. 自动弹出安装界面

### **自动检查更新（可选）**

如果需要在启动时自动检查，可以在 `MainActivity` 或 `SplashActivity` 中添加：

```kotlin
// 在合适的时机调用
private fun checkUpdateOnStartup() {
    lifecycleScope.launch {
        val updateChecker = UpdateChecker(this@MainActivity)
        updateChecker.checkForUpdate()
            .onSuccess { updateInfo ->
                if (updateInfo != null && updateInfo.isForceUpdate(updateChecker.getCurrentVersionInfo().first)) {
                    // 强制更新，立即显示对话框
                    UpdateDialogFragment.newInstance(updateInfo)
                        .show(supportFragmentManager, "update_dialog")
                }
            }
    }
}
```

---

## ⚙️ 配置选项说明

### **update.json 字段说明**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| versionCode | Int | ✅ | 版本号（整数，必须递增） |
| versionName | String | ✅ | 版本名称（显示给用户） |
| downloadUrl | String | ✅ | APK 下载地址 |
| releaseNotes | String | ✅ | 更新日志（支持 \n 换行） |
| forceUpdate | Boolean | ❌ | 是否强制更新（默认 false） |
| minVersionCode | Int | ❌ | 最低支持的版本代码 |
| fileSize | Long | ❌ | 文件大小（字节） |
| md5 | String | ❌ | MD5 校验值（暂未实现） |
| publishTime | String | ❌ | 发布时间（ISO 8601 格式） |

### **forceUpdate 使用说明**

```json
// 普通更新（用户可以取消）
"forceUpdate": false

// 强制更新（用户必须更新才能继续使用）
"forceUpdate": true
```

---

## 🔍 测试方法

### **本地测试**

1. **修改 versionCode 为更大的值**
   ```json
   {
     "versionCode": 999,
     "versionName": "9.9.9",
     ...
   }
   ```

2. **将 update.json 放到本地服务器**
   ```bash
   # 使用 Python 快速启动 HTTP 服务器
   python -m http.server 8080
   ```

3. **修改 UpdateChecker 中的 URL**
   ```kotlin
   private const val UPDATE_JSON_URL = "http://10.0.2.2:8080/update.json"
   ```

4. **运行 App 并检查更新**

### **真实环境测试**

1. 安装当前版本（versionCode = 1）
2. 上传新版本 APK 到 GitHub Release
3. 更新 update.json 并提交
4. 在 App 中检查更新
5. 验证下载和安装流程

---

## ⚠️ 注意事项

### **1. 权限问题**
- Android 8.0+ 需要用户授权"安装未知来源应用"
- 首次安装时会弹出系统授权对话框

### **2. 网络环境**
- 建议在 WiFi 环境下下载
- 可以添加网络类型检测（已在 UpdateChecker 中预留）

### **3. 存储空间**
- APK 保存在外部文件目录
- 路径：`/Android/data/com.mockloc/files/updates/`
- 卸载 App 时会自动清理

### **4. 下载中断**
- 当前版本不支持断点续传
- 如果下载失败，用户需要重新点击下载

### **5. MD5 校验**
- 当前版本未实现 MD5 校验
- 生产环境建议添加以确保文件完整性

---

## 🐛 常见问题

### **Q1: 检查更新失败**
**原因**：网络连接问题或 URL 配置错误  
**解决**：
1. 检查网络连接
2. 验证 update.json URL 是否正确
3. 查看 Logcat 日志

### **Q2: 下载失败**
**原因**：下载链接无效或网络中断  
**解决**：
1. 确认 downloadUrl 可访问
2. 检查 GitHub Release 是否公开
3. 重试下载

### **Q3: 安装失败**
**原因**：未授权安装未知来源应用  
**解决**：
1. 前往系统设置
2. 授权"安装未知来源应用"
3. 重新尝试安装

### **Q4: 图标未更新**
**原因**：启动器缓存  
**解决**：
1. 重启手机
2. 或清除启动器缓存

---

## 📊 日志查看

在 Logcat 中过滤 `UpdateChecker` 或 `UpdateDialogFragment` 可以看到详细日志：

```
D/UpdateChecker: Checking for updates from: ...
I/UpdateChecker: ✅ New version available: 1.1.0 (2)
D/UpdateChecker: Downloading APK from: ...
I/UpdateChecker: APK downloaded successfully: ...
I/UpdateDialogFragment: Installation intent started
```

---

## 🎯 下一步优化建议

1. **添加断点续传**：支持大文件下载中断后继续
2. **MD5 校验**：确保下载文件完整性
3. **后台下载**：使用 DownloadManager 支持后台下载
4. **差分更新**：只下载变更部分，减少流量
5. **灰度发布**：支持按比例推送新版本
6. **更新统计**：记录更新成功率和失败原因

---

## 📞 技术支持

如有问题，请查看：
- Logcat 日志
- GitHub Issues
- 项目文档

祝使用愉快！🎉
