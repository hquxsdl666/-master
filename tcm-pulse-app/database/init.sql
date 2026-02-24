-- 中医脉象诊断系统数据库初始化脚本

-- 创建数据库
CREATE DATABASE IF NOT EXISTS tcm_pulse CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE tcm_pulse;

-- 用户表
CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(36) PRIMARY KEY,
    phone VARCHAR(20) UNIQUE,
    email VARCHAR(100) UNIQUE,
    password_hash VARCHAR(255),
    nickname VARCHAR(50),
    avatar_url VARCHAR(255),
    real_name VARCHAR(50),
    gender TINYINT DEFAULT 0,
    birth_date DATE,
    age INT,
    height DECIMAL(5,2),
    weight DECIMAL(5,2),
    body_type VARCHAR(20),
    constitution VARCHAR(50),
    medical_history TEXT,
    allergies TEXT,
    status TINYINT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP,
    INDEX idx_phone (phone),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 用户设备表
CREATE TABLE IF NOT EXISTS user_devices (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    device_id VARCHAR(50) NOT NULL,
    device_type VARCHAR(30),
    device_name VARCHAR(100),
    device_model VARCHAR(50),
    os_version VARCHAR(30),
    app_version VARCHAR(20),
    bluetooth_mac VARCHAR(17),
    bind_status TINYINT DEFAULT 1,
    bind_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_sync_at TIMESTAMP,
    is_primary TINYINT DEFAULT 0,
    FOREIGN KEY (user_id) REFERENCES users(id),
    UNIQUE KEY uk_user_device (user_id, device_id),
    INDEX idx_device_id (device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 脉诊记录表
CREATE TABLE IF NOT EXISTS pulse_records (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    device_id VARCHAR(50) NOT NULL,
    record_time TIMESTAMP NOT NULL,
    measurement_duration SMALLINT DEFAULT 60,
    sample_rate TINYINT DEFAULT 100,
    signal_quality DECIMAL(3,2),
    valid_segments SMALLINT,
    total_segments SMALLINT,
    main_pulse VARCHAR(20),
    secondary_pulse VARCHAR(20),
    pulse_rate TINYINT,
    position_floating DECIMAL(3,2),
    position_normal DECIMAL(3,2),
    position_deep DECIMAL(3,2),
    rate_category VARCHAR(10),
    rhythm_regularity DECIMAL(3,2),
    has_intermittent TINYINT DEFAULT 0,
    force_score DECIMAL(3,2),
    systolic_amplitude DECIMAL(6,3),
    diastolic_amplitude DECIMAL(6,3),
    width_score DECIMAL(3,2),
    length_score DECIMAL(3,2),
    smoothness DECIMAL(3,2),
    tautness DECIMAL(3,2),
    fullness DECIMAL(3,2),
    hollowness DECIMAL(3,2),
    rising_slope DECIMAL(6,3),
    falling_slope DECIMAL(6,3),
    dicrotic_notch_depth DECIMAL(6,3),
    wave_area DECIMAL(8,3),
    dominant_freq DECIMAL(4,2),
    lf_power DECIMAL(8,3),
    hf_power DECIMAL(8,3),
    lf_hf_ratio DECIMAL(5,2),
    syndrome VARCHAR(50),
    syndrome_confidence DECIMAL(3,2),
    ppg_data_url VARCHAR(255),
    feature_vector TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    synced_at TIMESTAMP,
    sync_status TINYINT DEFAULT 0,
    FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_user_time (user_id, record_time),
    INDEX idx_main_pulse (main_pulse),
    INDEX idx_syndrome (syndrome)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 方剂表
CREATE TABLE IF NOT EXISTS prescriptions (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    pinyin VARCHAR(100),
    alias VARCHAR(200),
    category VARCHAR(50),
    source_book VARCHAR(100),
    source_dynasty VARCHAR(20),
    source_author VARCHAR(50),
    original_text TEXT,
    composition TEXT NOT NULL,
    total_herbs TINYINT,
    preparation TEXT,
    dosage TEXT,
    duration VARCHAR(50),
    efficacy TEXT NOT NULL,
    indications TEXT NOT NULL,
    symptoms TEXT,
    tongue_pulse TEXT,
    contraindications TEXT,
    precautions TEXT,
    side_effects TEXT,
    analysis TEXT,
    modifications TEXT,
    modern_applications TEXT,
    clinical_studies TEXT,
    authority_score DECIMAL(3,2) DEFAULT 0.80,
    usage_frequency INT DEFAULT 0,
    is_classic TINYINT DEFAULT 0,
    is_official TINYINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_name (name),
    INDEX idx_category (category),
    INDEX idx_is_classic (is_classic)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 证型表
CREATE TABLE IF NOT EXISTS syndromes (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    pinyin VARCHAR(100),
    category VARCHAR(50),
    main_symptoms TEXT,
    secondary_symptoms TEXT,
    tongue TEXT,
    pulse TEXT,
    pathogenesis TEXT,
    location VARCHAR(100),
    nature VARCHAR(50),
    treatment_principle TEXT,
    common_prescriptions TEXT,
    common_herbs TEXT,
    differentiation TEXT,
    similar_syndromes TEXT,
    prognosis TEXT,
    complications TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_name (name),
    INDEX idx_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 初始化方剂数据
INSERT INTO prescriptions (name, category, composition, efficacy, indications, is_classic, authority_score) VALUES
('四君子汤', '补益剂', '[{"herb":"人参","dosage":"9g","role":"君"},{"herb":"白术","dosage":"9g","role":"臣"},{"herb":"茯苓","dosage":"9g","role":"佐"},{"herb":"甘草","dosage":"6g","role":"使"}]', '益气健脾', '脾胃气虚证。面色萎白，语声低微，气短乏力，食少便溏，舌淡苔白，脉虚弱。', 1, 0.95),
('四物汤', '补益剂', '[{"herb":"当归","dosage":"9g","role":"君"},{"herb":"川芎","dosage":"6g","role":"臣"},{"herb":"白芍","dosage":"9g","role":"佐"},{"herb":"熟地黄","dosage":"12g","role":"佐"}]', '补血和血', '营血虚滞证。心悸失眠，头晕目眩，面色无华，妇人月经不调，量少或经闭不行，脐腹作痛，舌淡，脉细弦。', 1, 0.95),
('桂枝汤', '解表剂', '[{"herb":"桂枝","dosage":"9g","role":"君"},{"herb":"白芍","dosage":"9g","role":"臣"},{"herb":"生姜","dosage":"9g","role":"佐"},{"herb":"大枣","dosage":"3枚","role":"佐"},{"herb":"甘草","dosage":"6g","role":"使"}]', '解肌发表，调和营卫', '外感风寒表虚证。发热头痛，汗出恶风，鼻鸣干呕，苔白不渴，脉浮缓或浮弱。', 1, 0.95),
('柴胡疏肝散', '理气剂', '[{"herb":"柴胡","dosage":"6g","role":"君"},{"herb":"香附","dosage":"9g","role":"臣"},{"herb":"川芎","dosage":"9g","role":"臣"},{"herb":"陈皮","dosage":"9g","role":"佐"},{"herb":"枳壳","dosage":"9g","role":"佐"},{"herb":"白芍","dosage":"9g","role":"佐"},{"herb":"甘草","dosage":"3g","role":"使"}]', '疏肝理气，活血止痛', '肝气郁滞证。胁肋疼痛，胸闷喜太息，情志抑郁易怒，或嗳气，脘腹胀满，脉弦。', 1, 0.92),
('逍遥散', '和解剂', '[{"herb":"柴胡","dosage":"9g","role":"君"},{"herb":"当归","dosage":"9g","role":"臣"},{"herb":"白芍","dosage":"9g","role":"臣"},{"herb":"白术","dosage":"9g","role":"佐"},{"herb":"茯苓","dosage":"9g","role":"佐"},{"herb":"甘草","dosage":"6g","role":"使"}]', '疏肝解郁，养血健脾', '肝郁血虚脾弱证。两胁作痛，头痛目眩，口燥咽干，神疲食少，或月经不调，乳房胀痛，脉弦而虚。', 1, 0.94),
('白虎汤', '清热剂', '[{"herb":"石膏","dosage":"50g","role":"君"},{"herb":"知母","dosage":"18g","role":"臣"},{"herb":"甘草","dosage":"6g","role":"佐"},{"herb":"粳米","dosage":"9g","role":"使"}]', '清热生津', '气分热盛证。壮热面赤，烦渴引饮，汗出恶热，脉洪大有力。', 1, 0.93),
('理中丸', '温里剂', '[{"herb":"人参","dosage":"9g","role":"君"},{"herb":"干姜","dosage":"9g","role":"臣"},{"herb":"白术","dosage":"9g","role":"佐"},{"herb":"甘草","dosage":"9g","role":"使"}]', '温中祛寒，补气健脾', '脾胃虚寒证。脘腹绵绵作痛，喜温喜按，呕吐，大便稀溏，脘痞食少，畏寒肢冷，口不渴，舌淡苔白润，脉沉细或沉迟无力。', 1, 0.94),
('六味地黄丸', '补益剂', '[{"herb":"熟地黄","dosage":"24g","role":"君"},{"herb":"山茱萸","dosage":"12g","role":"臣"},{"herb":"山药","dosage":"12g","role":"臣"},{"herb":"泽泻","dosage":"9g","role":"佐"},{"herb":"茯苓","dosage":"9g","role":"佐"},{"herb":"丹皮","dosage":"9g","role":"佐"}]', '滋补肝肾', '肝肾阴虚证。腰膝酸软，头晕目眩，耳鸣耳聋，盗汗，遗精，消渴，骨蒸潮热，手足心热，口燥咽干，牙齿动摇，足跟作痛，小便淋沥，舌红少苔，脉沉细数。', 1, 0.96),
('血府逐瘀汤', '理血剂', '[{"herb":"桃仁","dosage":"12g","role":"君"},{"herb":"红花","dosage":"9g","role":"君"},{"herb":"当归","dosage":"9g","role":"臣"},{"herb":"生地黄","dosage":"9g","role":"臣"},{"herb":"川芎","dosage":"5g","role":"佐"},{"herb":"赤芍","dosage":"6g","role":"佐"},{"herb":"牛膝","dosage":"9g","role":"佐"},{"herb":"桔梗","dosage":"5g","role":"佐"},{"herb":"柴胡","dosage":"3g","role":"佐"},{"herb":"枳壳","dosage":"6g","role":"佐"},{"herb":"甘草","dosage":"6g","role":"使"}]', '活血化瘀，行气止痛', '胸中血瘀证。胸痛，头痛，日久不愈，痛如针刺而有定处，或呃逆日久不止，或饮水即呛，干呕，或内热瞀闷，或心悸怔忡，失眠多梦，急躁易怒，入暮潮热，唇暗或两目暗黑，舌质暗红，或舌有瘀斑、瘀点，脉涩或弦紧。', 1, 0.92),
('二陈汤', '祛痰剂', '[{"herb":"半夏","dosage":"15g","role":"君"},{"herb":"陈皮","dosage":"15g","role":"臣"},{"herb":"茯苓","dosage":"9g","role":"佐"},{"herb":"甘草","dosage":"5g","role":"使"}]', '燥湿化痰，理气和中', '湿痰证。咳嗽痰多，色白易咯，恶心呕吐，胸膈痞闷，肢体困重，或头眩心悸，舌苔白滑或腻，脉滑。', 1, 0.93);

-- 初始化证型数据
INSERT INTO syndromes (name, category, main_symptoms, pulse, treatment_principle) VALUES
('气虚证', '气血津液辨证', '神疲乏力，少气懒言，自汗，活动后加重', '虚脉、细脉、弱脉', '益气补虚'),
('血虚证', '气血津液辨证', '面色淡白或萎黄，唇甲色淡，头晕眼花，心悸多梦', '细脉、涩脉', '补血养血'),
('阴虚证', '气血津液辨证', '五心烦热，潮热盗汗，口干咽燥，舌红少苔', '细数脉', '滋阴降火'),
('阳虚证', '气血津液辨证', '畏寒肢冷，面色㿠白，神疲乏力，小便清长', '沉脉、细脉、迟脉、弱脉', '温阳补虚'),
('气滞证', '气血津液辨证', '胸胁胀痛，情志不畅，善太息，痛无定处', '弦脉、涩脉', '行气解郁'),
('血瘀证', '气血津液辨证', '刺痛固定，拒按，入夜尤甚，舌质紫暗或有瘀斑', '涩脉、结脉、代脉', '活血化瘀'),
('痰湿证', '气血津液辨证', '胸闷痰多，身体困重，纳呆便溏，苔腻', '滑脉、濡脉、缓脉', '燥湿化痰'),
('肝郁气滞', '脏腑辨证', '情志抑郁，胸胁胀痛，月经不调，乳房胀痛', '弦脉', '疏肝解郁'),
('肝阳上亢', '脏腑辨证', '头痛眩晕，面红目赤，急躁易怒，失眠多梦', '弦细数脉', '平肝潜阳'),
('心火亢盛', '脏腑辨证', '心烦失眠，口舌生疮，小便短赤，舌尖红', '数脉、洪脉', '清心泻火'),
('脾胃虚弱', '脏腑辨证', '食少腹胀，大便溏薄，神疲乏力，面色萎黄', '缓脉、弱脉', '健脾和胃'),
('肾气不足', '脏腑辨证', '腰膝酸软，耳鸣健忘，遗精早泄，小便频数', '沉脉、细脉、弱脉', '补肾益气');
