# 远程更新功能 - 快速测试指南

## ✅ 已完成的工作

### **1. 代码实现**
- ✅ UpdateInfo.kt - 数据模型
- ✅ UpdateChecker.kt - 更新检查器
- ✅ UpdateDialogFragment.kt - 更新对话框
- ✅ dialog_update.xml - 对话框布局

### **2. 配置完成**
- ✅ build.gradle.kts - 添加 OkHttp 和 Gson 依赖
- ✅ AndroidManifest.xml - 添加权限和 FileProvider
- ✅ file_paths.xml - FileProvider 路径配置
- ✅ SettingsActivity.kt - 集成检查更新功能

### **3. 文档完善**
- ✅ UPDATE_FEATURE_GUIDE.md - 完整使用指南
- ✅ update.json - 配置模板

---

## 🧪 测试步骤

### **步骤 1：同步 Gradle 依赖**

在 Android Studio 中：
```
File → Sync Project with Gradle Files
```

等待同步完成，确保 OkHttp 和 Gson 依赖下载成功。

---

### **步骤 2：构建并安装当前版本**

1. **清理项目**
   ```
   Build → Clean Project
   ```

2. **重新构建**
   ```
   Build → Rebuild Project
   ```

3. **运行到设备**
   ```
   Run → Run 'app'
   ```

4. **验证安装**
   - App 正常启动
   - 进入 设置 → 关于
   - 查看版本号显示

---

### **步骤 3：测试检查更新功能**

#### **场景 A：无新版本（当前情况）**

1. 打开 App
2. 进入 **设置** → **关于**
3. 点击 **检查更新**
4. 应该显示："当前已是最新版本"

#### **场景 B：模拟有新版本**

**方法 1：修改本地 update.json 测试**

1. 编辑 `update.json`，将 versionCode 改为更大的值：
   ```json
   {
     "versionCode": 999,
     "versionName": "9.9.9",
     ...
   }
   ```

2. 启动本地 HTTP 服务器：
   ```bash
   # 在项目根目录执行
   python -m http.server 8080
   ```

3. 修改 `UpdateChecker.kt` 第 28 行：
   ```kotlin
   private const val UPDATE_JSON_URL = "http://10.0.2.2:8080/update.json"
   ```
   > 注意：Android 模拟器访问本机使用 `10.0.2.2`，真机需要使用电脑的实际 IP 地址

4. 重新运行 App 并检查更新

5. 应该看到更新对话框，显示新版本信息

**方法 2：上传到 GitHub 测试（推荐）**

1. 提交 update.json 到 GitHub：
   ```bash
   git add update.json
   git commit -m "Test update configuration"
   git push origin master
   ```

2. 等待几秒让 GitHub 生效

3. 在 App 中检查更新

---

### **步骤 4：测试完整更新流程**

#### **准备新版本 APK**

1. **修改版本号**
   
   编辑 `app/build.gradle.kts`：
   ```kotlin
   defaultConfig {
       versionCode = 2        // ← 改为 2
       versionName = "1.1.0"  // ← 改为 1.1.0
   }
   ```

2. **构建 Release APK**
   ```
   Build → Generate Signed Bundle / APK
   → 选择 APK
   → 选择 release
   → 勾选 V1 + V2
   → Finish
   ```

3. **获取 APK 文件**
   ```
   app\build\outputs\apk\release\app-release.apk
   ```

#### **上传到 GitHub Release**

1. 访问：https://github.com/zhai110/MockLocations/releases
2. 点击 **Draft a new release**
3. 填写：
   - Tag version: `v1.1.0`
   - Release title: `Version 1.1.0`
   - 描述：更新内容
4. 上传 APK 文件
5. 点击 **Publish release**

#### **更新 update.json**

1. 获取 APK 下载地址：
   ```
   https://github.com/zhai110/MockLocations/releases/download/v1.1.0/app-release.apk
   ```

2. 编辑 `update.json`：
   ```json
   {
     "versionCode": 2,
     "versionName": "1.1.0",
     "downloadUrl": "https://github.com/zhai110/MockLocations/releases/download/v1.1.0/app-release.apk",
     "releaseNotes": "✨ 新增功能：\n- 远程更新功能\n\n🐛 Bug修复：\n- 修复若干问题",
     "forceUpdate": false,
     "minVersionCode": 1,
     "fileSize": 15728640,
     "md5": "",
     "publishTime": "2026-04-24T10:00:00Z"
   }
   ```

3. 提交到 Git：
   ```bash
   git add update.json
   git commit -m "Release version 1.1.0"
   git push origin master
   ```

#### **测试更新**

1. 确保手机安装的是旧版本（versionCode = 1）
2. 打开 App
3. 进入 **设置** → **关于** → **检查更新**
4. 应该看到更新对话框
5. 点击 **立即更新**
6. 观察下载进度
7. 下载完成后自动弹出安装界面
8. 授权安装未知来源应用（首次）
9. 完成安装

---

## 🔍 验证清单

- [ ] Gradle 依赖同步成功
- [ ] App 正常启动
- [ ] 版本号正确显示
- [ ] 检查更新按钮可点击
- [ ] 无新版本时提示正确
- [ ] 有新版本时显示对话框
- [ ] 更新日志格式正确
- [ ] 文件大小显示正确
- [ ] 下载进度条正常工作
- [ ] 下载完成后自动安装
- [ ] 安装成功后新版本正常运行

---

## 🐛 调试技巧

### **查看日志**

在 Android Studio 的 Logcat 中过滤：
```
UpdateChecker
UpdateDialogFragment
```

### **常见日志输出**

```
D/UpdateChecker: Checking for updates from: ...
D/UpdateChecker: Update info received: {...}
D/UpdateChecker: Current version: 1.0.0 (1)
D/UpdateChecker: Remote version: 1.1.0 (2)
I/UpdateChecker: ✅ New version available: 1.1.0 (2)
D/UpdateChecker: Downloading APK from: ...
I/UpdateChecker: APK downloaded successfully: ...
I/UpdateDialogFragment: Installation intent started
```

### **网络问题排查**

如果检查更新失败：
1. 检查网络连接
2. 验证 URL 是否可访问（浏览器打开）
3. 查看 Logcat 错误信息
4. 检查防火墙设置

---

## ⚠️ 注意事项

1. **GitHub Raw 延迟**
   - 提交 update.json 后可能需要几分钟生效
   - 可以使用 CDN 加速

2. **APK 签名**
   - 确保新旧版本使用相同签名
   - 否则无法覆盖安装

3. **版本号规则**
   - versionCode 必须递增
   - versionName 遵循语义化版本

4. **存储空间**
   - 确保设备有足够空间下载 APK
   - 下载的文件保存在外部存储

5. **权限授权**
   - Android 8.0+ 需要授权安装未知来源
   - 首次安装会弹出系统对话框

---

## 🎯 下一步

测试通过后：

1. **提交代码**
   ```bash
   git add .
   git commit -m "Add remote update feature"
   git push origin master
   ```

2. **发布正式版本**
   - 按照上述流程准备 v1.1.0
   - 上传到 GitHub Release
   - 更新 update.json

3. **通知用户**
   - 在 App 内提示有新版本
   - 或在社交媒体发布公告

---

准备好了吗？开始测试吧！🚀
