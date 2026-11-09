# UpWard 开发指南

Android 原生单模块应用（`:app`，包名 `com.xdl.upward`）：按项目记录 AI 对话、写日报、打卡提醒。纯本地应用，数据存 Room（`upward.db`），AI 走用户自配的 OpenAI 兼容接口。

## 构建与验证

- 编译（Windows）：`.\gradlew.bat assembleDebug --no-daemon --console=plain`；macOS/Linux 用 `./gradlew assembleDebug --no-daemon --console=plain`。
- 必须加 `--no-daemon`：本机 Gradle daemon 在构建结束后驻留后台并持有管道句柄，会导致明明已输出 `BUILD SUCCESSFUL` 但 bash 工具一直等待不返回。看到 `BUILD SUCCESSFUL`/`BUILD FAILED` 即判定构建结束，命令迟迟不返回不要干等。
- 工具链：Gradle 8.7 / AGP 8.6.0 / Kotlin 1.9.0 / KSP 1.9.0-1.0.13 / JDK 17 / compileSdk 34 / minSdk 31。版本升级需同步注意 Compose compiler（1.5.1）与 Kotlin 的配套关系。
- 发版：`.github/workflows/main.yml`（手动触发，输入 tag）。versionName 取 tag（去掉开头的 `v`），versionCode 取 `run_number + 2`，通过 `-PappVersionName` / `-PappVersionCode` 传给 `app/build.gradle.kts`；本地构建用文件里的默认值。**不要把版本号写死后只改 APK 文件名**。
- 无 lint 配置、无 pre-commit 钩子；`app/src/test` 和 `androidTest` 只有模板示例，没有真实测试。改逻辑后靠真机/模拟器跑 app 验证。

## 敏感文件（重要）

- 根目录 `keystore.properties`（明文密码）和 `app/xdl-turkey-blocker-release.jks` 是本地未跟踪文件（旧项目 xdl-turkey-blocker 遗留，与 UpWard 无关），**`.gitignore` 没有排除它们**。任何 `git add .` / `git add -A` 都会把密钥提交进仓库：提交前必须 `git status` 检查，永远不要 add 这两个文件，建议把它们补进 `.gitignore`。
- release 签名依赖根目录 `keystore.properties` 存在；debug 构建不依赖。

## Room 数据库约定

- `data/local/AppDatabase.kt` 中 `version = 17`，手写 Migration 1→17，**没有** `fallbackToDestructiveMigration`。修改实体字段时必须同步加对应 Migration，否则已装用户升级会直接 crash（Room 抛 IllegalStateException）。
- 新 config 键的默认值要用 `onOpen` 回调里已有的 `INSERT ... WHERE NOT EXISTS` 模式注入。
- `onOpen` 每次打开都会 `DELETE FROM config WHERE key = 'daily_record_prompt'`，这是有意行为，不要当 bug 修。

## 聊天与 AI

- `domain/ChatExecutor.kt` 是进程级单例：聊天请求生命周期脱离页面，退出聊天页/App 退后台仍会继续流式请求并写库；ViewModel 通过 `observeProjectChat(projectId)` 观察执行状态。改聊天逻辑先看这里。
- 本地工具协议：AI 在回复末尾输出 JSONL，每行一个 `{"name":"...","arguments":{...}}`，可多行/多个工具（`set_next_resume_time` 强制指定下次汇报时间、`complete_daily_challenge` 挑战打卡）。这些行**原样显示**在聊天里，回复结束后由 `ChatExecutor.executeToolCalls` 逐行解析执行（有 `name` 字段的 JSON 行即视为调用，新增工具只需在 `executeToolCalls` 的 when 里加分支，未知工具名会提示失败；兼容旧 `<tool_call>` 标签），执行结果只走页面临时提示、不写进正文。违规累计 10 次**不再清空数据**，而是进入惩罚状态：拦截所有项目消息发送（ProjectDetailViewModel / ChatExecutor 入口拦截），违规次数达到 10 次后仍继续累加；惩罚状态不受月度清零影响。
- 聊天上下文：`AiMessageBuilder.buildChatMessages` 默认附加回复篇幅规则（`CHAT_REPLY_RULES`，1~3 句、100 字内；输入框“闲聊模式”开关、心里话模式、生成日报时不加）；经验记录（每日记录内容以“经验”开头）默认**不**拼进聊天，只有开启经验模式时才带上。
- **惩罚捐款审核不走 AI 对话**（`domain/PaymentVerifier.kt`）：用户在挑战页 / 违规惩罚页上传捐款截图 → 一次 LLM 调用只识别 JSON（`payment_success`/`order_no`/`amount`/`payee`/`paid_at`/`is_charity`）→ 本地规则判定：支付成功、识别出交易单号、收款方为慈善机构、金额达标（挑战失败惩罚 ≥3 元，违规惩罚 ≥4 元）、单号未用过。通过后在一个事务里写 `payment_record` 并结算；不通过不入库，删除截图。支付时间目前只记录不校验。
  - 挑战失败惩罚固定为捐款 3 元（`ChallengeViewModel.FIXED_FAIL_PENALTY`），通过后该挑战 failed → settled，并把结算当天（在挑战周期内时）自动记为已打卡，避免当天一直悬着“未打卡”（违规惩罚连带结算同样处理）；AI 不能结算挑战（已移除 `settle_failed_challenge` 工具）。
  - 违规惩罚通过后违规次数 -10（降到 10 次以下才解除），并调用 `ChallengeRepository.settleAllFailed()` 一并结算所有失败中的挑战（结算前会补做缺卡检测）。已移除违规告诫 AI 对话与 `reset_violation_count` 工具。
  - 识别用当前选中的 API（需 `supportImage`），否则用其视觉辅助模型；都没有则直接报错。
- 请假：违规惩罚页标题栏的请假按钮（`ViolationChatViewModel.requestLeave`），每个自然周（周一~周日，凌晨 4 点换日）一次，记录在 config `leave_last_date`。效果是清除所有项目的汇报窗口并取消提醒；清除前先对每个项目跑 `RequiredMessageDeadlineHandler.checkAndRecord`，已过期未汇报的窗口照常记违规；没有窗口可清时不消耗次数。
- 挑战可设过渡期：`challenge.transition_end_date`（含当天，空 = 无过渡期，范围 开始日期 ≤ x < 截止日期）。今天处于过渡期时，`ChatExecutor.buildChallengeSystemMessage` 会在提示词里写明“日期1 ~ 日期2 是过渡期，今天是 xxx，属于过渡期”。已开始的挑战只能提前结束或取消过渡期，不能新增或延长。
- 全局提醒时间存在 config 表：`global_reminder_morning_time` / `afternoon` / `evening`（默认 07:00 / 13:00 / 19:30）；`reminder/GlobalReminderScheduler.kt` 用 `setExactAndAllowWhileIdle` 排程，Android 13+ 需要用户授予精确闹钟权限。`MainActivity.onCreate` 会初始化 config 并 reschedule。
- Manifest 已开 `usesCleartextTraffic="true"`，允许 http 明文 AI 接口（局域网/127.0.0.1 地址）。

## 导航与 UI

- 路由串集中在 `ui/navigation/AppRoute.kt`，NavHost 在 `ui/navigation/AppNavGraph.kt`。新增页面必须同时改这两个文件（AppRoute 里定义路由+参数，AppNavGraph 里挂 composable）。
- UI 按功能分目录：`ui/project`、`ui/record`、`ui/config`、`ui/api`、`ui/violation`；页面与其 ViewModel 同目录成对出现。

## 备份格式

- `data/repository/BackupRepository.kt`：备份为 zip，内含 `backup.json`（`format` 字段固定为 `upward-full-backup`）和 `chat_images/`；恢复流程会清空全部表再重插。改实体字段时如需兼容旧备份，注意 `readXxx` 用 `optXxx` 兜底。
