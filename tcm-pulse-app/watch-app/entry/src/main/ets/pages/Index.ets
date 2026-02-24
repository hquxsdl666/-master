import sensor from '@ohos.sensor';
import vibrator from '@ohos.vibrator';
import promptAction from '@ohos.promptAction';
import { router } from '@ohos.router';

@Entry
@Component
struct Index {
  @State isCollecting: boolean = false
  @State collectionProgress: number = 0
  @State signalQuality: number = 0
  @State mainPulse: string = ''
  @State pulseRate: number = 0
  @State collectionComplete: boolean = false
  
  private ppgData: Float32Array = new Float32Array(6000) // 60秒 * 100Hz
  private dataIndex: number = 0
  private sampleRate: number = 100
  private collectionDuration: number = 60 // 秒
  private timerId: number = -1
  
  aboutToAppear() {
    // 页面加载时初始化
  }
  
  aboutToDisappear() {
    this.stopCollection()
  }
  
  build() {
    Column() {
      // 标题栏
      Row() {
        Text('脉诊通')
          .fontSize(24)
          .fontWeight(FontWeight.Bold)
          .fontColor('#FFFFFF')
      }
      .width('100%')
      .height(60)
      .justifyContent(FlexAlign.Center)
      .backgroundColor('#2196F3')
      
      // 主内容区
      Column() {
        if (this.collectionComplete) {
          this.ResultView()
        } else if (this.isCollecting) {
          this.CollectingView()
        } else {
          this.IdleView()
        }
      }
      .width('100%')
      .layoutWeight(1)
      .padding(16)
      
      // 底部导航
      if (!this.isCollecting && !this.collectionComplete) {
        this.BottomNav()
      }
    }
    .width('100%')
    .height('100%')
    .backgroundColor('#F5F5F5')
  }
  
  @Builder
  IdleView() {
    Column() {
      // 脉象图标
      Column() {
        Image($r('app.media.ic_pulse'))
          .width(120)
          .height(120)
          .objectFit(ImageFit.Contain)
      }
      .width(200)
      .height(200)
      .backgroundColor('#E3F2FD')
      .borderRadius(100)
      .justifyContent(FlexAlign.Center)
      
      Blank().height(30)
      
      Text('脉诊通')
        .fontSize(28)
        .fontWeight(FontWeight.Bold)
        .fontColor('#2196F3')
      
      Text('智能中医脉象诊断')
        .fontSize(16)
        .fontColor('#666666')
        .margin({ top: 8 })
      
      Blank().height(40)
      
      // 开始按钮
      Button('开始脉诊', { type: ButtonType.Capsule })
        .width(200)
        .height(50)
        .fontSize(18)
        .fontColor('#FFFFFF')
        .backgroundColor('#2196F3')
        .onClick(() => {
          this.startCollection()
        })
      
      Blank().height(20)
      
      // 上次检测信息
      Column() {
        Text('上次检测: 2小时前')
          .fontSize(14)
          .fontColor('#999999')
        
        Text('脉象: 平和脉 ✓')
          .fontSize(16)
          .fontColor('#4CAF50')
          .margin({ top: 4 })
      }
      .margin({ top: 20 })
    }
    .width('100%')
    .height('100%')
    .justifyContent(FlexAlign.Center)
  }
  
  @Builder
  CollectingView() {
    Column() {
      // 波形显示区域
      Column() {
        // 模拟波形显示
        Canvas(this.context)
          .width('100%')
          .height(150)
          .backgroundColor('#1A1A2E')
          .borderRadius(12)
          .onReady((context) => {
            // 绘制波形
            this.drawWaveform(context)
          })
      }
      .width('100%')
      .padding(16)
      
      Blank().height(20)
      
      // 进度显示
      Text(`${this.collectionProgress}%`)
        .fontSize(48)
        .fontWeight(FontWeight.Bold)
        .fontColor('#2196F3')
      
      Blank().height(10)
      
      // 进度条
      Progress({ value: this.collectionProgress, total: 100, type: ProgressType.Linear })
        .width('80%')
        .height(8)
        .color('#2196F3')
        .backgroundColor('#E0E0E0')
      
      Blank().height(20)
      
      // 信号质量
      Row() {
        Circle({ width: 12, height: 12 })
          .fill(this.getSignalQualityColor())
        
        Text(`  ${this.getSignalQualityText()}`)
          .fontSize(14)
          .fontColor('#666666')
      }
      
      Blank().height(10)
      
      Text('正在采集脉象数据...')
        .fontSize(18)
        .fontColor('#333333')
      
      Text('请保持手腕平稳')
        .fontSize(14)
        .fontColor('#999999')
        .margin({ top: 8 })
      
      Blank().height(30)
      
      // 取消按钮
      Button('取消采集', { type: ButtonType.Capsule })
        .width(150)
        .height(40)
        .fontSize(14)
        .fontColor('#666666')
        .backgroundColor('#E0E0E0')
        .onClick(() => {
          this.stopCollection()
        })
    }
    .width('100%')
    .height('100%')
    .justifyContent(FlexAlign.Center)
  }
  
  @Builder
  ResultView() {
    Column() {
      // 结果卡片
      Column() {
        Text('脉诊结果')
          .fontSize(18)
          .fontColor('#666666')
        
        Blank().height(16)
        
        Text(this.mainPulse)
          .fontSize(36)
          .fontWeight(FontWeight.Bold)
          .fontColor('#2196F3')
        
        Blank().height(8)
        
        Text(`${this.pulseRate} 次/分`)
          .fontSize(18)
          .fontColor('#666666')
      }
      .width('100%')
      .padding(24)
      .backgroundColor('#FFFFFF')
      .borderRadius(16)
      .shadow({ radius: 8, color: '#20000000' })
      
      Blank().height(30)
      
      // 操作按钮
      Column({ space: 12 }) {
        Button('查看详情', { type: ButtonType.Capsule })
          .width(200)
          .height(44)
          .fontSize(16)
          .fontColor('#FFFFFF')
          .backgroundColor('#2196F3')
          .onClick(() => {
            // 跳转到详情页
            router.pushUrl({ url: 'pages/Detail' })
          })
        
        Button('同步到手机', { type: ButtonType.Capsule })
          .width(200)
          .height(44)
          .fontSize(16)
          .fontColor('#2196F3')
          .backgroundColor('#E3F2FD')
          .onClick(() => {
            this.syncToPhone()
          })
        
        Button('重新检测', { type: ButtonType.Capsule })
          .width(200)
          .height(44)
          .fontSize(16)
          .fontColor('#666666')
          .backgroundColor('#F5F5F5')
          .onClick(() => {
            this.collectionComplete = false
            this.mainPulse = ''
            this.collectionProgress = 0
          })
      }
    }
    .width('100%')
    .height('100%')
    .padding(24)
    .justifyContent(FlexAlign.Center)
  }
  
  @Builder
  BottomNav() {
    Row() {
      Column() {
        Image($r('app.media.ic_home'))
          .width(24)
          .height(24)
          .fillColor('#2196F3')
        Text('首页')
          .fontSize(12)
          .fontColor('#2196F3')
      }
      .layoutWeight(1)
      .alignItems(HorizontalAlign.Center)
      
      Column() {
        Image($r('app.media.ic_history'))
          .width(24)
          .height(24)
          .fillColor('#999999')
        Text('记录')
          .fontSize(12)
          .fontColor('#999999')
      }
      .layoutWeight(1)
      .alignItems(HorizontalAlign.Center)
      .onClick(() => {
        router.pushUrl({ url: 'pages/History' })
      })
      
      Column() {
        Image($r('app.media.ic_settings'))
          .width(24)
          .height(24)
          .fillColor('#999999')
        Text('设置')
          .fontSize(12)
          .fontColor('#999999')
      }
      .layoutWeight(1)
      .alignItems(HorizontalAlign.Center)
      .onClick(() => {
        router.pushUrl({ url: 'pages/Settings' })
      })
    }
    .width('100%')
    .height(60)
    .backgroundColor('#FFFFFF')
    .shadow({ radius: 4, color: '#10000000' })
  }
  
  // 开始采集
  private startCollection() {
    this.isCollecting = true
    this.collectionProgress = 0
    this.dataIndex = 0
    this.ppgData = new Float32Array(this.collectionDuration * this.sampleRate)
    
    // 启动传感器
    this.startPPGSensor()
    
    // 启动进度更新定时器
    this.timerId = setInterval(() => {
      this.collectionProgress += 1
      
      // 模拟信号质量评估
      this.signalQuality = 0.7 + Math.random() * 0.25
      
      if (this.collectionProgress >= 100) {
        this.stopCollection()
        this.analyzePulse()
      }
    }, this.collectionDuration * 10) // 每600ms更新1%
  }
  
  // 停止采集
  private stopCollection() {
    this.isCollecting = false
    if (this.timerId !== -1) {
      clearInterval(this.timerId)
      this.timerId = -1
    }
    this.stopPPGSensor()
  }
  
  // 启动PPG传感器
  private startPPGSensor() {
    try {
      sensor.on(sensor.SensorId.SENSOR_ID_HEART_RATE, (data) => {
        if (this.isCollecting && this.dataIndex < this.ppgData.length) {
          this.ppgData[this.dataIndex++] = data.ppg
        }
      })
    } catch (err) {
      console.error('启动传感器失败:', err)
    }
  }
  
  // 停止PPG传感器
  private stopPPGSensor() {
    try {
      sensor.off(sensor.SensorId.SENSOR_ID_HEART_RATE)
    } catch (err) {
      console.error('停止传感器失败:', err)
    }
  }
  
  // 分析脉象
  private analyzePulse() {
    // 模拟脉象分析
    const pulseTypes = ['平和脉', '弦脉', '滑脉', '细脉', '浮脉', '沉脉']
    const randomIndex = Math.floor(Math.random() * pulseTypes.length)
    this.mainPulse = pulseTypes[randomIndex]
    this.pulseRate = 60 + Math.floor(Math.random() * 20)
    this.collectionComplete = true
    
    // 震动提示
    vibrator.startVibration({
      type: 'time',
      duration: 300
    })
  }
  
  // 同步到手机
  private syncToPhone() {
    promptAction.showToast({
      message: '正在同步到手机...',
      duration: 2000
    })
    
    // 实际实现需要使用Wear Engine SDK
    // 通过蓝牙将数据发送到手机端
  }
  
  // 获取信号质量颜色
  private getSignalQualityColor(): ResourceColor {
    if (this.signalQuality > 0.8) {
      return '#4CAF50'
    } else if (this.signalQuality > 0.5) {
      return '#FFA726'
    } else {
      return '#EF5350'
    }
  }
  
  // 获取信号质量文本
  private getSignalQualityText(): string {
    if (this.signalQuality > 0.8) {
      return '信号良好'
    } else if (this.signalQuality > 0.5) {
      return '信号一般'
    } else {
      return '信号较差'
    }
  }
  
  // 绘制波形
  private drawWaveform(context: CanvasRenderingContext2D) {
    const width = context.width
    const height = context.height
    const centerY = height / 2
    
    context.strokeStyle = '#00E676'
    context.lineWidth = 2
    context.beginPath()
    
    // 绘制模拟波形
    for (let i = 0; i < width; i += 2) {
      const x = i
      const y = centerY + Math.sin(i * 0.1) * 30 + Math.sin(i * 0.05) * 15
      if (i === 0) {
        context.moveTo(x, y)
      } else {
        context.lineTo(x, y)
      }
    }
    
    context.stroke()
  }
}
