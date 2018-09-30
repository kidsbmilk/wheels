package com.zz4955.example;

import com.zz4955.concurrent.Futures;
import com.zz4955.concurrent.ListenableFuture;
import com.zz4955.concurrent.MoreExecutors;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FuturesGetCheckedEx {

    public static void main(String[] args) {
        ExecutorService executorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(6));
        ListenableFuture<Integer> listenableFuture1 = (ListenableFuture<Integer>) executorService.submit(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                throw new MyExceptionForEx("test");
            }
        });

        try {
            Futures.getChecked(listenableFuture1, InterruptedException.class);
        } catch (InterruptedException e) {
            System.out.println(e.getMessage());
        }

        System.out.println("main is done.");
        executorService.shutdownNow();
    }
}
