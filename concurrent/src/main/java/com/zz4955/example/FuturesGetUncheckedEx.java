package com.zz4955.example;

import com.zz4955.concurrent.Futures;
import com.zz4955.concurrent.ListenableFuture;
import com.zz4955.concurrent.MoreExecutors;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FuturesGetUncheckedEx {

    public static void main(String[] args) {
        ExecutorService executorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(6));
        ListenableFuture<Integer> listenableFuture1 = (ListenableFuture<Integer>)executorService.submit(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                throw new Error("test");
            }
        });
        Futures.getUnchecked(listenableFuture1); // 见这个方法的注释说明，用于已知不会返回受检测异常的future中。
        // 如果真的在受检异常的上面使用此方法，异常会被封装在UncheckedExecutionException中抛出。
        System.out.println("main is done.");
        executorService.shutdownNow();
    }
}
