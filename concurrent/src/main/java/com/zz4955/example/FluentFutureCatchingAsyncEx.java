package com.zz4955.example;

import com.zz4955.concurrent.FluentFuture;
import com.zz4955.concurrent.ListenableFuture;
import com.zz4955.concurrent.MoreExecutors;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FluentFutureCatchingAsyncEx {

    public static void main(String[] args) {
        ExecutorService executorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(5));
        ListenableFuture<Integer> listenableFuture1 = (ListenableFuture<Integer>)executorService.submit(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                System.out.println(System.currentTimeMillis());
                Thread.sleep(2 * 1000);
                System.out.println("test in listenableFuture1 call");
                throw new MyExceptionForEx("test");
            }
        });
        listenableFuture1.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    System.out.println(listenableFuture1.get());
                } catch (InterruptedException e) {
//                    e.printStackTrace();
                } catch (ExecutionException e) {
//                    e.printStackTrace();
                }
                System.out.println("listenableFuture1 get InterruptedException");
            }
        }, executorService);
        ListenableFuture<Integer> listenableFuture2 = FluentFuture.from(listenableFuture1)
                .catchingAsync(
                        MyExceptionForEx.class,
                        e -> {
                            System.out.println(System.currentTimeMillis());
                            System.out.println(e.getMessage());
                            return (ListenableFuture<Integer>)executorService.submit(new Callable<Integer>() {
                                @Override
                                public Integer call() throws Exception {
                                    Thread.sleep(3 * 1000);
                                    System.out.println(System.currentTimeMillis());
                                    System.out.println("test in catchingAsync callback");
                                    return 2;
                                }
                            });
                        },
                        executorService
                );
        listenableFuture2.addListener(new Runnable() {
            @Override
            public void run() {
                System.out.println(System.currentTimeMillis());
                try {
                    System.out.println(listenableFuture2.get());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }
                System.out.println(System.currentTimeMillis());
                System.out.println("shutdown now");
                executorService.shutdownNow();
            }
        }, executorService);

        // 上面是异步的写法，下面是同步获取结果的方法。
        try {
            listenableFuture2.get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        System.out.println("get in main " + System.currentTimeMillis());
        System.out.println("main is done.");
    }
}
