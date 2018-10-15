package com.cx.coffeehi.spider.bean;

import lombok.Data;

@Data
public class Author {
    private String id;
    private Byte gender;
    private String name;
    private Long follower_count;
}
