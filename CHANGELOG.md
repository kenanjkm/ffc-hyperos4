# 更新记录

## 2026111002-hyperos4（97）

- Ana sayfaya **Hook durumu** kartı eklendi: system_server açılışta hangi hook gruplarının kurulduğunu (ve kurulamayanların hatasını) bir kez modül uygulamasına bildirir; karta dokununca liste açılır. Rapor önceki açılıştansa uyarı gösterilir.
- Bildirim mekanizması: mevcut açılış/kilit açma başlatmasına bağlı tek seferlik `ContentProvider.call`; alarm, periyodik iş veya wakelock yok.

## 2026111001-hyperos4（96）

- HyperOS: Greeze'nin PID tabanlı dondurma yolları da teslim penceresine uyuyor (`freezePids`, `freezeProcess`). PowerKeeper, uygulamaların ana olmayan süreçlerini bu yoldan donduruyordu (`ActionExecute.freezeNonKeyProcesses`); çok süreçli uygulamalarda FCM işlenirken yan süreç dondurulabiliyordu.
- Greeze çözme gerekçesi `broadcast` (günlükteki `unknown reason:fcmfix` uyarısı kalktı).
- Arayüz: HyperOS'ta ColorOS'a özel iki ayar gizlendi; Ice Box ayarının çevirisi düzeltildi.

## 2026110901-hyperos4（95）

- HyperOS 4 desteği (HyperOS 4 / Android 17 services.jar ve miui-services.jar üzerinde doğrulandı):
  - MIUI otomatik başlatma: `BroadcastQueueModernStubImpl` (`checkApplicationAutoStart`, `checkReceiverAppDealBroadcast`, `checkReceiverIfRestricted`) yalnızca GMS'den gelen ve doğrulanan FCM için atlanır
  - `AutoStartManagerServiceStubImpl.isAllowStartService` yalnızca 20 sn'lik FCM teslim penceresinde izin verir
  - Greeze dondurucu: `isRestrictReceiver`, `isRestrictBackgroundAction`, `freezeAction`, `freezeUids`; hedef uygulama `thawUidAsync` ile çözülür
  - "GMS'yi uyanık tut" (Doze beyaz listesi ayarı) HyperOS'ta Greeze'nin GMS ağ kısıtlamasını ve hızlı dondurmasını da engeller
  - MIUI yerel bildirim engeli (`isDeniedLocalNotification`) izinli uygulamalar için kaldırılır
- Arayüz: metinler kaynak dosyalarına taşındı; Türkçe ve İngilizce eklendi
- Derin uyku: wakelock, alarm veya zamanlanmış iş eklenmedi

## 2026110501（94）

- 修复关闭界面后，Xposed 服务监听仍持有页面对象的问题
- 应用列表保留系统语言排序，刷新时继续显示已有列表，并合并重复加载
- 减少重复配置读取和无变化的远端写入，保留初始化、旧设置迁移和安装事件去重
- 清理未使用的日志转发、反射工具、旧布局、兼容资源和无效的目标包名推断
- 移除现有界面未使用的 Hook 健康状态上报及旧通知频道卸载清理，保留安装失败日志和 Google 网络修复重试
- 最低安装要求调整为 Android 14（API 34），与已使用的系统接口一致；适配范围为 ColorOS17

## 20261001（85）

- ui小优化

## 20261001（82）

- 拆分深度睡眠优化和 Doze 待机优化白名单
- 修复解除 Google 网络限制

## 20260930

- 首个正式发布版本，包名 `io.github.zopulus.ffc`
- API 102，静态作用域为系统框架和电池
- ColorOS17 推送修复、白名单管理与 MD3E 界面
- 首页系统信息卡片、动态配色与主题图标
- 关于入口指向本仓库
- 仅 arm64-v8a，启用代码与资源裁剪
- 显示版本号采用构建日期，内部版本码为 79
