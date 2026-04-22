# 更新日志 (Changelog)

所有重要的项目变更都将记录在此文件中。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，
版本遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

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

**最后更新**: 2026-04-16

</div>
