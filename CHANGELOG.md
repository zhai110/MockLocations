# 更新日志 (Changelog)

所有重要的项目变更都将记录在此文件中。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，
版本遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

---

## [v1.6.0] - 2026-05-13

### Changed - 变更
- **架构重构：Delegate 模式拆分 MainFragment**：
  - MainFragment 从 1157 行精简至 735 行
  - 新增 5 个 Delegate：SearchDelegate、SimulationDelegate、RouteEditDelegate、ThemeDelegate、DialogDelegate
  - Delegate 间通过 ViewModel StateFlow 解耦，不直接引用
  - Delegate 不持有 AMap/MapView 引用，通过回调获取
- **新增 ServiceConnector 桥接层**：
  - LocationServiceConnector 使用 flatMapLatest 自动管理 Service 生命周期
  - ViewModel 不再直接依赖 LocationService 类型
  - 暴露 simulationState、routePlaybackState、sharedMapState 三个 StateFlow
- **新增 MapDelegate 地图逻辑复用**：
  - 消除 MainFragment 和 MapWindowController 之间的地图逻辑重复
  - 提供标记管理、相机移动、蓝点更新等通用方法
- **新增 AppResult 统一结果封装**：
  - 替代原有的 Result.kt，提供 Success/Error 密封类
  - 所有 Repository 统一使用 safeCall 包装

### Added - 新增
- **路线控制浮动窗**：RouteControlWindowController，支持路线播放控制
- **路线播放引擎**：RoutePlaybackEngine，支持多点路线自动循环播放
- **SavedRoute 数据层**：SavedRoute 实体 + SavedRouteDao + RouteRepository
- **状态同步链**：Service → Connector → ViewModel → Fragment/Delegate 完整数据流
- **相机移动节流**：路线模拟时 500ms 节流避免画面抖动
- **逆地理编码节流**：3 秒节流减少网络请求
- **单元测试**：MapUtilsTest、PrefsConfigTest、DataEntityTest

### Fixed - 修复
- **路线模拟 FAB 状态不同步**：FAB 在路线模式下与播放按钮状态同步（ic_fly/ic_position + 脉冲动画）
- **路线模拟 BottomSheet 位置不更新**：通过 sharedMapState 链路实时更新位置信息
- **路线模拟相机不跟踪**：shouldMoveToCurrentLocation 在路线播放时正确设置
- **Service 模拟状态不同步**：playRoute/pauseRoute/stopRoute 正确更新 _simulationState
- **FAB 状态冲突**：移除 updateMapUI 中直接操作 FAB 的代码，统一由 SimulationDelegate 管理
- **statusText 冲突**：移除 RouteEditDelegate 中的 statusText 设置，统一由 SimulationDelegate 管理

### Removed - 移除
- **清理未使用工具类**（921 行）：
  - SpringAnimationHelper.kt（弹簧动画，无引用）
  - OnboardingManager.kt（引导管理，无引用）
  - LoadingManager.kt（加载管理，无引用）
  - ProgressivePermissionManager.kt（渐进式权限，无引用）
  - NetworkLocationHelper.kt（网络定位，无引用）

### Technical Details - 技术细节
- **MainFragment**: 1157 → 735 行（-36%）
- **MainViewModel**: 440 → 946 行（+115%，状态同步链集中管理）
- **LocationService**: ~600 → 478 行（-20%）
- **MapWindowController**: ~900 → 815 行（-9%）
- **Kotlin 源文件**: 67 → 62 个（清理 5 个未使用文件）
- **新增核心文件**: LocationServiceConnector、MapDelegate、AppResult、RoutePlaybackEngine、RouteControlWindowController、4 个 Repository、5 个 Delegate
- **状态同步架构**: Service → Connector(flatMapLatest) → ViewModel(collect) → Fragment/Delegate

---

## [v1.5.1] - 2026-05-04

### Fixed - 修复
- **悬浮窗位置模拟不生效**：
  - 修复 MapWindowController 地图选点后位置模拟不生效问题
  - 修复 HistoryWindowController 历史记录选择后位置模拟不生效问题
  - 修复 setPositionWgs84 经纬度参数颠倒问题
- **更新对话框 UI 问题**：
  - 修复更新内容很长时进度条挤出按钮的问题
  - 进度条内嵌在更新按钮中，按钮始终可见
  - 用户可以随时看到下载进度并取消操作

### Changed - 变更
- **位置函数参数顺序统一**：
  - setPositionWgs84 参数顺序从 (lng, lat) 改为 (lat, lng)
  - 与 startSimulation、updateTargetLocation 等其他函数保持一致
  - 更新所有调用点，确保参数传递正确
- **代码简化**：
  - 删除未使用的 setPositionGcj02 函数，减少维护负担
  - 简化 UpdateDialogFragment 逻辑，移除按钮容器管理

### Added - 新增
- **坐标验证增强**：
  - performAutoMoveStep：验证计算出的新坐标是否有效
  - updatePlaybackPosition：验证转换后的 WGS-84 坐标
  - setLocation：三重验证（0.0、NaN、范围检查）
- **签名配置修复**：
  - 修复 RELEASE_STORE_FILE 路径配置（app/release.jks → release.jks）
  - 清理 local.properties 中的重复配置

### Technical Details - 技术细节
- **versionCode**：7 → 8
- **versionName**：1.5.0 → 1.5.1
- **APK 大小**：69.07 MB (72,423,137 bytes)
- **关键修复**：坐标参数顺序统一、三重坐标验证
- **内存安全**：无内存泄漏风险，所有资源清理完善

---

## [v1.5.0] - 2026-05-04

### Added - 新增
- **路线模拟功能**：
  - 支持多点路线规划与自动循环播放
  - 实时显示路线播放进度
  - 支持暂停/继续路线播放

### Fixed - 修复
- **路线模拟起点跳跃问题**：
  - 修复循环播放时起点跳跃的严重 Bug
  - 优化路线播放的位置平滑过渡

### Optimized - 优化
- **路线模拟体验**：
  - 优化路线模拟时相机跟随逻辑
  - 异步化数据库清理，提升流畅度

### Technical Details - 技术细节
- **versionCode**：6 → 7
- **versionName**：1.4.1 → 1.5.0
- **新功能**：路线模拟与循环播放
- **关键修复**：起点跳跃问题

---

## [v1.4.1] - 2026-04-30

### Fixed - 修复
- **升级数据丢失问题**：
  - 移除 DEBUG 模式的 `fallbackToDestructiveMigration()`，防止迁移失败时删除用户数据
  - 修复 MIGRATION_2_3 逻辑：保留最新的搜索记录（MAX timestamp）而非最早的（MIN id）
  - 添加迁移异常处理和日志记录，便于排查问题
- **数据库索引命名规范**：
  - 统一索引命名为 Room 标准格式：`index_{tableName}_{columns}`
  - 新增 MIGRATION_3_4：重命名所有自定义索引为 Room 标准命名
  - 修复 schema 验证不通过导致的潜在问题
- **升级兼容性**：
  - 确保从 1.3.0 → 1.4.1 升级时历史记录、收藏、搜索记录完整保留
  - 解决 DEBUG 模式下测试升级导致数据清空的问题

### Technical Details - 技术细节
- **数据库版本**：3 → 4
- **versionCode**：5 → 6
- **versionName**：1.4.0 → 1.4.1
- **新增迁移**：MIGRATION_3_4（索引重命名）
- **优化迁移**：MIGRATION_2_3（保留最新记录）

---

## [v1.4.0] - 2026-04-29

### Added - 新增
- **双模式交互设计**：
  - App 内历史记录/收藏点击后返回主界面，红点移动但不立即模拟
  - FAB 确认后才开始模拟，蓝点跟随移动（两步确认）
  - 悬浮窗历史记录点击直接生效，快速切换位置（一步直达）
- **坐标系支持说明**：在关于页面明确标注支持 GCJ-02/WGS-84/BD-09

### Changed - 变更
- **MainViewModel 事件通信**：MutableStateFlow → MutableSharedFlow，避免一次性事件丢失
- **selectPosition 方法**：新增 clearAddress 参数，支持从历史/收藏返回时保留地址信息
- **launcher 回调优化**：pendingPositionFromResult 标志，避免 onResume 覆盖用户新选择的位置
- **更新日志**：update.json 详细描述 v1.4.0 所有改进

### Fixed - 修复
- **坐标转换逻辑**：
  - 修复 startSimulation 和 teleportToPosition 的 EXTRA_COORD_GCJ02 标志设置
  - 确保所有外部传入的 GCJ-02 坐标正确转换为 WGS-84 注入系统
- **悬浮窗历史记录坐标系**：FloatingWindowManager 中 GCJ-02 → WGS-84 转换
- **连续选点偏差问题**：修复第二次及以后选点传送时红点和蓝点不重合的问题
- **FavoriteLocationDao 浮点精度**：使用 ROUND(latitude, 6) 与 HistoryLocationDao 保持一致
- **AddressCache 线程安全**：HashMap → ConcurrentHashMap，去除可空类型
- **AddressCache 协程泄漏**：新增 destroy() 方法，在 Application.onTerminate() 中调用
- **UpdateDialogFragment 生命周期**：独立 CoroutineScope → lifecycleScope，自动绑定生命周期
- **LocationService 日志管理**：uprootAll() → 只移除 DebugTree，保留其他 Tree（如 LeakCanary）
- **BootReceiver 硬编码**：SP 键名改用 PrefsConfig.Settings.KEY_* 常量
- **VirtualLocationApp database**：添加 @Volatile 注解保证多线程可见性

### Technical Details - 技术细节
- **数据库版本**：保持 v3，无结构变更
- **versionCode**：4 → 5
- **versionName**：1.3.0 → 1.4.0
- **编译 SDK**：36 (Android 15)
- **目标 SDK**：36 (Android 15)
- **最低 SDK**：29 (Android 10)

---

## [Unreleased]

### Added - 新增
- MVVM 架构重构：MainActivity 拆分为 MainActivity + MainFragment + MainViewModel
- StateFlow 状态管理：mapState, searchState, simulationState, bottomSheetState
- 夜间模式完美适配：手动更新所有 UI 元素背景色
- 悬浮窗 Manager + Controller 架构：Joystick/Map/History 三个窗口控制器
- 渐进式权限请求系统：ProgressivePermissionManager
- 动画工具库：AnimationHelper, AdvancedAnimationHelper, SpringAnimationHelper
- LeakCanary 内存泄漏检测（Debug 版本）
- 地址缓存系统：LRU 策略优化性能
- 新手引导系统：OnboardingManager
- GPL-3.0 开源协议文件
- **数据库索引优化**：为 history_location、favorite_location、search_history 添加时间戳和关键词索引

### Changed - 变更
- 底部导航栏选中态颜色：蓝色 → 蓝绿色（与应用主色调一致）
- 底部导航栏激活指示器：使用 primary_container 浅色背景
- 项目包名：com.virtuallocation.app → com.mockloc
- 高德地图 SDK 版本：升级到 11.1.0
- Material Design 3 完整实现：MD3 色彩系统
- 日志系统统一使用 Timber

### Fixed - 修复
- 夜间模式切换闪退问题：添加 configChanges="uiMode"
- 夜间模式 UI 颜色不更新：手动刷新所有 View 背景色
- **底部导航栏选中指示器日夜模式不切换**：动态设置颜色，绕过 configChanges 资源不刷新问题
- **设置页面 AppBar 背景色硬编码**：改为引用 @color/app_bar_background 资源
- **LocationService 内存泄漏**：HandlerThread 使用 quitSafely()，ExecutorService 优雅关闭
- **服务生命周期问题**：START_NOT_STICKY + onTaskRemoved 彻底清理，用户可以真正退出应用
- **数据库迁移策略不完善**：移除 fallbackToDestructiveMigration()，添加异常处理和降级方案
- 悬浮窗主题切换时自动弹出：记录 wasShowing 状态
- 搜索结果点击后地图不跳转：根据 shouldMoveCamera 标志移动相机
- FloatingHistoryAdapter Timber 调用错误：修正 import
- 协程内存泄漏：正确使用 viewModelScope 和 lifecycleScope
- 死代码清理：移除未使用的变量和方法

### Removed - 移除
- 空的 manager 目录
- 未使用的颜色定义（info, legacy colors）
- onDestroy() 中的冗余日志
- onConfigurationChanged() 中的无效逻辑

### Optimized - 优化
- MainActivity 精简：从 ~800 行减少到 93 行
- Service 绑定逻辑优化：简化 onStop() 清理代码
- 搜索功能：修复相机移动逻辑
- 内存管理：正确的协程取消和资源清理

---

## [1.0.0] - 2024-XX-XX

### Added
- 初始版本发布
- TestProvider 虚拟定位机制
- 高德地图集成
- 摇杆控制系统
- 悬浮窗功能
- 历史记录和收藏功能
- Material Design 3 UI
- Room 数据库
- MVVM 架构基础

---

## 版本说明

### [Unreleased] 中的分类说明

- **Added**: 新增功能
- **Changed**: 现有功能的变更
- **Deprecated**: 即将废弃的功能
- **Removed**: 已移除的功能
- **Fixed**: Bug 修复
- **Security**: 安全相关修复
- **Optimized**: 性能优化

### 提交规范

每次提交 PR 或 Release 时，请在此文件中添加相应的变更记录。

格式示例：
```markdown
### Added
- 添加了 XXX 功能 (#123)

### Fixed
- 修复了 YYY 问题 (#456)
```

---

<div align="center">

**最后更新**: 2026-05-13

</div>
