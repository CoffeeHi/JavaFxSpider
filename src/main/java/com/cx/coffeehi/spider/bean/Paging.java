package com.cx.coffeehi.spider.bean;

import lombok.Data;

@Data
public class Paging {
    private Boolean is_start;
    private Boolean is_end;
    private Long totals;
}
