package com.cx.coffeehi.spider.bean;

import lombok.ToString;

@lombok.Data
@ToString
public class Data {
    private String id;
    private Author author;
    private String content;
    private Long updated_time;
    private Long created_time;
    private Long voteup_count;
    private Long comment_count;
    private Boolean admin_closed_comment;
}
