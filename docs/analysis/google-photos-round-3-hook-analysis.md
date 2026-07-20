# Google Photos round 3 hook analysis

## 结论

当前仓库没有 Google Photos APK、split APK 或反编译产物，且本机 `adb devices`
未发现已连接设备。因此本轮不能把任何 Google Photos 业务方法声明为最终坐标转换点。

本轮实现选择一个严格受作用域保护的只读运行时候选：

- 候选：`com.google.android.gms.maps.model.LatLng#<init>(double,double)`
- 参数：`arg0=latitude`，`arg1=longitude`
- 写入行为：无；只读取构造参数并记录过滤后的调用栈
- 作用域：仅当 `CollectionsGridPageActivity` 在 3 秒内跳转到
  `MapExploreActivity` 时启用

## 证据与缺口

round 2 已确认地图页 Activity 名称为
`com.google.android.apps.photos.mapexplore.ui.MapExploreActivity`，合集页入口为
`com.google.android.apps.photos.collectionstab.collectionsgridpage.CollectionsGridPageActivity`。
旧探针也观察到 Google Maps 运行时类，例如 `com.google.maps.api.android.lib6.impl.au`。

缺口是当前任务环境缺少 Google Photos 安装包和真机运行日志，无法用静态反编译或设备栈追踪定位 Photos 内部业务坐标转换方法。为避免误选，本轮不声明最终方法，只安装 Google Maps `LatLng`
构造候选，等待真机日志给出 Photos/Maps 过滤栈后再收敛。

## 运行时作用域设计

`GooglePhotosMapScopeTracker` 在非地图 Activity `onPause` 时记录
`NavigationMarker(sourceActivityClassName, createdAtElapsedRealtime)`。当
`MapExploreActivity` `onCreate` 或首次 `onResume` 到达时消费该 marker：

- marker 来自 `CollectionsGridPageActivity` 且时间差在 `0..3000ms`：标记为 `COLLECTIONS`
- marker 来自其他 Activity：标记为 `OTHER`
- marker 缺失、过期或时间倒退：标记为 `UNKNOWN`

坐标探针只在当前弱引用地图 Activity 被标记为 `COLLECTIONS` 时记录候选。`OTHER` 和
`UNKNOWN` 都失败关闭。

## 候选日志策略

每个 Map Activity、每个候选最多记录 10 个不重复坐标。重复坐标直接跳过；超过上限后只记录一次
`candidate sample limit reached`，之后继续静默跳过，避免滚动地图时日志爆炸。

记录栈只保留以下前缀，并限制 10 帧：

- `com.google.android.apps.photos`
- `com.google.android.gms.maps`

## 后续验证标准

设备验证时需要从日志中确认：

- `map entry source bound source=COLLECTIONS`
- `coordinate observed source=COLLECTIONS signature=com.google.android.gms.maps.model.LatLng#<init>(double,double)`
- 过滤栈中出现稳定的 Google Photos 业务类或方法
- 同一候选在非合集入口进入地图时没有坐标日志

只有满足以上条件后，下一轮才能把具体 Photos 业务方法列为可修改的转换点。
