# IME 面板尺寸调节与输入法切换 — 设计文档

日期：2026-08-23
状态：已确认（用户批准）

## 背景与目标

TVKeyboard 的 IME 面板（`TvInputMethodService`）当前固定为 75% 屏高、半透明背景。本次迭代新增两个能力：

1. **切换输入法**：在 IME 面板上直接唤起系统输入法选择器，无需离开当前输入场景。
2. **面板尺寸可调**：支持 全屏 / 75% / 半屏 / 1/4 屏 四档循环切换，选择持久化记忆。

非目标：手机端控制面板尺寸、自由拖拽调整、面板停靠位置可配（固定底部）。

## 方案选型

采用**单布局 + 运行时自适应**（方案 A）：保留 `ime_tv_panel.xml` 单一布局文件，运行时按档位设置面板高度，并按高度阈值精简次要元素。

否决方案：
- 多套布局文件：三份布局同步维护成本高，切换需重绑控件，易出错。
- ConstraintLayout 百分比重写：需重写现有布局，回归风险大，收益不成比例。

## 详细设计

### 1. 尺寸档位（PanelSizeMode）

新增枚举 `com.tvkeyboard.tv.PanelSizeMode`：

| 档位 | 高度比例 | 说明 |
|------|---------|------|
| FULL | 1.00 | 全屏 |
| TALL | 0.75 | 当前默认档 |
| HALF | 0.50 | 半屏 |
| QUARTER | 0.25 | 1/4 屏 |

- `next()` 返回下一档，循环顺序 FULL → TALL → HALF → QUARTER → FULL。
- 默认值 `TALL`，保持现状不变。
- 持久化：`SharedPreferences`（文件名 `ime_panel_prefs`，key `height_fraction` 存 float）。`TvInputMethodService.onCreate` 读取；切换时写入。

### 2. 面板 UI 变更（ime_tv_panel.xml + Service）

左列底部新增横向按钮排（位于现有"遥控器提示"卡片下方）：

- **「尺寸：半屏」**：文本动态显示当前档位中文名；点击循环到下一档并立即生效。
- **「切换输入法」**：点击弹出系统输入法选择器。

按钮要求：
- 可聚焦（`focusable=true`、`background=@drawable/btn_secondary_bg`），遥控器方向键可聚焦、OK 键触发。
- 不干扰现有按键拦截：✓ 确认键由 `ConfirmKeyEventTracker` 处理、BACK 收起面板的逻辑保持不变。

### 3. 小尺寸自动精简

按面板实际高度像素判定（在设置 LayoutParams 后统一执行 `applyCompactMode()`）：

| 条件 | 动作 |
|------|------|
| ≤ 50% 屏高 | 隐藏"遥控器提示"卡片、隐藏底部提示条 |
| = 25% 屏高 | 二维码 148dp → 96dp；二维码卡片内"手机扫码输入"等文案字号各降一级；左列宽 200dp → 160dp |

恢复较大档位时全部还原为原始尺寸/可见性。

### 4. 切换输入法实现

- 按钮 onClick 与遥控器 **MENU 键**（`KEYCODE_MENU`，在 `onKeyDown` 拦截）均调用：
  ```java
  InputMethodManager imm = getSystemService(InputMethodManager.class);
  imm.showInputMethodPicker();
  ```
- 选择器由系统渲染；用户选中其他输入法后本 IME 自动退场，无需额外处理。
- 部分遥控器无 MENU 键，面板按钮为兜底入口。

### 5. 边界情况

- FULL（100%）在部分 TV 上可能被系统窗口机制裁剪：实现时真机验证；若显示异常，FULL 实际取 92%（常量集中定义便于调整）。
- 设备上无其他输入法时系统选择器自行呈现（仅列当前项），无需特殊处理。
- SharedPreferences 读取出错时回退默认档 TALL。

### 6. 测试策略

单元测试（junit，纯逻辑类不依赖 Android UI）：
- `PanelSizeMode.next()` 循环顺序覆盖四档闭环。
- `fromFraction(float)` 解析合法/非法值（含默认回退）。

手动验收清单：
1. 四档依次切换显示正确，重启键盘后记忆生效。
2. 「切换输入法」按钮与 MENU 键均能弹出系统选择器。
3. 半屏 / 1/4 屏下布局不破版，二维码与文字仍清晰可用。
4. ✓ 确认、BACK 收起、手机端同步输入等既有功能不受影响。
5. 焦点导航：方向键能在新按钮间移动，OK 触发正确。

## 影响范围

| 文件 | 变更 |
|------|------|
| `tv/PanelSizeMode.java` | 新增 |
| `tv/TvInputMethodService.java` | 档位应用、精简逻辑、MENU 拦截、按钮事件 |
| `res/layout/ime_tv_panel.xml` | 新增按钮排、控件 id |
| `res/values/strings.xml` | 按钮文案、档位名称 |
| `app/build.gradle` | 无依赖变更 |

风险低：不改动 WebSocket 协议、服务器、手机端任何代码。
