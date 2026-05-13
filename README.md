# 虚拟定位应用 (MockLoc)

> **最新版本**: v1.6.1 | [下载 APK](https://gitee.com/eizmme/MockLocations/releases)

基于 **Kotlin + MVVM + Android 10+** 实现的现代化虚拟定位应用，采用 **Material Design 3** 设计规范，无需 ROOT 权限即可修改设备位置。

## ✨ 项目亮点

- 🏗️ **现代架构**: MVVM + Delegate + StateFlow + Fragment，代码清晰易维护
- 🎨 **Material Design 3**: 完整的 MD3 色彩系统，支持深色模式
- 🌙 **完美夜间模式**: 手动适配所有 UI 元素，切换流畅无闪烁
- 🎮 **悬浮窗控制**: Manager + Controller 架构，窗口切换带动画
- 🚀 **性能优化**: 协程异步处理、LeakCanary 内存检测、地址缓存
- 📱 **用户体验**: 丰富的弹簧动画
- 🔗 **Delegate 模式拆分**: ViewModel 逻辑按功能域拆分为独立 Delegate
- 🌉 **ServiceConnector 桥接层**: Service↔ViewModel 解耦通信

## 📱 核心功能

### 定位模拟
- ✅ **TestProvider 机制** - 无需 ROOT，通过系统 API 模拟 GPS/网络定位
- ✅ **连续移动** - 摇杆控制方向和速度，实时位置更新
- ✅ **多速度模式** - 步行 (5km/h)、跑步 (12km/h)、骑行 (20km/h)
- ✅ **随机偏移** - 增加位置真实性，避免被检测
- ✅ **路线模拟** - 支持多点路线规划与自动循环播放

### 地图交互
- ✅ **高德地图集成** - 3D 地图、POI 搜索、地理编码
- ✅ **标记选点** - 点击/长按地图添加红色标记，支持拖拽调整
- ✅ **定位蓝点** - 实时显示当前位置，跟随模拟位置移动
- ✅ **图层切换** - 标准地图、卫星地图、夜景地图
- ✅ **智能搜索** - 默认20km范围，结果少时自动扩大到50km

### 位置管理
- ✅ **历史记录** - 自动保存使用过的位置，支持搜索过滤
- ✅ **收藏功能** - 收藏常用位置，快速切换
- ✅ **坐标输入** - 手动输入经纬度，精确选点
- ✅ **位置分享** - 分享位置给好友
- ✅ **一键清除** - 搜索框清除按钮，快速重置搜索

### 悬浮窗系统
- ✅ **三种窗口** - 摇杆窗、地图窗、历史窗，一键切换
- ✅ **平滑动画** - 淡入淡出效果，窗口切换流畅
- ✅ **拖动支持** - 所有窗口可自由拖动到任意位置
- ✅ **主题同步** - 自动跟随系统深色/浅色模式

### 技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| **语言** | Kotlin | 1.9+ |
| **架构** | MVVM + Service | - |
| **UI** | Material Design 3 | 1.12.0 |
| **地图** | 高德地图 3D SDK | 11.1.0 |
| **数据库** | Room | 2.7.0 |
| **异步** | Coroutines + Flow | 1.10.2 |
| **依赖注入** | ViewModel + by viewModels | - |
| **日志** | Timber | 5.0.1 |
| **内存检测** | LeakCanary | 2.14 (Debug) |

### 最低要求
- **Android 10+ (API 29)**
- **JDK 17+**
- **Gradle 9.3+**

## 🏗️ 项目结构

```
app/src/main/java/com/mockloc/
├── 📱 VirtualLocationApp.kt          # Application 初始化 (151行)
│
├── 🎨 ui/                             # UI 层
│   ├── main/                          # 主界面 (MVVM + Delegate)
│   │   ├── MainActivity.kt (81行)     # 轻量容器，Service 绑定
│   │   ├── MainFragment.kt (735行)    # UI 展示、用户交互
│   │   ├── MainViewModel.kt (946行)   # 业务逻辑、StateFlow 状态管理
│   │   └── delegate/                  # 🆕 Delegate 模式拆分
│   │       ├── SearchDelegate.kt (223行)      # 搜索功能
│   │       ├── SimulationDelegate.kt (297行)  # 模拟控制
│   │       ├── RouteEditDelegate.kt (301行)   # 路线编辑
│   │       ├── ThemeDelegate.kt (321行)       # 主题切换
│   │       └── DialogDelegate.kt (72行)       # 对话框
│   ├── history/                       # 历史记录界面
│   ├── favorite/                      # 收藏功能界面
│   ├── settings/                      # 设置界面
│   ├── search/                        # 搜索结果适配器
│   ├── permission/                    # 权限引导界面
│   ├── splash/                        # 启动页
│   └── update/                        # 更新对话框
│
├── ⚙️ service/                        # 服务层
│   ├── LocationService.kt (478行)     # 🔑 核心定位服务 (TestProvider)
│   ├── FloatingWindowManager.kt (725行) # 🔑 悬浮窗管理器
│   ├── JoystickWindowController.kt (323行) # 摇杆窗口控制器
│   ├── MapWindowController.kt (815行) # 地图窗口控制器
│   ├── HistoryWindowController.kt (344行) # 历史窗口控制器
│   ├── RouteControlWindowController.kt (249行) # 🆕 路线控制窗口
│   ├── RoutePlaybackEngine.kt (207行) # 🆕 路线播放引擎
│   ├── PositionInjector.kt (221行)    # 位置注入器
│   ├── MovementController.kt (165行)  # 移动控制器
│   ├── WindowController.kt (79行)     # 窗口控制器接口
│   ├── DragLinearLayout.kt (48行)     # 可拖动布局容器
│   ├── DragHelper.kt (86行)           # 拖动辅助类
│   └── FloatingHistoryAdapter.kt (69行) # 悬浮窗历史适配器
│
├── 🔗 core/                           # 🆕 核心桥接层
│   ├── service/
│   │   └── LocationServiceConnector.kt (145行) # Service↔ViewModel 桥接
│   ├── common/
│   │   └── AppResult.kt (50行)        # 统一结果封装
│   └── utils/
│       └── MapDelegate.kt (181行)     # 地图逻辑复用
│
├── 💾 data/                           # 数据层
│   ├── db/                            # Room 数据库
│   │   ├── AppDatabase.kt (238行)     # 数据库实例
│   │   ├── FavoriteLocation.kt + Dao  # 收藏位置
│   │   ├── HistoryLocation.kt + Dao   # 历史位置
│   │   ├── SearchHistory.kt + Dao     # 搜索历史
│   │   └── SavedRoute.kt + Dao        # 🆕 路线数据
│   └── repository/                    # Repository 层
│       ├── LocationRepository.kt (137行) # 位置数据仓库
│       ├── SearchRepository.kt (99行)    # 搜索数据仓库
│       ├── FavoriteRepository.kt (53行)  # 收藏数据仓库
│       └── RouteRepository.kt (76行)     # 🆕 路线数据仓库
│
├── 🔄 repository/                     # 外部服务封装
│   └── PoiSearchHelper.kt (193行)     # POI 搜索封装 (高德SDK)
│
├── 🛠️ util/                           # 工具层 (10个文件)
│   ├── 动画相关 (2个)
│   │   ├── AnimationHelper.kt (519行) # 基础+高级动画
│   │   └── AnimationConfig.kt (202行) # 动画配置
│   ├── 地图相关 (2个)
│   │   ├── MapUtils.kt (129行)        # 坐标转换 (GCJ02 ↔ WGS84 ↔ BD09)
│   │   └── ThemeUtils.kt (20行)       # 主题上下文创建
│   ├── 权限相关 (1个)
│   │   └── PermissionHelper.kt (105行) # 权限检查
│   └── 其他工具 (5个)
│       ├── AddressCache.kt (242行)    # 地址缓存 (LRU)
│       ├── UIFeedbackHelper.kt (117行) # Toast/Snackbar 封装
│       ├── CrashHandler.kt (191行)    # 崩溃处理
│       ├── PrefsConfig.kt (92行)      # 偏好配置常量
│       └── UpdateChecker.kt (249行)   # 更新检查
│
├── 🎮 widget/                         # 自定义控件
│   ├── JoystickView.kt (266行)        # 圆形摇杆
│   └── ButtonView.kt (148行)          # 八方向按钮
│
└── 📡 receiver/                       # 广播接收器
    └── BootReceiver.kt (84行)         # 开机自启
```

## 🚀 快速开始

### 1. 环境准备
- **Android Studio**: Hedgehog (2023.1.1) 或更高版本
- **JDK**: 17+
- **Android SDK**: API 29 - 36
- **Kotlin**: 1.9+

### 2. 配置高德地图 Key

1. 访问 [高德开放平台](https://lbs.amap.com/)
2. 注册账号并创建应用
3. 获取 Android 平台 Key
4. 在项目根目录创建 `local.properties` 文件（如果不存在）
5. 添加以下内容：

```properties
AMAP_API_KEY=你的高德地图Key
```

> ⚠️ **注意**: `local.properties` 已被 `.gitignore` 忽略，不会提交到版本控制。

### 3. 编译运行

```bash
# 克隆项目
git clone <repository-url>
cd demo

# 打开 Android Studio
# File -> Open -> 选择项目目录

# 等待 Gradle 同步完成
# 点击 Run 按钮运行
```

### 4. 启用模拟位置权限

在真机上运行前，需要启用模拟位置权限：

1. 打开 **设置** → **开发者选项**
2. 找到 **选择模拟位置信息应用**
3. 选择 **MockLoc**

> 💡 如果没有看到“开发者选项”，请在 **关于手机** 中连续点击 **版本号** 7 次。

## 📋 权限说明

### 必需权限
| 权限 | 说明 | 用途 |
|------|------|------|
| `ACCESS_FINE_LOCATION` | 精确定位 | 模拟GPS定位 |
| `ACCESS_COARSE_LOCATION` | 粗略定位 | 模拟网络定位 |
| `ACCESS_MOCK_LOCATION` | 模拟定位 | 核心功能权限 |
| `SYSTEM_ALERT_WINDOW` | 悬浮窗 | 显示摇杆控制面板 |

### 可选权限
| 权限 | 说明 | 用途 |
|------|------|------|
| `ACCESS_BACKGROUND_LOCATION` | 后台定位 | Android 10+后台定位 |
| `FOREGROUND_SERVICE` | 前台服务 | 保持服务运行 |
| `RECEIVE_BOOT_COMPLETED` | 开机自启 | 自动启动服务 |

## 🎮 使用说明

### 基本操作
1. **设置位置**
   - 在地图上点击或长按选择位置
   - 使用搜索框搜索地点
   - 输入经纬度坐标

2. **开始模拟**
   - 点击右下角FAB按钮启动模拟
   - 状态栏显示"模拟中"

3. **移动控制**
   - 悬浮摇杆窗自动显示
   - 拖动摇杆控制移动方向
   - 切换速度模式（步行/跑步/骑行）

4. **停止模拟**
   - 再次点击FAB按钮停止
   - 或关闭悬浮窗

### 悬浮窗操作
- **拖动** - 长按悬浮窗拖动到任意位置
- **关闭** - 点击关闭按钮
- **切换** - 在不同悬浮窗之间切换

## ⚙️ 设置选项

### 移动设置
- **步行速度** - 默认 5 km/h
- **跑步速度** - 默认 12 km/h
- **骑行速度** - 默认 20 km/h

### 定位设置
- **海拔高度** - 设置模拟位置的海拔
- **随机偏移** - 开启后增加位置真实性
- **摇杆类型** - 圆形摇杆 / 方向键

### 其他设置
- **历史记录有效期** - 自动清理过期记录
- **日志记录** - 开启调试日志
- **关于应用** - 版本信息

## 🔧 架构设计

### MVVM + Delegate 架构

项目采用 **MVVM + Delegate** 架构模式，通过 Delegate 拆分和 ServiceConnector 桥接层实现清晰的职责分离：

```
┌─────────────────────────────────────────────────┐
│              UI Layer (Fragment)                 │
│  - 地图初始化、用户交互、动画控制                │
│  - 观察 ViewModel 的 StateFlow                   │
└──────────────────┬──────────────────────────────┘
                   │ StateFlow 收集
┌──────────────────▼──────────────────────────────┐
│           ViewModel Layer + Delegate             │
│  - MainViewModel: 状态管理、Delegate 调度        │
│  - SearchDelegate: 搜索功能                      │
│  - SimulationDelegate: 模拟控制                  │
│  - RouteEditDelegate: 路线编辑                   │
│  - ThemeDelegate: 主题切换                       │
│  - DialogDelegate: 对话框                        │
└──────────────────┬──────────────────────────────┘
                   │ ServiceConnector 桥接
┌──────────────────▼──────────────────────────────┐
│         Service Layer (via Connector)            │
│  - LocationServiceConnector: Service↔VM 解耦    │
│  - LocationService (TestProvider)                │
│  - FloatingWindowManager                         │
└──────────────────┬──────────────────────────────┘
                   │ Room Database / Repository
┌──────────────────▼──────────────────────────────┐
│              Data Layer                          │
│  - Room Database (4 个 Entity + Dao)             │
│  - Repository (Location, Search, Favorite, Route)│
│  - PoiSearchHelper (高德 SDK)                    │
└─────────────────────────────────────────────────┘
```

### 关键设计模式

#### 1. Delegate 模式 (ViewModel 拆分)

```kotlin
class MainViewModel : ViewModel() {
    val searchDelegate = SearchDelegate(this)       // 搜索功能
    val simulationDelegate = SimulationDelegate(this) // 模拟控制
    val routeEditDelegate = RouteEditDelegate(this)   // 路线编辑
    val themeDelegate = ThemeDelegate(this)           // 主题切换
    val dialogDelegate = DialogDelegate(this)         // 对话框
}
```

**优势**:
- ✅ 单一职责：每个 Delegate 只负责一个功能域
- ✅ 降低复杂度：ViewModel 从 440 行拆分为 946 行主类 + 5 个 Delegate
- ✅ 易于测试：每个 Delegate 可独立测试
- ✅ 易于扩展：新增功能只需添加 Delegate

#### 2. ServiceConnector 桥接层

```kotlin
LocationServiceConnector    // Service↔ViewModel 桥接
├── 封装 Service 绑定/解绑生命周期
├── 提供 Flow-based 的 Service 状态观察
└── 解耦 ViewModel 与 Service 直接依赖
```

**优势**:
- ✅ 生命周期安全：自动处理 Service 绑定状态
- ✅ 解耦通信：ViewModel 不直接持有 Service 引用
- ✅ 可测试性：可替换为 Mock Connector

#### 3. Manager + Controller 模式 (悬浮窗)

```kotlin
FloatingWindowManager          // 管理器：统一调度
├── JoystickWindowController   // 控制器：摇杆窗口
├── MapWindowController        // 控制器：地图窗口
├── HistoryWindowController    // 控制器：历史窗口
└── RouteControlWindowController // 控制器：路线控制窗口
```

**优势**:
- ✅ 单一职责：每个控制器只管理一个窗口
- ✅ 易于扩展：添加新窗口只需新增 Controller
- ✅ 统一管理：Manager 处理窗口切换、主题同步

#### 4. StateFlow 状态管理

```kotlin
class MainViewModel : ViewModel() {
    // 不可变 StateFlow，供 Fragment 观察
    val mapState: StateFlow<MapState>
    val searchState: StateFlow<SearchState>
    val simulationState: StateFlow<SimulationState>
    
    // 可变 StateFlow，内部更新
    private val _mapState = MutableStateFlow(MapState())
}
```

**优势**:
- ✅ 生命周期安全：自动跟随 Lifecycle
- ✅ 线程安全：协程调度
- ✅ 配置变化安全：ViewModel 存活

---

## 🎨 设计规范

### Material Design 3

项目完整实现了 **MD3 色彩系统**：

| 颜色角色 | 日间模式 | 夜间模式 | 用途 |
|---------|---------|---------|------|
| Primary | `#26A69A` | `#4DB6AC` | 主要操作按钮、FAB |
| Secondary | `#4DB6AC` | `#80CBC4` | 次要元素 |
| Background | `#E0F2F1` | `#1A3D3A` | 页面背景 |
| Surface | `#FFFFFF` | `#1E1E1E` | 卡片、对话框 |

### 夜间模式适配

通过 `configChanges="uiMode"` 防止 Activity 重建，手动更新所有 UI 元素：

```kotlin
override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    updateNightModeStatus()     // 更新地图类型
    updateViewBackgrounds()     // 手动刷新 UI 颜色
}
```

### 动画系统

项目提供了完整的动画工具库：

- **AnimationHelper**: 淡入淡出、数字滚动、脉冲动画、弹跳效果
- **AnimationConfig**: 统一的时长和插值器配置

---

## 📝 注意事项

1. **模拟定位权限**
   - 需要在开发者选项中开启"允许模拟位置"
   - 路径：设置 → 开发者选项 → 选择模拟位置信息应用

2. **悬浮窗权限**
   - Android 6.0+ 需要手动授权
   - 应用会自动引导用户授权

3. **后台定位**
   - Android 10+ 需要额外申请后台定位权限
   - 建议在使用时授予"始终允许"

4. **电池优化**
   - 建议将应用加入电池优化白名单
   - 避免系统杀死后台服务

## 🐛 常见问题

### Q: 无法模拟定位？
**A**: 检查以下项：
1. ✅ 是否授予定位权限
2. ✅ 是否在开发者选项中开启模拟位置
3. ✅ 是否选择了本应用作为模拟位置应用
4. ✅ 应用是否为 Debug 版本（Release 版本需要特殊配置）

### Q: 悬浮窗不显示？
**A**: 检查以下项：
1. ✅ 是否授予悬浮窗权限（设置 → 应用管理 → MockLoc → 悬浮窗）
2. ✅ 是否启动了模拟服务
3. ✅ 系统是否阻止了悬浮窗显示（部分手机需手动允许）

### Q: 位置跳动或不稳定？
**A**: 尝试以下方法：
1. 关闭随机偏移功能
2. 降低更新频率（在设置中调整）
3. 检查是否有其他定位应用干扰
4. 重启 LocationService

### Q: 夜间模式切换后 UI 颜色不变？
**A**: 这是已知问题，已通过 `configChanges` + 手动更新修复。如果仍然出现，请重启应用。

### Q: 编译时提示高德地图 Key 无效？
**A**: 检查 `local.properties` 文件中的 `AMAP_API_KEY` 是否正确配置。

---

## 📈 性能优化

### 内存管理
- ✅ **LeakCanary**: Debug 版本集成，自动检测内存泄漏
- ✅ **协程作用域**: 正确使用 `viewModelScope` 和 `lifecycleScope`
- ✅ **资源清理**: Service 解绑时彻底清理引用

### 启动优化
- ✅ **懒加载**: Fragment 和 ViewModel 按需创建
- ✅ **异步初始化**: 高德地图 SDK 异步初始化
- ✅ **地址缓存**: LRU 策略减少网络请求

### 渲染优化
- ✅ **ViewBinding**: 类型安全，避免 findViewById
- ✅ **RecyclerView**: 列表复用，DiffUtil 增量更新
- ✅ **硬件加速**: 动画使用 GPU 加速

---

## 🚧 已知问题与改进计划

### 高优先级 🔴
- [x] 悬浮窗地图搜索范围过小（5km → 已优化为20km，自动扩大到50km）
- [x] 更新对话框内容显示不全（已添加滚动支持）
- [ ] Release 版本禁用混淆（高德地图死锁问题）

### 中优先级 🟡
- [x] 添加单元测试覆盖核心逻辑 — 已有 MapUtilsTest、PrefsConfigTest、DataEntityTest
- [x] Tertiary 颜色重新设计（已完成主题色重构）
- [x] 摇杆颜色主题化（已完成，ThemeDelegate）

### 低优先级 🟢
- [ ] 支持用户自定义主题色
- [ ] 添加 E2E 测试（Espresso）
- [ ] 集成 Firebase Performance 监控

---

## 📝 版本历史

### v1.6.1 (2026-05-13)
🐛 **修复更新提示问题 + 增强安全性**
- 🐛 修复从旧版本升级后仍弹出更新提示的问题
- 🔧 UpdateChecker 缓存机制增加版本验证逻辑
- 🔒 添加 APK MD5 完整性校验，防止文件篡改
- ⚡ 优化缓存管理：清除过期缓存后继续网络检查
- 🛡️ 异常处理更健壮，避免崩溃风险

### v1.6.0 (2026-05-13)
🏗️ **架构重构：Delegate 模式 + ServiceConnector 桥接层**
- 🆕 ViewModel 拆分为 5 个 Delegate (Search/Simulation/RouteEdit/Theme/Dialog)
- 🆕 新增 LocationServiceConnector 桥接层，解耦 Service↔ViewModel
- 🆕 新增 RouteControlWindowController、RoutePlaybackEngine
- 🆕 新增 core 桥接层 (AppResult、MapDelegate)
- 🆕 数据层新增 Repository 拆分和 SavedRoute 实体
- 🧹 移除 ProgressivePermissionManager、OnboardingManager 等已废弃模块
- 🧹 合并动画工具为 AnimationHelper + AnimationConfig
- ✅ 摇杆颜色主题化 (ThemeDelegate)
- ✅ 添加单元测试 (MapUtilsTest、PrefsConfigTest、DataEntityTest)

### v1.5.0 (2026-05-02)
🎯 **路线模拟与循环播放功能**
- ✨ 新增路线模拟与循环播放功能
- 🐛 修复循环播放时起点跳跃的严重 Bug
-  优化路线模拟时相机跟随逻辑，避开工具栏遮挡
- 🔧 统一位置更新接口参数顺序，降低开发风险
- ⚡ 异步化数据库清理，提升模拟定位流畅度
- 🧹 PoiSearchHelper 单例化，减少内存泄漏风险
- 🛡️ 增强 Fragment 异步回调生命周期防护

### v1.4.1 (2026-04-24)
🔧 **修复升级数据丢失问题**
- 移除 DEBUG 模式破坏性迁移
- 修复 MIGRATION_2_3 保留最新记录
- 统一索引命名为 Room 标准格式
- 确保 1.3.0→1.4.1 升级数据完整

---

## 📄 开源协议

本项目采用 **GPL-3.0** 协议开源。

## 🙏 致谢

- [影梭 (gogogo)](https://github.com/zcshou/gogogo) - 参考项目
- [高德地图SDK](https://lbs.amap.com/) - 地图和定位服务
- [Material Design](https://material.io/) - UI 设计规范
- [Android Jetpack](https://developer.android.com/jetpack) - 现代化开发组件

## 📮 贡献指南

欢迎提交 Issue 或 Pull Request！

### 提交 Issue
- 描述清楚问题现象
- 提供复现步骤
- 附上日志截图（如有）

### 提交 PR
1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

---

## ⚠️ 免责声明

**本应用仅供学习和研究使用，请勿用于任何违法用途。**

使用本应用所产生的一切后果由使用者自行承担。开发者不对因使用本应用而导致的任何直接或间接损失负责。

---

<div align="center">

**Made with ❤️ by MockLoc Team**

[⭐ Star this repo](https://github.com/zhai110/MockLocations) · [🐛 Report Bug](https://github.com/zhai110/MockLocations/issues) · [💡 Request Feature](https://github.com/zhai110/MockLocations/issues)

</div>
