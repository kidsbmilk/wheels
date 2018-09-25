package com.zz4955.example;

import com.zz4955.concurrent.FluentFuture;
import com.zz4955.concurrent.ListenableFuture;
import com.zz4955.concurrent.MoreExecutors;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FluentFutureTransformAsyncEx {

    public static void main(String[] args) {
        ExecutorService executorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(5));
        ListenableFuture<Integer> listenableFuture1 = (ListenableFuture<Integer>) executorService.submit(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                System.out.println(System.currentTimeMillis());
                System.out.println("test in listenablefuture1");
                return 1;
            }
        });
        ListenableFuture<String> listenableFuture2 = FluentFuture.from(listenableFuture1)
                .transformAsync(i -> {
                        return (ListenableFuture<String>) executorService.submit(new Callable<String>() {
                            @Override
                            public String call() throws Exception {
                                Thread.sleep(2 * 1000);
                                return "aa";
                            }
                        });
                    }, executorService);
        listenableFuture2.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    System.out.println(System.currentTimeMillis());
                    System.out.println(listenableFuture2.get());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }
                executorService.shutdownNow();
            }
        }, executorService);
        System.out.println(System.currentTimeMillis());
        System.out.println("main is done.");
    }
}
