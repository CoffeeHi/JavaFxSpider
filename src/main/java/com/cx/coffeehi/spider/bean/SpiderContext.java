package com.cx.coffeehi.spider.bean;

import lombok.Data;

@Data
/**
 * 上下文
 */
public class SpiderContext {
    private String savePath;
    private boolean ifCheckVideo;
    private boolean ifCheckPic;
    private String questionId;
}
