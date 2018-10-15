package com.cx.coffeehi.spider.bean;

import java.util.List;

import lombok.ToString;


@lombok.Data
@ToString
public class Result {
    private List<Data> data;
    private Paging paging;
}
