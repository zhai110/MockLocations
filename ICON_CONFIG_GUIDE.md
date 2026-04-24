# App 图标配置说明

## ✅ 已完成的修改

### 1. **使用 Adaptive Icon（自适应图标）**
- 文件位置：`app/src/main/res/mipmap-anydpi-v26/`
  - `ic_launcher.xml` - 标准图标
  - `ic_launcher_round.xml` - 圆角图标

### 2. **AndroidManifest.xml 配置**
```xml
android:icon="@mipmap/ic_launcher"
android:roundIcon="@mipmap/ic_launcher_round"
```

### 3. **图标组成**
- **背景层**：`@drawable/ic_launcher_background` (青绿色 #26A69A)
- **前景层**：`@drawable/ic_launcher_foreground` (白色定位图标)
- **单色层**：`@drawable/ic_launcher_foreground` (Android 13+ 支持)

---

## 📱 圆角效果说明

### **不同 Android 版本的显示效果**

| Android 版本 | 图标形状 | 说明 |
|-------------|---------|------|
| Android 7.1 及以下 | 方形/圆形 | 由启动器决定 |
| Android 8.0 - 12 | **自适应圆角** | 系统自动应用圆角蒙版 |
| Android 13+ | **自适应圆角 + 单色模式** | 支持主题化图标 |

### **圆角半径**
- 系统会根据设备自动调整圆角半径
- 通常在 **16dp - 24dp** 之间
- 无需手动设置，系统统一管理

---

## 🎨 图标设计特点

### **当前图标样式**
```
┌─────────────────┐
│                 │
│    ┌─────┐      │
│    │  ●  │      │  ← 白色定位图标
│    └─────┘      │
│                 │
└─────────────────┘
   青绿色背景 (#26A69A)
```

### **安全区域**
- 前景图标应在 **72dp × 72dp** 的中心区域内
- 周围留出 **18dp** 的边距（总尺寸 108dp）
- 确保在不同形状的蒙版下都能完整显示

---

## 🔧 如何自定义图标

### **方法 1：修改颜色**
编辑 `app/src/main/res/drawable/ic_launcher_background.xml`：
```xml
<path
    android:fillColor="#你的颜色代码"
    android:pathData="M0,0h108v108h-108z" />
```

### **方法 2：修改图标图形**
编辑 `app/src/main/res/drawable/ic_launcher_foreground.xml`：
- 修改 `pathData` 改变图标形状
- 修改 `fillColor` 改变图标颜色

### **方法 3：使用 Image Asset Studio（推荐）**
1. 在 Android Studio 中右键点击 `res` 文件夹
2. 选择 **New** → **Image Asset**
3. 选择图标类型：**Adaptive and Legacy**
4. 上传你的图标图片
5. 调整位置和缩放
6. 点击 **Next** → **Finish**

---

## 📦 打包测试清单

### **构建前检查**
- [x] 签名文件存在：`app/release.jks`
- [x] local.properties 配置正确
- [x] build.gradle.kts 使用 release 签名
- [x] 图标配置为 mipmap 资源

### **构建步骤**
1. **清理项目**：Build → Clean Project
2. **重新构建**：Build → Rebuild Project
3. **生成签名 APK**：
   - Build → Generate Signed Bundle / APK
   - 选择 APK
   - 确认签名配置
   - 选择 release 构建类型
   - 勾选 V1 + V2 签名
   - 点击 Finish

### **验证 APK**
构建完成后，APK 位置：
```
app/build/outputs/apk/release/app-release.apk
```

验证签名：
```bash
# 方法 1：使用 verify_signature.bat
verify_signature.bat

# 方法 2：手动验证
apksigner verify --verbose app\build\outputs\apk\release\app-release.apk
```

---

## ⚠️ 注意事项

1. **不要删除 drawable/ic_launcher.xml**
   - 虽然 Manifest 已改为使用 mipmap
   - 但某些旧设备可能仍会引用

2. **图标缓存问题**
   - 安装新版本后，如果图标未更新
   - 重启手机或清除启动器缓存

3. **测试不同设备**
   - Android 8.0+：应显示圆角图标
   - Android 13+：支持单色主题图标
   - 不同品牌手机：圆角半径可能略有差异

---

## 🎯 下一步

1. ✅ 图标已配置为圆角自适应图标
2. ⏳ 在 Android Studio 中构建 Release APK
3. ⏳ 安装到测试设备
4. ⏳ 验证图标显示效果
5. ⏳ 测试远程更新功能

如有问题，请查看 Android Studio 的 Build 窗口输出日志。
