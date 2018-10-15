package com.cx.coffeehi.spider.bean;

import lombok.Data;

@Data
public class SpiderContext {
    public volatile static boolean isRunning;
    private String savePath;
    private boolean ifCheckVideo;
    private boolean ifCheckPic;
    private String questionId;
}
