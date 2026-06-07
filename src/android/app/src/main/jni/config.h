// Copyright 2014 Citra Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

#pragma once

#include <memory>
#include <string>

class INIReader;

class Config {
private:
    std::unique_ptr<INIReader> sdl2_config;
    std::string sdl2_config_loc;

    bool LoadINI(const std::string& default_contents = "", bool retry = true);
    void ReadValues();
    void UpdateCFG();

public:
    Config();
    ~Config();

    void Reload();

    // 补充: 如果 Config 是在 SetUserDirectory 之前实例化的,这里可以重新
    // 读取最新的 ConfigDir 并重读 ini。补丁后的 Java 调用顺序理论上
    // 不需要走这条路径,但保留作为防御。
    void ReinitAfterSetUserPath();
};
