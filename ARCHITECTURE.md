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

项目采用 **分层架构 + MVVM 模式**，包含 Delegate 层和 ServiceConnector 桥接层：

```
┌─────────────────────────────────────────────────────┐
│                 Presentation Layer                   │
│  ┌──────────┐  ┌──────────────────────────────────┐│
│  │ Activity │  │ Fragment + Delegates              ││
│  │          │  │  ┌──────────┐ ┌───────────────┐  ││
│  │          │  │  │SearchDel.│ │SimulationDel. │  ││
│  │          │  │  ├──────────┤ ├───────────────┤  ││
│  │          │  │  │RouteEdit │ │ ThemeDelegate │  ││
│  │          │  │  ├──────────┤ ├───────────────┤  ││
│  │          │  │  │DialogDel.│ │               │  ││
│  │          │  │  └──────────┘ └───────────────┘  ││
│  └──────────┘  └──────────────────────────────────┘│
└──────────────────────┬──────────────────────────────┘
                       │ StateFlow 观察
┌──────────────────────▼──────────────────────────────┐
│                  ViewModel Layer                     │
│  ┌──────────────────────────────────────────────┐  │
│  │           MainViewModel                      │  │
│  │  - StateFlow 状态管理                        │  │
│  │  - 业务逻辑处理                              │  │
│  │  - 数据转换                                  │  │
│  └──────────────────────────────────────────────┘  │
└──────────────────────┬──────────────────────────────┘
                       │ ServiceConnector 桥接
┌──────────────────────▼──────────────────────────────┐
│               Core Layer (ServiceConnector)          │
│  ┌──────────────────────────────────────────────┐  │
│  │       LocationServiceConnector               │  │
│  │  - flatMapLatest 自动管理 Service 生命周期   │  │
│  │  - 暴露 simulationState / routePlaybackState │  │
│  │  - 暴露 sharedMapState                       │  │
│  └──────────────────────────────────────────────┘  │
└──────────────────────┬──────────────────────────────┘
                       │ StateFlow / SharedFlow
┌──────────────────────▼──────────────────────────────┐
│             Service + Data Layer                     │
│  ┌────────────────┐  ┌──────────────────────────┐  │
│  │ LocationService│  │ FloatingWindowManager    │  │
│  │ RoutePlayback  │  │ RouteControlWindowCtrl   │  │
│  │ Engine         │  │                          │  │
│  └────────────────┘  └──────────────────────────┘  │
│  ┌──────────┐  ┌──────────────┐  ┌──────────────┐ │
│  │  Room    │  │LocationRepo  │  │  MapDelegate │ │
│  └──────────┘  └──────────────┘  └──────────────┘ │
└─────────────────────────────────────────────────────┘
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
                updateMapUI(state)
            }
        }
    }
}
```

---

### 2. Delegate 模式

#### 应用场景：Fragment 功能拆分

**问题**: MainFragment 职责过重，代码行数膨胀至 1157 行，难以维护和测试

**解决方案**: 将 Fragment 按功能领域拆分为独立 Delegate，每个 Delegate 职责单一

```
MainFragment (735行)
├── SearchDelegate (223行)      # 搜索功能
├── SimulationDelegate (297行)  # 模拟控制（单点+路线）
├── RouteEditDelegate (301行)   # 路线编辑
├── ThemeDelegate (321行)       # 主题切换
└── DialogDelegate (72行)       # 对话框（坐标输入、速度选择）
```

#### 核心规则

1. **Delegate 间不直接引用**：通过 ViewModel StateFlow 中转通信，避免耦合
2. **Delegate 不持有 AMap/MapView 引用**：通过回调获取（`onGetAMap` / `onGetSearchCenter`），避免地图对象生命周期问题
3. **Delegate 初始化必须在 `initMap()` 之前**：确保回调可用时 Delegate 已就绪

#### 实现示例

```kotlin
// Delegate 基本结构
class SearchDelegate(
    private val viewModel: MainViewModel,
    private val onGetAMap: () -> AMap?,
    private val onGetSearchCenter: () -> LatLng?
) {
    fun onMapReady() { /* 搜索相关地图初始化 */ }
    fun performSearch(query: String) { /* 执行搜索 */ }
}

// MainFragment 中初始化
class MainFragment : Fragment() {
    private val searchDelegate = SearchDelegate(
        viewModel = viewModel,
        onGetAMap = { aMap },
        onGetSearchCenter = { searchCenter }
    )

    override fun onViewCreated(...) {
        // Delegate 初始化在 initMap() 之前
        searchDelegate.onMapReady()
        initMap()
    }
}
```

**优势**:
- ✅ 代码量降低：MainFragment 从 1157 行降至 735 行
- ✅ 职责单一：每个 Delegate 可独立理解和修改
- ✅ 可独立测试：Delegate 不依赖 Fragment 生命周期
- ✅ 解耦通信：Delegate 间通过 ViewModel StateFlow 解耦

---

### 3. ServiceConnector 桥接模式

#### 应用场景：Service ↔ ViewModel 通信

**问题**: ViewModel 直接依赖 Service 类型，Service 绑定/解绑生命周期管理复杂

**解决方案**: 引入 LocationServiceConnector 桥接层，使用 `flatMapLatest` 自动管理 Service 生命周期

```
LocationService (Service端)
    ↓ StateFlow/SharedFlow
LocationServiceConnector (桥接层, flatMapLatest)
    ↓ StateFlow
MainViewModel (ViewModel端)
    ↓ StateFlow
MainFragment + Delegates (UI端)
```

#### 核心机制

- **flatMapLatest 自动管理**: Service 绑定时自动开始收集状态，解绑时自动停止
- **暴露三个核心状态**: `simulationState`、`routePlaybackState`、`sharedMapState`
- **ViewModel init 中 collect**: 在 ViewModel 初始化时同步 Connector 状态到本地 StateFlow

#### 实现示例

```kotlin
// Connector: 桥接层
class LocationServiceConnector(
    private val serviceFlow: StateFlow<LocationService?>
) {
    val simulationState: StateFlow<SimulationState> = serviceFlow
        .flatMapLatest { service ->
            service?.simulationState ?: flowOf(SimulationState.IDLE)
        }
        .stateIn(scope, SharingStarted.Eagerly, SimulationState.IDLE)

    val routePlaybackState: StateFlow<RoutePlaybackState> = serviceFlow
        .flatMapLatest { service ->
            service?.routePlaybackState ?: flowOf(RoutePlaybackState.Idle)
        }
        .stateIn(scope, SharingStarted.Eagerly, RoutePlaybackState.Idle)

    val sharedMapState: StateFlow<SharedMapState> = serviceFlow
        .flatMapLatest { service ->
            service?.sharedMapState ?: flowOf(SharedMapState())
        }
        .stateIn(scope, SharingStarted.Eagerly, SharedMapState())
}

// ViewModel: 收集 Connector 状态
class MainViewModel : ViewModel() {
    init {
        viewModelScope.launch {
            connector.simulationState.collect { state ->
                _simulationState.update { state }
            }
        }
        viewModelScope.launch {
            connector.routePlaybackState.collect { state ->
                _routeState.update { state }
            }
        }
    }
}
```

**优势**:
- ✅ ViewModel 不直接依赖 Service 类型
- ✅ flatMapLatest 自动处理 Service 绑定/解绑
- ✅ Service 端 StateFlow 变化自动传播到 ViewModel

---

### 4. Manager + Controller 模式

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
    private var routeControlController: RouteControlWindowController? = null

    fun switchToMap() {
        hide()
        currentWindowType = WINDOW_TYPE_MAP
        show()
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

### 5. MapDelegate 地图逻辑复用

#### 应用场景：消除地图逻辑重复

**问题**: MainFragment 和 MapWindowController 之间存在大量地图逻辑重复（标记管理、相机移动、蓝点更新等）

**解决方案**: 提取通用地图操作到 `MapDelegate` 工具类

```kotlin
// MapDelegate: 通用地图操作
object MapDelegate {
    fun addMarker(aMap: AMap, position: LatLng): Marker { /* 添加标记 */ }
    fun moveCamera(aMap: AMap, position: LatLng, zoom: Float) { /* 移动相机 */ }
    fun updateBlueDot(aMap: AMap, position: LatLng) { /* 更新蓝点 */ }
    fun clearMarkers(aMap: AMap) { /* 清除标记 */ }
}
```

**优势**:
- ✅ 消除重复：MainFragment 和 MapWindowController 共享同一套地图逻辑
- ✅ 一致性：确保两端行为完全一致
- ✅ 可维护性：修改地图逻辑只需改一处

---

### 6. Repository 模式

#### 应用场景：数据访问

```kotlin
// Repository: 数据访问抽象
class LocationRepository(
    private val historyDao: HistoryLocationDao,
    private val favoriteDao: FavoriteLocationDao
) {
    suspend fun saveToHistory(name: String, address: String, latitude: Double, longitude: Double): AppResult<Int> {
        return safeCall {
            // 去重逻辑 + 清理过期记录
            historyDao.insert(historyLocation)
            historyDao.keepRecentRecords(100)
            historyDao.getAll().size
        }
    }

    suspend fun addToFavorite(name: String, address: String, latitude: Double, longitude: Double): AppResult<Boolean> {
        return safeCall {
            val exists = favoriteDao.exists(latitude, longitude)
            if (!exists) { favoriteDao.insert(favorite) }
            !exists
        }
    }
}

// ViewModel: 使用 Repository
class MainViewModel : ViewModel() {
    private val repository: LocationRepository

    fun saveCurrentPosition() {
        viewModelScope.launch {
            repository.saveToHistory(name, address, lat, lng)
        }
    }
}
```

**优势**:
- ✅ 解耦：ViewModel 不直接依赖 DAO
- ✅ 可测试：可以 mock Repository
- ✅ 灵活性：可以轻松切换数据源
- ✅ 统一错误处理：通过 AppResult 封装

---

## 模块职责

### Core Layer (core/)

| 模块 | 职责 | 关键文件 |
|------|------|---------|
| **core/service** | Service↔ViewModel 桥接 | LocationServiceConnector |
| **core/common** | 通用类型封装 | AppResult |
| **core/utils** | 跨模块工具 | MapDelegate |

**原则**:
- ✅ 不依赖 UI 层
- ✅ 不依赖具体 Service 实现（通过 StateFlow 抽象）
- ✅ 可被多个模块复用

---

### UI Layer (ui/)

| 模块 | 职责 | 关键文件 |
|------|------|---------|
| **main** | 主界面 UI | MainActivity (81行), MainFragment (735行) |
| **delegate** | 功能模块拆分 | SearchDelegate, SimulationDelegate, RouteEditDelegate, ThemeDelegate, DialogDelegate |
| **history** | 历史记录列表 | HistoryActivity, HistoryAdapter |
| **favorite** | 收藏位置管理 | FavoriteActivity |
| **settings** | 应用设置 | SettingsActivity |
| **search** | 搜索结果展示 | SearchResultAdapter |

**原则**:
- ❌ 不包含业务逻辑
- ✅ 只负责 UI 展示和用户交互
- ✅ 观察 ViewModel 的 StateFlow
- ✅ Delegate 间通过 ViewModel StateFlow 通信

---

### ViewModel Layer (ui/main/MainViewModel.kt)

**职责**:
- ✅ 管理所有 UI 状态（StateFlow）
- ✅ 处理业务逻辑（位置传送、搜索）
- ✅ 数据转换（坐标转换、格式化）
- ✅ 通过 ServiceConnector 与 Service 交互

**不包含**:
- ❌ UI 引用（View、Context）
- ❌ Android 框架类（除了 Application）
- ❌ 直接依赖 Service 类型

---

### Service Layer (service/)

| 服务 | 职责 |
|------|------|
| **LocationService** | TestProvider 模拟定位，摇杆移动控制，路线播放调度 |
| **RoutePlaybackEngine** | 路线播放引擎，计算插值位置，管理播放状态 |
| **FloatingWindowManager** | 悬浮窗管理，窗口切换，主题同步 |
| **RouteControlWindowController** | 路线控制悬浮窗，播放/暂停/停止控制 |

**特点**:
- ✅ 前台服务，保持后台运行
- ✅ StateFlow/SharedFlow 通信，向 ViewModel 暴露状态
- ✅ 生命周期独立于 UI

---

### Data Layer (data/)

**Room 数据库**:
- `AppDatabase`: 数据库实例
- `HistoryLocation`: 历史位置实体
- `FavoriteLocation`: 收藏位置实体
- DAO: 数据访问对象

**Repository**:
- `LocationRepository`: 统一管理历史记录和收藏位置的数据访问
- `FavoriteRepository`: 收藏位置专用仓库

**特点**:
- ✅ 编译时 SQL 检查
- ✅ 协程支持（suspend 函数）
- ✅ 迁移支持（版本升级）
- ✅ AppResult 统一错误处理

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
SimulationDelegate.onFabClick()
    ↓
viewModel.startSimulation()
    ↓
LocationServiceConnector → LocationService.startSimulation()
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

### 3. 路线模拟播放数据流

```
RoutePlaybackEngine.state (播放状态)
    ↓ SharedFlow
LocationService._routePlaybackState
    ↓ StateFlow
LocationServiceConnector.routePlaybackState (flatMapLatest)
    ↓ StateFlow
MainViewModel collect → _routeState.update
    ↓ StateFlow
RouteEditDelegate.updatePlaybackUI()
SimulationDelegate.updateSimulationUI()
```

---

### 4. 路线位置实时更新数据流

```
RoutePlaybackEngine 位置更新
    ↓
LocationService.updatePlaybackPosition() → _sharedMapState.update
    ↓ StateFlow
LocationServiceConnector.sharedMapState
    ↓ StateFlow
MainViewModel collect → _mapState.update(currentLocation, shouldMoveToCurrentLocation=true)
    ↓ StateFlow
MainFragment.updateMapUI() → animateCamera (500ms节流)
MainFragment.updateLocationInfo() → 逆地理编码 (3s节流)
```

---

### 5. 夜间模式切换

```
用户切换系统主题
    ↓
Android 发送配置变化事件
    ↓
MainActivity.onConfigurationChanged() (configChanges 阻止重建)
    ↓
ThemeDelegate.onConfigurationChanged()
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

### 决策 2: 为什么使用 Delegate 模式拆分 Fragment？

**选择**: 将 MainFragment 拆分为 5 个 Delegate

**理由**:
1. ✅ **代码量降低**: MainFragment 从 1157 行降至 735 行
2. ✅ **职责单一**: 每个 Delegate 只负责一个功能领域，可独立理解和修改
3. ✅ **可独立测试**: Delegate 不依赖 Fragment 生命周期，可单独测试
4. ✅ **解耦通信**: Delegate 间通过 ViewModel StateFlow 中转，不直接引用

**替代方案对比**:
```kotlin
// 方案 A: 单个大 Fragment (❌ 复杂度高)
class MainFragment : Fragment() {
    // 搜索逻辑 + 模拟控制 + 路线编辑 + 主题切换 + 对话框
    // 所有代码混在一起，难以维护
}

// 方案 B: Delegate 拆分 (✅ 推荐)
class MainFragment : Fragment() {
    private val searchDelegate = SearchDelegate(...)
    private val simulationDelegate = SimulationDelegate(...)
    private val routeEditDelegate = RouteEditDelegate(...)
    private val themeDelegate = ThemeDelegate(...)
    private val dialogDelegate = DialogDelegate(...)
}
```

---

### 决策 3: 为什么引入 ServiceConnector 桥接层？

**选择**: LocationServiceConnector 作为 Service ↔ ViewModel 桥接

**理由**:
1. ✅ **解耦**: ViewModel 不直接依赖 Service 类型，只依赖 StateFlow
2. ✅ **自动生命周期管理**: `flatMapLatest` 自动处理 Service 绑定/解绑时的状态切换
3. ✅ **自动传播**: Service 端 StateFlow 变化自动传播到 ViewModel，无需手动监听
4. ✅ **默认值处理**: Service 未绑定时自动提供默认状态（IDLE / Idle）

**替代方案对比**:
```kotlin
// 方案 A: ViewModel 直接持有 Service 引用 (❌ 耦合高)
class MainViewModel : ViewModel() {
    private var locationService: LocationService? = null
    // 需要手动管理绑定/解绑
    // Service 断开时需要手动清理状态
}

// 方案 B: ServiceConnector 桥接 (✅ 推荐)
class MainViewModel : ViewModel() {
    private val connector = LocationServiceConnector(serviceFlow)
    // flatMapLatest 自动管理生命周期
    // Service 断开时自动回退到默认状态
}
```

---

### 决策 4: 为什么 MainActivity 这么精简？

**选择**: MainActivity 仅 81 行

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

// 现代做法：Activity 作为容器 (81 行)
class MainActivity : AppCompatActivity() {
    // 只负责 Service 绑定
    // Fragment 管理
}
```

---

### 决策 5: 为什么使用 configChanges 处理夜间模式？

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
    themeDelegate.onConfigurationChanged(newConfig)
}
```

---

### 决策 6: 为什么悬浮窗使用 Manager + Controller？

**选择**: FloatingWindowManager + 多个 Controller

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
    private var routeControlController: RouteControlWindowController
}
```

---

### 决策 7: 为什么最低 SDK 设为 API 29？

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
- ✅ **Delegate**: 不依赖 Fragment 生命周期，可独立测试
- ⚠️ **覆盖率**: 目标 70%+（待实现）

### UI 测试

- ⚠️ **Espresso**: 计划添加关键流程测试
- ⚠️ **截图测试**: 计划添加视觉回归测试

### 手动测试

- ✅ **真机测试**: 多品牌手机验证
- ✅ **场景覆盖**: 正常流程 + 异常流程

---

## 版本历史

### v1.6.2 (2026-05-13)

**Bug 修复**
- 修复 RoutePlaybackEngine 状态竞争风险
- 路线播放启动瞬间的状态不一致问题

**技术改进**
- RoutePlaybackState 新增 isStarting 过渡状态
- play() 先设置 isStarting=true，第一次位置更新后才设置 isPlaying=true
- LocationService 同步时忽略 isStarting，只关注 isPlaying
- 消除竞争窗口，增强状态机健壮性

### v1.6.1 (2026-05-13)

**Bug 修复**
- 修复从旧版本升级后仍弹出更新提示的问题
- UpdateChecker 缓存机制增加版本验证逻辑
- 添加 APK MD5 完整性校验，增强安全性

**技术改进**
- 优化缓存管理：清除过期缓存后继续网络检查
- 新增 `calculateMD5()` 方法计算文件哈希
- 异常处理更健壮，避免崩溃风险

### v1.6.0 (2026-05-13)

**架构重构**
- Delegate 模式拆分 MainFragment（1157→735行，-36%）
- ServiceConnector 桥接层实现 Service↔ViewModel 解耦
- 更新对话框布局优化：ConstraintLayout 固定底部按钮

**功能增强**
- 两步确认模式（App内）/ 一步直达（悬浮窗）
- 路线模拟与循环播放功能
- 智能搜索范围自动调整（20km→50km）

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

**最后更新**: 2026-05-13

[📖 返回 README](README.md) · [📝 更新日志](CHANGELOG.md)

</div>
