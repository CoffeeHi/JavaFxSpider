package com.cx.coffeehi.spider.utils;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import lombok.extern.log4j.Log4j;
@Log4j
public class SpiderThread {
    private static ScheduledExecutorService scheduleService;
    private static ThreadPoolExecutor mainTaskExecutor;
    private static ThreadPoolExecutor answerTaskExecutor;
    private static ThreadPoolExecutor picTaskExecutor;
    
    private SpiderThread() {
        UncaughtExceptionHandler scheduleUnExceHandler = new UncaughtExceptionHandler() {
            public void uncaughtException(Thread thread, Throwable throwable) {
                log.error("scheduleTaskExecutor error : ", throwable);
            }
        };
        ThreadFactory scheduleThreadFactory = new ThreadFactoryBuilder().setDaemon(true).setNameFormat("schedule-task-t%d")
            .setUncaughtExceptionHandler(scheduleUnExceHandler).build();
        scheduleService = Executors.newSingleThreadScheduledExecutor(scheduleThreadFactory);
        
        UncaughtExceptionHandler mainUnExceHandler = new UncaughtExceptionHandler() {
            public void uncaughtException(Thread thread, Throwable throwable) {
                log.error("mainTaskExecutor error : ", throwable);
            }
        };
        BlockingQueue<Runnable> mainQueue = new LinkedBlockingQueue<Runnable>(1);
        ThreadFactory mainThreadFactory = new ThreadFactoryBuilder().setDaemon(true).setNameFormat("main-task-t%d")
            .setUncaughtExceptionHandler(mainUnExceHandler).build();
        mainTaskExecutor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.SECONDS, mainQueue, mainThreadFactory);
        
        UncaughtExceptionHandler ansUnExceHandler = new UncaughtExceptionHandler() {
            public void uncaughtException(Thread thread, Throwable throwable) {
                log.error("ansUnExceHandler error : ", throwable);
            }
        };
        BlockingQueue<Runnable> answerQueue = new LinkedBlockingQueue<Runnable>(2000);
        ThreadFactory ansThreadFactory = new ThreadFactoryBuilder().setDaemon(true).setNameFormat("answer-task-t%d")
            .setUncaughtExceptionHandler(ansUnExceHandler).build();
        answerTaskExecutor = new ThreadPoolExecutor(50, 50, 0L, TimeUnit.SECONDS, answerQueue, ansThreadFactory);
        
        UncaughtExceptionHandler picUnExceHandler = new UncaughtExceptionHandler() {
            public void uncaughtException(Thread thread, Throwable throwable) {
                log.error("picTaskExecutor error : ", throwable);
            }
        };
        BlockingQueue<Runnable> picQueue = new LinkedBlockingQueue<Runnable>(2000);
        ThreadFactory picThreadFactory = new ThreadFactoryBuilder().setDaemon(true).setNameFormat("pic-task-t%d")
            .setUncaughtExceptionHandler(picUnExceHandler).build();
        picTaskExecutor = new ThreadPoolExecutor(50, 50, 0L, TimeUnit.SECONDS, picQueue, picThreadFactory);
    }
    
    private volatile static SpiderThread instance =  null;
    
    public static SpiderThread getInstance() {
        if (instance == null) {
            synchronized (SpiderThread.class) {
                if (instance == null) {
                    instance = new SpiderThread();
                }
            }
        }
        return instance;
    }
    
    public void mainTaskSubmit(Runnable task) {
        mainTaskExecutor.execute(task);
    }
    
    public void ansTaskSubmit(Runnable task) {
        answerTaskExecutor.execute(task);
    }
    
    public void picTaskSubmit(Runnable task) {
        picTaskExecutor.execute(task);
    }
    
    public void scheduleTaskSubmit(Runnable task) {
      scheduleService.scheduleAtFixedRate(task, 0, 100, TimeUnit.MILLISECONDS);
    }

    public void shutdown() {
        if (answerTaskExecutor != null) {
            answerTaskExecutor.shutdown();
        }
        if (picTaskExecutor != null) {
            picTaskExecutor.shutdown();
        }
        if (mainTaskExecutor != null) {
            mainTaskExecutor.shutdown();
        }
        if (scheduleService != null) {
            scheduleService.shutdown();
        }
    }

    public void close() {
        if (answerTaskExecutor != null) {
            answerTaskExecutor.getQueue().clear();
        }
        if (picTaskExecutor != null) {
            picTaskExecutor.getQueue().clear();
        }
        if (mainTaskExecutor != null) {
            mainTaskExecutor.getQueue().clear();
        }
        if (scheduleService != null) {
            scheduleService.shutdown();
        }
    }
}
