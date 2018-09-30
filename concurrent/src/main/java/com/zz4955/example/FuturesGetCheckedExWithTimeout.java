package com.zz4955.example;

import com.zz4955.concurrent.Futures;
import com.zz4955.concurrent.ListenableFuture;
import com.zz4955.concurrent.MoreExecutors;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class FuturesGetCheckedExWithTimeout {

    public static void main(String[] args) {
        ExecutorService executorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(6));
        ListenableFuture<Integer> listenableFuture1 = (ListenableFuture<Integer>) executorService.submit(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                Thread.sleep(3 * 1000);
                throw new InterruptedException("test");
            }
        });

        try {
            Futures.getChecked(listenableFuture1, MyExceptionForEx.class, 2L, TimeUnit.SECONDS); // 可以对比一下2s与5s时的异常信息。
        } catch (MyExceptionForEx e) {
            System.out.println(e.getMessage());
        }

        System.out.println("main is done.");
        executorService.shutdownNow();
    }
}
