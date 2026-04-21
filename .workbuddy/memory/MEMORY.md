# Mock Location 项目记忆

## 项目概况
- **包名**: com.mockloc
- **技术栈**: Kotlin, AGP 8.7.3, Room, 高德SDK, Material3
- **minSdk**: 29, targetSdk: 34
- **整体实现度**: ~85% (2026-04-17评估，上次55%)

## 当前问题清单 (2026-04-17 更新)
| 优先级 | 问题 | 文件 |
|---|---|---|
| **P0** | 高德SDK用`latest.integration`，构建不可复现 | build.gradle.kts:109 |
| **P1** | JoystickView.moveToPosition硬编码auto=true，自由模式滑动手柄也触发自动移动 | JoystickView.kt:228 |
| **P1** | showToastWithIcon使用废弃Toast.view API (Android 12+) | UIFeedbackHelper.kt:119 |
| **P1** | FavoriteActivity返回坐标未标记GCJ02 | FavoriteActivity.kt:47-51 |
| **P2** | BootReceiver用Float保存经纬度，精度不足 | BootReceiver.kt:38-40 |
| **P2** | LocationService.move()死代码 | LocationService.kt:500 |
| **P2** | FloatingWindowManager.refreshHistory()创建未绑定CoroutineScope | FloatingWindowManager.kt:1430 |
| **P2** | 动画工具类冗余(4个) + AppConstants未引用 | util/ |
| **P3** | shareLocation()无UI入口 | MainActivity.kt:1032 |

## 核心架构 (2026-04-17 更新)

### 功能链路
**地图选点 → 启动LocationService → TestProvider注入 → 悬浮窗交互 → 摇杆移动** ✅ 全链路贯通

### 悬浮窗系统
- **FloatingWindowManager.kt** (1759行): 三窗口(JOYSTICK/MAP/HISTORY)管理
  - 共享LayoutParams，切换窗口位置保持
  - DragLinearLayout/DragFrameLayout + dragExcludeView排除区域
  - 搜索框焦点动态切换(FLAG_NOT_FOCUSABLE管理)
  - 深色模式: ContextThemeWrapper + syncMapWithSystemTheme()重建
- **JoystickView.kt** (255行): 圆形摇杆(锁定/自由模式)
- **ButtonView.kt** (170行): 八方向按钮摇杆
- **LocationService.kt** (687行): 前台服务
  - processDirection()对接摇杆回调
  - CountDownTimer(1秒间隔)自动移动
  - SP监听器即时生效(altitude/speed/joystick_type/logging/history_expiry)

### 已完成的修复 (2026-04-14~17)
- ✅ 摇杆回调连接到移动逻辑
- ✅ 悬浮地图窗实现(MapView + POI搜索 + 标记 + 确定按钮)
- ✅ 悬浮历史窗实现(Room数据库 + 搜索)
- ✅ 搜索框焦点动态切换
- ✅ 设置值实时传递(SP监听器)
- ✅ logging/history_expiry设置生效
- ✅ 深色模式完整支持
- ✅ History/Favorite返回结果MainActivity已处理
- ✅ 拖动与操控不冲突(dragExcludeView)
