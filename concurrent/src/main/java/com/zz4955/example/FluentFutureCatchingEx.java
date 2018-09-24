package com.zz4955.example;

import com.zz4955.concurrent.FluentFuture;
import com.zz4955.concurrent.ListenableFuture;
import com.zz4955.concurrent.MoreExecutors;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

public class FluentFutureCatchingEx {

    public static void main(String[] args) throws InterruptedException {
        ExecutorService executorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(5));
        ListenableFuture<Integer> listenableFuture1 = (ListenableFuture<Integer>)executorService.submit(new Callable<Integer>() {
                @Override
                public Integer call() throws Exception {
                    Thread.sleep(2 * 1000);
                    throw new InterruptedException("test");
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
                .catching(
                        InterruptedException.class,
                        new Function<InterruptedException, Integer>() {
                            @Override
                            public Integer apply(InterruptedException e) {
                                System.out.println(e.getMessage());
                                return 1;
                            }
                        },
                        executorService);
        listenableFuture2.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    System.out.println(listenableFuture2.get());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }
            }
        }, executorService);
        System.out.println("main is done.");
        Thread.sleep(5 * 1000);
        executorService.shutdownNow();
    }
}
