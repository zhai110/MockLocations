# 架构设计文档 (Architecture)

本文档详细说明了 MockLoc 项目的架构设计、技术选型和关键决策。

---

## 📋 目录

- [架构概览](#架构概览)
- [技术栈选型](#技术栈选型)
- [核心设计模式](#核心设计模式)
- [模块职责](#模块职责)
- [数据流](#数据流)
- [关键决策](#关键决策)

---

## 架构概览

### 整体架构

项目采用 **分层架构 + MVVM 模式**：

```
┌─────────────────────────────────────────────┐
│              Presentation Layer             │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐ │
│  │ Activity │  │ Fragment │  │   View   │ │
│  └──────────┘  └──────────┘  └──────────┘ │
└──────────────────┬──────────────────────────┘
                   │ StateFlow 观察
┌──────────────────▼──────────────────────────┐
│              ViewModel Layer                │
│  ┌──────────────────────────────────────┐  │
│  │         MainViewModel                │  │
│  │  - StateFlow 状态管理                │  │
│  │  - 业务逻辑处理                      │  │
│  │  - 数据转换                          │  │
│  └──────────────────────────────────────┘  │
└──────────────────┬──────────────────────────┘
                   │ Service 引用注入
┌──────────────────▼──────────────────────────┐
│               Domain Layer                  │
│  ┌────────────────┐  ┌──────────────────┐  │
│  │ LocationService│  │FloatingWindowMgr │  │
│  └────────────────┘  └──────────────────┘  │
└──────────────────┬──────────────────────────┘
                   │ Room Database
┌──────────────────▼──────────────────────────┐
│                Data Layer                   │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐ │
│  │  Room    │  │Repository│  │  Helper  │ │
│  └──────────┘  └──────────┘  └──────────┘ │
└─────────────────────────────────────────────┘
```

### 设计原则

1. **单一职责**: 每个类只负责一个功能领域
2. **依赖倒置**: 高层模块不依赖低层模块，都依赖抽象
3. **开闭原则**: 对扩展开放，对修改关闭
4. **接口隔离**: 使用细粒度的接口而非胖接口

---

## 技术栈选型

### 核心框架

| 技术 | 版本 | 选型理由 |
|------|------|---------|
| **Kotlin** | 1.9+ | 现代语言特性，空安全，协程支持 |
| **MVVM** | - | Google 推荐架构，生命周期感知 |
| **StateFlow** | - | 替代 LiveData，协程原生支持 |
| **ViewBinding** | - | 类型安全，编译时检查 |

### UI 框架

| 技术 | 版本 | 选型理由 |
|------|------|---------|
| **Material Design 3** | 1.12.0 | 最新设计规范，深色模式支持 |
| **Fragment** | 1.8.6 | 模块化 UI，生命周期管理 |
| **BottomSheet** | - | 优雅的面板交互 |
| **RecyclerView** | - | 高性能列表，DiffUtil |

### 数据持久化

| 技术 | 版本 | 选型理由 |
|------|------|---------|
| **Room** | 2.7.0 | SQLite 抽象层，编译时 SQL 检查 |
| **SharedPreferences** | - | 简单配置存储 |

### 异步处理

| 技术 | 版本 | 选型理由 |
|------|------|---------|
| **Coroutines** | 1.10.2 | 轻量级线程，结构化并发 |
| **Flow** | - | 响应式数据流，背压支持 |

### 第三方库

| 库 | 版本 | 用途 |
|----|------|------|
| **高德地图 SDK** | 11.1.0 | 地图渲染、定位、POI 搜索 |
| **Timber** | 5.0.1 | 结构化日志，自动添加标签 |
| **LeakCanary** | 2.14 | 内存泄漏检测（Debug） |

---

## 核心设计模式

### 1. MVVM (Model-View-ViewModel)

#### 为什么选择 MVVM？

✅ **生命周期安全**: ViewModel 在配置变化时存活  
✅ **可测试性**: ViewModel 不依赖 Android 框架  
✅ **关注点分离**: UI 逻辑与业务逻辑解耦  
✅ **数据驱动**: StateFlow 自动更新 UI  

#### 实现示例

```kotlin
// ViewModel: 状态管理
class MainViewModel : ViewModel() {
    private val _mapState = MutableStateFlow(MapState())
    val mapState: StateFlow<MapState> = _mapState.asStateFlow()
    
    fun selectPosition(latLng: LatLng) {
        _mapState.update { it.copy(markedPosition = latLng) }
    }
}

// Fragment: 观察状态
class MainFragment : Fragment() {
    private val viewModel: MainViewModel by activityViewModels()
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.mapState.collect { state ->
                updateMapUI(state)  // 自动响应状态变化
            }
        }
    }
}
```

---

### 2. Manager + Controller 模式

#### 应用场景：悬浮窗系统

**问题**: 需要管理多个悬浮窗，支持切换、主题同步、动画效果

**解决方案**:
- **Manager**: 统一管理窗口生命周期、切换逻辑
- **Controller**: 每个窗口独立控制器，负责自己的 UI

```kotlin
// Manager: 统一调度
class FloatingWindowManager {
    private var joystickController: JoystickWindowController? = null
    private var mapController: MapWindowController? = null
    private var historyController: HistoryWindowController? = null
    
    fun switchToMap() {
        hide()  // 隐藏当前窗口
        currentWindowType = WINDOW_TYPE_MAP
        show()  // 显示新窗口
    }
}

// Controller: 独立管理
class MapWindowController : WindowController {
    override fun initialize() { /* 初始化地图 */ }
    override fun show() { /* 显示窗口 */ }
    override fun hide() { /* 隐藏窗口 */ }
    override fun destroy() { /* 清理资源 */ }
}
```

**优势**:
- ✅ 单一职责：每个 Controller 只管理一个窗口
- ✅ 易于扩展：添加新窗口只需新增 Controller
- ✅ 统一管理：Manager 处理窗口切换、主题同步
- ✅ 资源隔离：每个 Controller 独立管理自己的资源

---

### 3. Repository 模式

#### 应用场景：数据访问

```kotlin
// Repository: 数据访问抽象
class PoiSearchHelper(private val context: Context) {
    fun searchPlace(keyword: String, callback: (List<PlaceItem>) -> Unit) {
        // 封装高德 POI 搜索 API
        // 提供统一的回调接口
    }
}

// ViewModel: 使用 Repository
class MainViewModel : ViewModel() {
    private var poiSearchHelper: PoiSearchHelper? = null
    
    fun searchPlaces(query: String) {
        poiSearchHelper?.searchPlace(query) { results ->
            _searchState.update { it.copy(results = results) }
        }
    }
}
```

**优势**:
- ✅ 解耦：ViewModel 不直接依赖高德 SDK
- ✅ 可测试：可以 mock Repository
- ✅ 灵活性：可以轻松切换数据源

---

## 模块职责

### UI Layer (ui/)

| 模块 | 职责 | 关键文件 |
|------|------|---------|
| **main** | 主界面 UI，用户交互 | MainActivity, MainFragment |
| **history** | 历史记录列表 | HistoryActivity, HistoryAdapter |
| **favorite** | 收藏位置管理 | FavoriteActivity |
| **settings** | 应用设置 | SettingsActivity |
| **search** | 搜索结果展示 | SearchResultAdapter |

**原则**: 
- ❌ 不包含业务逻辑
- ✅ 只负责 UI 展示和用户交互
- ✅ 观察 ViewModel 的 StateFlow

---

### ViewModel Layer (ui/main/MainViewModel.kt)

**职责**:
- ✅ 管理所有 UI 状态（StateFlow）
- ✅ 处理业务逻辑（位置传送、搜索）
- ✅ 数据转换（坐标转换、格式化）
- ✅ 与 Service 交互

**不包含**:
- ❌ UI 引用（View、Context）
- ❌ Android 框架类（除了 Application）

---

### Service Layer (service/)

| 服务 | 职责 |
|------|------|
| **LocationService** | TestProvider 模拟定位，摇杆移动控制 |
| **FloatingWindowManager** | 悬浮窗管理，窗口切换，主题同步 |

**特点**:
- ✅ 前台服务，保持后台运行
- ✅ Binder 通信，向 Activity/Fragment 暴露接口
- ✅ 生命周期独立于 UI

---

### Data Layer (data/db/)

**Room 数据库**:
- `AppDatabase`: 数据库实例
- `HistoryLocation`: 历史位置实体
- `FavoriteLocation`: 收藏位置实体
- DAO: 数据访问对象

**特点**:
- ✅ 编译时 SQL 检查
- ✅ 协程支持（suspend 函数）
- ✅ 迁移支持（版本升级）

---

## 数据流

### 1. 用户点击地图标记

```
用户点击地图
    ↓
MainFragment.onMapClick()
    ↓
viewModel.selectPosition(latLng, moveCamera = false)
    ↓
_mapState.update { copy(markedPosition = latLng, shouldMoveCamera = false) }
    ↓
StateFlow 发射新值
    ↓
MainFragment.observeViewModel() 收集到变化
    ↓
updateMapUI(state)
    ↓
updateMarker(position, moveCamera = state.shouldMoveCamera)
    ↓
地图显示红色标记（不移动相机）
```

---

### 2. 用户点击 FAB 开始模拟

```
用户点击 FAB
    ↓
MainFragment.onFabClick()
    ↓
viewModel.startSimulation()
    ↓
locationService?.startSimulation()
    ↓
LocationService.addTestProviders()
    ↓
TestProvider 开始推送模拟位置
    ↓
系统位置被修改
    ↓
其他应用读取到虚拟位置
```

---

### 3. 夜间模式切换

```
用户切换系统主题
    ↓
Android 发送配置变化事件
    ↓
MainActivity.onConfigurationChanged() (configChanges 阻止重建)
    ↓
MainFragment.onConfigurationChanged() 自动调用
    ↓
updateNightModeStatus()  // 检测主题变化
    ↓
updateMapTypeForNightMode()  // 切换地图类型
    ↓
updateViewBackgrounds()  // 手动刷新所有 View 背景色
    ↓
FloatingWindowManager.syncMapWithSystemTheme()
    ↓
重建悬浮窗视图（使用新的 themedContext）
    ↓
UI 颜色全部更新完成
```

---

## 关键决策

### 决策 1: 为什么使用 StateFlow 而不是 LiveData？

**选择**: StateFlow

**理由**:
1. ✅ **协程原生支持**: 无需 `liveData {}` 构建器
2. ✅ **冷流变热流**: 可以使用 `stateIn()` 控制行为
3. ✅ **操作符丰富**: 支持所有 Flow 操作符
4. ✅ **未来趋势**: Google 推荐使用 Flow/StateFlow

**对比**:
```kotlin
// LiveData (旧方式)
val data: LiveData<String> = liveData {
    emit(fetchData())
}

// StateFlow (新方式)
private val _data = MutableStateFlow("")
val data: StateFlow<String> = _data.asStateFlow()
```

---

### 决策 2: 为什么 MainActivity 这么精简？

**选择**: MainActivity 仅 93 行

**理由**:
1. ✅ **单一职责**: Activity 只负责生命周期和 Service 绑定
2. ✅ **Fragment 复用**: 可以在不同 Activity 中复用 MainFragment
3. ✅ **可测试性**: ViewModel 可以独立测试
4. ✅ **配置变化**: Fragment 自动处理重建

**对比传统做法**:
```kotlin
// 传统做法：Activity 包含所有逻辑 (800+ 行)
class MainActivity : AppCompatActivity() {
    // 地图初始化
    // 搜索逻辑
    // 模拟控制
    // UI 更新
    // ... 所有代码都在这里
}

// 现代做法：Activity 作为容器 (93 行)
class MainActivity : AppCompatActivity() {
    // 只负责 Service 绑定
    // Fragment 管理
}
```

---

### 决策 3: 为什么使用 configChanges 处理夜间模式？

**选择**: `android:configChanges="uiMode"`

**理由**:
1. ✅ **避免重建**: Activity/Fragment 不会销毁重建
2. ✅ **性能更好**: 无需重新初始化地图、Service
3. ✅ **用户体验**: 切换流畅无闪烁

**代价**:
- ⚠️ 需要手动更新所有 UI 元素颜色
- ⚠️ 需要在 `onConfigurationChanged()` 中处理

**实现**:
```kotlin
override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    updateNightModeStatus()     // 更新地图类型
    updateViewBackgrounds()     // 手动刷新 UI 颜色
}
```

---

### 决策 4: 为什么悬浮窗使用 Manager + Controller？

**选择**: FloatingWindowManager + 三个 Controller

**理由**:
1. ✅ **职责清晰**: Manager 管理切换，Controller 管理窗口
2. ✅ **易于扩展**: 添加新窗口只需新增 Controller
3. ✅ **资源隔离**: 每个 Controller 独立管理资源
4. ✅ **主题同步**: Manager 统一重建所有 Controller

**替代方案对比**:
```kotlin
// 方案 A: 单个大 Service (❌ 复杂度高)
class FloatingWindowService : Service() {
    // 所有窗口的逻辑都在一个类中
    // 难以维护，难以测试
}

// 方案 B: Manager + Controller (✅ 推荐)
class FloatingWindowManager {
    private var joystickController: JoystickWindowController
    private var mapController: MapWindowController
    private var historyController: HistoryWindowController
}
```

---

### 决策 5: 为什么最低 SDK 设为 API 29？

**选择**: minSdk = 29 (Android 10)

**理由**:
1. ✅ **TestProvider 稳定性**: Android 10+ TestProvider 更稳定
2. ✅ **权限模型简化**: 不需要兼容旧版权限请求
3. ✅ **现代 API**: 可以使用 WindowInsetsController 等新 API
4. ✅ **市场占比**: Android 10+ 已覆盖 90%+ 设备

**权衡**:
- ⚠️ 失去 Android 9 及以下用户（占比 < 10%）
- ✅ 大幅简化代码，减少兼容性处理

---

## 性能优化

### 1. 内存管理

- ✅ **LeakCanary**: Debug 版本自动检测内存泄漏
- ✅ **协程作用域**: 正确使用 `viewModelScope` 和 `lifecycleScope`
- ✅ **资源清理**: Service 解绑时彻底清理引用

### 2. 启动优化

- ✅ **懒加载**: Fragment 和 ViewModel 按需创建
- ✅ **异步初始化**: 高德地图 SDK 异步初始化
- ✅ **地址缓存**: LRU 策略减少网络请求

### 3. 渲染优化

- ✅ **ViewBinding**: 类型安全，避免 findViewById
- ✅ **RecyclerView**: 列表复用，DiffUtil 增量更新
- ✅ **硬件加速**: 动画使用 GPU 加速

---

## 安全性

### 1. API Key 保护

- ✅ **local.properties**: Key 存储在本地文件，不提交到 Git
- ✅ **.gitignore**: 确保敏感信息不被泄露

### 2. 权限最小化

- ✅ **渐进式请求**: 仅在需要时请求权限
- ✅ **权限解释**: 向用户说明权限用途

### 3. 数据加密

- ⚠️ **Room 数据库**: 目前未加密（后续可集成 SQLCipher）
- ✅ **SharedPreferences**: 存储非敏感配置

---

## 测试策略

### 单元测试

- ✅ **ViewModel**: 不依赖 Android 框架，可直接测试
- ✅ **Repository**: 可以 mock 数据源
- ⚠️ **覆盖率**: 目标 70%+（待实现）

### UI 测试

- ⚠️ **Espresso**: 计划添加关键流程测试
- ⚠️ **截图测试**: 计划添加视觉回归测试

### 手动测试

- ✅ **真机测试**: 多品牌手机验证
- ✅ **场景覆盖**: 正常流程 + 异常流程

---

## 未来规划

### 短期 (1-2 个月)

- [ ] 修复悬浮窗地图搜索范围问题
- [ ] 添加单元测试覆盖核心逻辑
- [ ] Release 版本启用混淆

### 中期 (3-6 个月)

- [ ] 支持用户自定义主题色
- [ ] 添加路径规划模拟
- [ ] 集成 Firebase Analytics

### 长期 (6-12 个月)

- [ ] 多语言支持
- [ ] 云端同步收藏位置
- [ ] Widget 桌面组件

---

<div align="center">

**最后更新**: 2024-XX-XX

[📖 返回 README](README.md) · [📝 更新日志](CHANGELOG.md)

</div>
