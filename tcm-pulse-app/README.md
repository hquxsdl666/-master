# 中医脉象诊断软件系统

基于华为WATCH GT4智能手表的中医脉象诊断系统，通过PPG传感器采集脉搏波数据，结合中医脉诊理论和人工智能算法，实现数字化脉象诊断。

## 系统架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        系统架构                                  │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────┐      BLE       ┌──────────────┐              │
│  │  华为WATCH   │ ◄────────────► │ Android客户端 │              │
│  │   GT4手表    │                │              │              │
│  │              │                │ • 数据可视化  │              │
│  │ • PPG采集    │                │ • 报告展示    │              │
│  │ • 信号处理   │                │ • 方剂推荐    │              │
│  │ • 边缘推理   │                │ • 历史记录    │              │
│  └──────────────┘                └──────┬───────┘              │
│                                         │                      │
│                                         │ HTTPS                │
│                                         ▼                      │
│                              ┌──────────────┐                  │
│                              │  云端服务     │                  │
│                              │              │                  │
│                              │ • AI推理     │                  │
│                              │ • 数据分析   │                  │
│                              │ • 知识库     │                  │
│                              └──────────────┘                  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## 项目结构

```
tcm-pulse-app/
├── android-client/          # Android客户端 (Kotlin + Jetpack Compose)
│   └── app/
│       ├── src/main/java/com/tcmpulse/pulseapp/
│       │   ├── data/
│       │   │   ├── api/     # API接口
│       │   │   ├── model/   # 数据模型
│       │   │   └── repository/ # 数据仓库
│       │   ├── ui/
│       │   │   ├── screens/ # 页面
│       │   │   ├── components/ # 组件
│       │   │   └── theme/   # 主题
│       │   ├── viewmodel/   # ViewModel
│       │   ├── bluetooth/   # 蓝牙通信
│       │   └── utils/       # 工具类
│       └── build.gradle.kts
│
├── backend-service/         # 后端服务 (Spring Boot + Kotlin)
│   └── src/main/kotlin/com/tcmpulse/
│       ├── entity/          # 实体类
│       ├── repository/      # 数据访问
│       ├── service/         # 业务逻辑
│       ├── controller/      # 控制器
│       ├── dto/             # 数据传输对象
│       └── config/          # 配置类
│
├── watch-app/               # 手表端 (HarmonyOS + ArkTS)
│   └── entry/src/main/
│       ├── ets/pages/       # 页面
│       └── resources/       # 资源文件
│
└── database/                # 数据库脚本
    └── init.sql             # 初始化脚本
```

## 核心功能

### 1. 脉象采集
- 利用华为WATCH GT4的PPG传感器采集脉搏波数据
- 采样率：100Hz
- 采集时长：60秒
- 实时信号质量评估

### 2. 脉象识别
- 支持28种中医脉象分类
- 基于深度学习的脉象分类模型
- 边缘推理 + 云端协同

### 3. 辨证分析
- 智能辨证引擎
- 证型与脉象关联分析
- 置信度评估

### 4. 方剂推荐
- 1000+经典方剂数据库
- 基于证型的智能推荐
- 禁忌检查

### 5. 数据可视化
- 脉波波形显示
- 脉象雷达图
- 健康趋势分析

## 技术栈

### 手表端
- **操作系统**: HarmonyOS 4.0+
- **开发语言**: ArkTS
- **UI框架**: ArkUI
- **传感器**: PPG心率传感器

### Android客户端
- **开发语言**: Kotlin
- **UI框架**: Jetpack Compose
- **架构**: MVVM
- **依赖注入**: Hilt
- **本地存储**: Room + DataStore
- **网络**: Retrofit + OkHttp
- **蓝牙**: Bluetooth LE

### 后端服务
- **框架**: Spring Boot 3.2
- **语言**: Kotlin
- **数据库**: PostgreSQL
- **缓存**: Redis
- **安全**: Spring Security + JWT

## 快速开始

### 1. 启动后端服务

```bash
cd backend-service
./mvnw spring-boot:run
```

### 2. 初始化数据库

```bash
# 创建数据库
createdb tcm_pulse

# 执行初始化脚本
psql -d tcm_pulse -f database/init.sql
```

### 3. 运行Android客户端

```bash
cd android-client
./gradlew :app:installDebug
```

### 4. 部署手表应用

使用DevEco Studio打开 `watch-app` 项目，连接华为WATCH GT4设备进行部署。

## API接口

### 脉象分析
```http
POST /api/v1/pulse/analyze
Content-Type: application/json

{
  "ppgData": "base64_encoded_data",
  "sampleRate": 100,
  "duration": 60
}
```

### 获取脉诊记录
```http
GET /api/v1/pulse/records?userId={userId}&page=1&size=20
```

### 获取方剂推荐
```http
POST /api/v1/prescriptions/recommend
Content-Type: application/json

{
  "pulseRecordId": "record_id",
  "mainPulse": "弦脉",
  "secondaryPulse": "细脉",
  "syndrome": "肝郁气滞"
}
```

## 28种脉象分类

| 分类 | 脉象 |
|------|------|
| 浮脉类 | 浮脉、洪脉、濡脉、散脉、芤脉、革脉 |
| 沉脉类 | 沉脉、伏脉、弱脉、牢脉 |
| 迟脉类 | 迟脉、缓脉、涩脉、结脉 |
| 数脉类 | 数脉、疾脉、促脉、动脉 |
| 虚脉类 | 虚脉、微脉、细脉、代脉、短脉 |
| 实脉类 | 实脉、滑脉、紧脉、长脉、弦脉、大脉 |

## 开发计划

| 阶段 | 周期 | 任务 |
|------|------|------|
| 第一阶段 | 4周 | 手表端基础功能开发 |
| 第二阶段 | 4周 | 脉象识别算法开发 |
| 第三阶段 | 3周 | Android客户端开发 |
| 第四周 | 3周 | 方剂推荐系统开发 |
| 第五阶段 | 2周 | 集成测试优化 |
| 第六阶段 | 2周 | 上线准备 |

## 许可证

MIT License

## 联系方式

- 项目主页: https://github.com/tcmpulse/tcm-pulse-app
- 问题反馈: https://github.com/tcmpulse/tcm-pulse-app/issues
