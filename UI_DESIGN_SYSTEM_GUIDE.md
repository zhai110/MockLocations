# UI/UX 设计系统完整指南

## 📋 概述

本项目已建立完整的Material Design 3设计系统，包括：
1. ✅ **图标系统** - 统一的Vector Drawable图标
2. ✅ **Typography系统** - 完整的字体排版规范
3. ✅ **Spacing系统** - 基于4dp网格的间距规范

---

## 🎨 1. 图标系统 (Icon System)

### 现状
所有图标均已使用**Vector Drawable**格式，位于 `res/drawable/` 目录。

### 图标清单 (共56个)

#### 功能图标
- `ic_add` - 添加
- `ic_close` - 关闭
- `ic_delete` - 删除
- `ic_edit_location` - 编辑位置
- `ic_search` - 搜索
- `ic_settings` - 设置
- `ic_share` - 分享
- `ic_copy` - 复制
- `ic_favorite` - 收藏
- `ic_history` - 历史

#### 导航图标
- `ic_back` - 返回
- `ic_chevron_right` - 右箭头
- `ic_explore` - 探索
- `ic_layers` - 图层
- `ic_map` - 地图
- `ic_place` - 地点
- `ic_position` - 位置
- `ic_my_location` - 我的位置
- `ic_home_position` - 首页位置

#### 交通方式图标
- `ic_walk` / `ic_directions_walk` - 步行
- `ic_run` / `ic_directions_run` - 跑步
- `ic_bike` / `ic_directions_bike` - 骑行
- `ic_car` - 驾车

#### 摇杆方向图标
- `ic_direction_up` - 上
- `ic_direction_down` - 下
- `ic_direction_left` - 左
- `ic_direction_right` - 右
- `ic_direction_left_up` - 左上
- `ic_direction_left_down` - 左下
- `ic_direction_right_up` - 右上
- `ic_direction_right_down` - 右下

#### 状态图标
- `ic_play` - 播放/开始
- `ic_stop` - 停止
- `ic_lock_open` - 解锁
- `ic_lock_close` - 锁定
- `ic_gamepad` - 游戏手柄/摇杆
- `ic_mic` - 麦克风

#### 标记图标
- `ic_marker_dot` - 标记点
- `ic_marker_location` - 位置标记

#### 其他图标
- `ic_move` - 移动
- `ic_remove` - 移除

### 图标使用规范

```xml
<!-- 标准用法 -->
<ImageButton
    android:layout_width="@dimen/icon_size_medium"
    android:layout_height="@dimen/icon_size_medium"
    android:src="@drawable/ic_search"
    android:contentDescription="@string/search" />

<!-- 带颜色 tint -->
<ImageButton
    android:layout_width="24dp"
    android:layout_height="24dp"
    android:src="@drawable/ic_play"
    app:tint="@color/primary" />
```

### 图标尺寸规范

| 尺寸 | 值 | 用途 |
|------|-----|------|
| Tiny | 16dp | 极小图标 |
| Small | 20dp | 小图标 |
| Medium | 24dp | **标准图标（默认）** |
| Large | 32dp | 大图标 |
| XLarge | 40dp | 特大图标 |
| Huge | 48dp | 巨大图标 |

---

## 📝 2. Typography 排版系统

### 设计理念
基于**Material Design 3 Type Scale**，定义了完整的字体层级系统。

### 字体层级 (Type Scale)

#### Display Styles (展示级 - 超大标题)
```xml
<!-- Display Large - 57sp -->
<TextView
    style="@style/TextAppearance.VirtualLocation.DisplayLarge"
    android:text="欢迎使用" />

<!-- Display Medium - 45sp -->
<TextView
    style="@style/TextAppearance.VirtualLocation.DisplayMedium"
    android:text="虚拟定位" />

<!-- Display Small - 36sp -->
<TextView
    style="@style/TextAppearance.VirtualLocation.DisplaySmall"
    android:text="专业版" />
```

#### Headline Styles (标题级)
```xml
<!-- Headline Large - 32sp -->
<TextView
    style="@style/TextAppearance.VirtualLocation.HeadlineLarge"
    android:text="页面标题" />

<!-- Headline Medium - 28sp -->
<TextView
    style="@style/TextAppearance.VirtualLocation.HeadlineMedium"
    android:text="章节标题" />

<!-- Headline Small - 24sp -->
<TextView
    style="@style/TextAppearance.VirtualLocation.HeadlineSmall"
    android:text="区块标题" />
```

#### Title Styles (副标题级)
```xml
<!-- Title Large - 22sp, Bold -->
<TextView
    style="@style/TextAppearance.VirtualLocation.TitleLarge"
    android:text="卡片标题" />

<!-- Title Medium - 16sp, Medium -->
<TextView
    style="@style/TextAppearance.VirtualLocation.TitleMedium"
    android:text="列表项标题" />

<!-- Title Small - 14sp, Medium -->
<TextView
    style="@style/TextAppearance.VirtualLocation.TitleSmall"
    android:text="辅助标题" />
```

#### Body Styles (正文级)
```xml
<!-- Body Large - 16sp -->
<TextView
    style="@style/TextAppearance.VirtualLocation.BodyLarge"
    android:text="主要正文内容" />

<!-- Body Medium - 14sp -->
<TextView
    style="@style/TextAppearance.VirtualLocation.BodyMedium"
    android:text="次要正文内容" />

<!-- Body Small - 12sp -->
<TextView
    style="@style/TextAppearance.VirtualLocation.BodySmall"
    android:text="注释文字" />
```

#### Label Styles (标签级 - 按钮/表单)
```xml
<!-- Label Large - 14sp, Medium -->
<TextView
    style="@style/TextAppearance.VirtualLocation.LabelLarge"
    android:text="按钮文字" />

<!-- Label Medium - 12sp, Medium -->
<TextView
    style="@style/TextAppearance.VirtualLocation.LabelMedium"
    android:text="标签" />

<!-- Label Small - 11sp, Medium -->
<TextView
    style="@style/TextAppearance.VirtualLocation.LabelSmall"
    android:text="微型标签" />
```

### 语义化别名 (Semantic Aliases)

为了更易用，提供了语义化的样式别名：

```xml
<!-- 页面标题 -->
<TextView
    style="@style/TextAppearance.VirtualLocation.PageTitle"
    android:text="主界面" />

<!-- 章节标题 -->
<TextView
    style="@style/TextAppearance.VirtualLocation.SectionTitle"
    android:text="设置选项" />

<!-- 卡片标题 -->
<TextView
    style="@style/TextAppearance.VirtualLocation.CardTitle"
    android:text="当前位置" />

<!-- 按钮文字 -->
<TextView
    style="@style/TextAppearance.VirtualLocation.ButtonText"
    android:text="确定" />

<!-- 说明文字 -->
<TextView
    style="@style/TextAppearance.VirtualLocation.Caption"
    android:text="最后更新: 2分钟前" />

<!-- 坐标数值（等宽字体） -->
<TextView
    style="@style/TextAppearance.VirtualLocation.CoordinateValue"
    android:text="39.9042° N" />
```

### Typography关键特性

#### 1. 字重规范
- **Bold**: Display、Headline、Title Large
- **Medium**: Title Medium/Small、Label系列
- **Regular**: Body系列（默认）

#### 2. 字间距 (Letter Spacing)
```
Display Large:  -0.005  (紧凑)
Display/Medium:  0      (正常)
Title Medium:    0.015  (微松)
Body Medium:     0.025  (适中)
Label Small:     0.05   (宽松，提高可读性)
```

#### 3. 行高 (Line Height)
所有样式都设置了明确的`lineHeight`，确保垂直节奏一致：
```
Display Large:  64dp (57sp文字)
Headline Small: 32dp (24sp文字)
Body Large:     24dp (16sp文字) → 1.5倍行高
Label Small:    16dp (11sp文字)
```

### 使用建议

#### ✅ 推荐做法
```xml
<!-- 使用语义化样式 -->
<TextView
    style="@style/TextAppearance.VirtualLocation.CardTitle"
    android:text="当前位置" />

<!-- 使用预定义尺寸 -->
<TextView
    android:textSize="@dimen/text_size_body_large"
    android:text="正文" />
```

#### ❌ 避免做法
```xml
<!-- 不要硬编码字体大小 -->
<TextView
    android:textSize="15sp"  <!-- ❌ 魔法数字 -->
    android:text="不要这样" />

<!-- 不要忽略行高 -->
<TextView
    android:textSize="16sp"
    android:lineHeight="0dp"  <!-- ❌ 缺少行高 -->
    android:text="不要这样" />
```

---

## 📏 3. Spacing 间距系统

### 设计理念
基于**4dp网格系统** (4dp base unit)，所有间距都是4的倍数。

### 基础间距刻度 (Base Scale)

| 名称 | 值 | 用途 |
|------|-----|------|
| `spacing_xxs` | 2dp | 超小间距（图标内部） |
| `spacing_xs` | 4dp | 超小间距（紧密相关元素） |
| `spacing_sm` | 8dp | 小间距（相关组件） |
| `spacing_md_sm` | 12dp | 中小间距 |
| `spacing_md` | 16dp | **中等间距（标准，最常用）** |
| `spacing_md_lg` | 20dp | 中大间距 |
| `spacing_lg` | 24dp | 大间距（组件之间） |
| `spacing_xl` | 32dp | 超大间距（区块之间） |
| `spacing_xxl` | 40dp | 特大间距 |
| `spacing_xxxl` | 48dp | 巨大间距（页面分隔） |
| `spacing_huge` | 56dp | 超巨大间距 |
| `spacing_massive` | 64dp | 最大间距 |

### 语义化间距 (Semantic Spacing)

#### Padding (内边距)
```xml
<!-- 推荐使用语义化名称 -->
<View
    android:padding="@dimen/padding_small" />    <!-- 8dp -->
    
<View
    android:padding="@dimen/padding_medium" />   <!-- 16dp -->
    
<View
    android:padding="@dimen/padding_large" />    <!-- 24dp -->
```

#### Margin (外边距)
```xml
<View
    android:layout_margin="@dimen/margin_small" />    <!-- 8dp -->
    
<View
    android:layout_margin="@dimen/margin_medium" />   <!-- 16dp -->
    
<View
    android:layout_margin="@dimen/margin_large" />    <!-- 24dp -->
```

### 组件专用间距

#### 卡片 (Card)
```xml
<CardView
    android:padding="@dimen/card_padding"           <!-- 16dp -->
    android:layout_margin="@dimen/card_margin"      <!-- 8dp -->
    app:cardCornerRadius="@dimen/card_corner_radius" <!-- 16dp -->
    ... />
```

#### 按钮 (Button)
```xml
<Button
    android:paddingHorizontal="@dimen/button_padding_horizontal"  <!-- 16dp -->
    android:paddingVertical="@dimen/button_padding_vertical"      <!-- 8dp -->
    android:minHeight="@dimen/button_min_height"                  <!-- 48dp -->
    ... />
```

#### 列表项 (List Item)
```xml
<LinearLayout
    android:padding="@dimen/list_item_padding"    <!-- 16dp -->
    android:minHeight="@dimen/list_item_height"   <!-- 56dp -->
    ... />
```

#### BottomSheet
```xml
<FrameLayout
    android:padding="@dimen/bottom_sheet_padding"          <!-- 16dp -->
    app:behavior_peekHeight="@dimen/bottom_sheet_peek_height" <!-- 280dp -->
    ... />
```

### 图标尺寸系统

```xml
<!-- 标准图标 24dp -->
<ImageButton
    android:layout_width="@dimen/icon_size_medium"
    android:layout_height="@dimen/icon_size_medium"
    ... />

<!-- 大图标 32dp -->
<ImageButton
    android:layout_width="@dimen/icon_size_large"
    android:layout_height="@dimen/icon_size_large"
    ... />

<!-- 图标与文字间距 -->
<LinearLayout
    android:orientation="horizontal">
    <ImageView
        android:layout_width="24dp"
        android:layout_height="24dp" />
    <TextView
        android:layout_marginStart="@dimen/icon_text_spacing"  <!-- 8dp -->
        ... />
</LinearLayout>
```

### 触摸目标 (Touch Targets)

符合无障碍要求的最小触摸区域：

```xml
<!-- 最小触摸目标 48dp -->
<ImageButton
    android:layout_width="@dimen/touch_target_min"   <!-- 48dp -->
    android:layout_height="@dimen/touch_target_min"  <!-- 48dp -->
    ... />

<!-- 舒适触摸目标 56dp -->
<ImageButton
    android:layout_width="@dimen/touch_target_comfortable"  <!-- 56dp -->
    android:layout_height="@dimen/touch_target_comfortable" <!-- 56dp -->
    ... />
```

### 圆角系统 (Corner Radius)

```xml
<!-- 小圆角 4dp -->
<View
    android:background="@drawable/bg_rounded_small" />

<!-- 中圆角 8dp -->
<View
    android:background="@drawable/bg_rounded_medium" />

<!-- 大圆角 12dp -->
<Button
    android:background="@drawable/bg_button" />

<!-- 超大圆角 16dp -->
<CardView
    app:cardCornerRadius="@dimen/radius_xlarge" />  <!-- 16dp -->

<!-- 完全圆形 -->
<View
    android:background="@drawable/bg_circle" />
```

### 高度/阴影系统 (Elevation)

```xml
<!-- 无阴影 -->
<View
    android:elevation="@dimen/elevation_none" />   <!-- 0dp -->

<!-- 低阴影（卡片） -->
<CardView
    app:cardElevation="@dimen/elevation_low" />    <!-- 2dp -->

<!-- 中等阴影（悬浮按钮） -->
<FloatingActionButton
    android:elevation="@dimen/elevation_medium" /> <!-- 4dp -->

<!-- 高阴影（模态对话框） -->
<Dialog
    android:elevation="@dimen/elevation_high" />   <!-- 8dp -->
```

---

## 🎯 最佳实践

### 1. 始终使用设计令牌 (Design Tokens)

```xml
<!-- ✅ 好：使用设计令牌 -->
<TextView
    style="@style/TextAppearance.VirtualLocation.BodyLarge"
    android:padding="@dimen/padding_medium"
    android:layout_margin="@dimen/margin_small" />

<!-- ❌ 坏：硬编码值 -->
<TextView
    android:textSize="16sp"
    android:padding="16dp"
    android:layout_margin="8dp" />
```

### 2. 保持一致的垂直节奏

```xml
<!-- ✅ 好：使用统一的行高 -->
<LinearLayout
    android:orientation="vertical">
    <TextView
        style="@style/TextAppearance.VirtualLocation.TitleMedium"
        android:layout_marginBottom="@dimen/spacing_sm" />
    <TextView
        style="@style/TextAppearance.VirtualLocation.BodyMedium" />
</LinearLayout>
```

### 3. 遵循4dp网格

```xml
<!-- ✅ 好：4的倍数 -->
android:padding="8dp"    <!-- ✓ -->
android:padding="12dp"   <!-- ✓ -->
android:padding="16dp"   <!-- ✓ -->
android:padding="24dp"   <!-- ✓ -->

<!-- ❌ 坏：不是4的倍数 -->
android:padding="10dp"   <!-- ✗ -->
android:padding="15dp"   <!-- ✗ -->
android:padding="23dp"   <!-- ✗ -->
```

### 4. 语义化命名优先

```xml
<!-- ✅ 好：语义清晰 -->
android:padding="@dimen/card_padding"
android:layout_margin="@dimen/margin_medium"

<!-- ⚠️ 可接受：基础刻度 -->
android:padding="@dimen/spacing_md"
android:layout_margin="@dimen/spacing_sm"

<!-- ❌ 坏：魔法数字 -->
android:padding="16dp"
android:layout_margin="8dp"
```

---

## 📚 快速参考

### 常用Typography样式

| 场景 | 推荐样式 | 大小 |
|------|---------|------|
| 页面标题 | `PageTitle` | 24sp |
| 卡片标题 | `CardTitle` | 16sp |
| 正文内容 | `BodyLarge` | 16sp |
| 辅助文字 | `BodyMedium` | 14sp |
| 按钮文字 | `ButtonText` | 14sp |
| 坐标数值 | `CoordinateValue` | 16sp monospace |

### 常用Spacing值

| 场景 | 推荐值 | 实际大小 |
|------|--------|---------|
| 组件内边距 | `padding_medium` | 16dp |
| 组件间距 | `margin_small` | 8dp |
| 卡片内边距 | `card_padding` | 16dp |
| 列表项内边距 | `list_item_padding` | 16dp |
| 区块间距 | `spacing_lg` | 24dp |

### 常用图标尺寸

| 场景 | 推荐尺寸 | 实际大小 |
|------|---------|---------|
| 工具栏图标 | `icon_size_medium` | 24dp |
| 按钮图标 | `icon_size_medium` | 24dp |
| 大图标 | `icon_size_large` | 32dp |
| FAB图标 | `icon_size_xlarge` | 40dp |

---

## 🔧 迁移指南

### 从硬编码值迁移到设计令牌

#### Before
```xml
<TextView
    android:textSize="18sp"
    android:padding="12dp"
    android:layout_marginTop="8dp" />
```

#### After
```xml
<TextView
    style="@style/TextAppearance.VirtualLocation.HeadlineSmall"
    android:padding="@dimen/padding_medium"
    android:layout_marginTop="@dimen/margin_small" />
```

### 批量替换建议

1. **字体大小**: 搜索 `textSize="[0-9]+sp"` 并替换为对应样式
2. **间距**: 搜索 `padding="[0-9]+dp"` 和 `margin="[0-9]+dp"` 并替换
3. **图标尺寸**: 统一使用 `@dimen/icon_size_*`

---

## 📊 设计规范总结

| 维度 | 规范 | 说明 |
|------|------|------|
| **图标** | Vector Drawable | 56个统一图标 |
| **字体** | Material 3 Type Scale | 15个层级 + 6个别名 |
| **间距** | 4dp网格系统 | 12个基础刻度 |
| **圆角** | 6个级别 | 0dp ~ 9999dp |
| **阴影** | 5个级别 | 0dp ~ 16dp |
| **触摸目标** | ≥48dp | 符合WCAG 2.1 |

---

## ✨ 优势

### 一致性
- ✅ 全站统一的视觉语言
- ✅ 减少决策疲劳
- ✅ 提升品牌识别度

### 可维护性
- ✅ 一处修改，全局生效
- ✅ 清晰的命名规范
- ✅ 易于理解和协作

### 可扩展性
- ✅ 轻松添加新组件
- ✅ 支持主题切换
- ✅ 适配不同屏幕

### 无障碍
- ✅ 符合WCAG 2.1标准
- ✅ 足够的触摸区域
- ✅ 良好的对比度

---

**设计系统版本**: 1.0  
**最后更新**: 2026-04-16  
**遵循规范**: Material Design 3
