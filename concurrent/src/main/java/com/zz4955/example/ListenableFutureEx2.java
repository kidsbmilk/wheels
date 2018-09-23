package com.zz4955.example;

import com.zz4955.concurrent.ListenableFuture;
import com.zz4955.concurrent.ListeningExecutorService;
import com.zz4955.concurrent.MoreExecutors;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ListenableFutureEx2 {

    public static void main(String[] args) {
        ExecutorService executorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(5));
        ListenableFuture<Integer> listenableFuture1 = (ListenableFuture<Integer>) executorService.submit(
                new Callable<Integer>() {
                    @Override
                    public Integer call() throws Exception {
                        Thread.sleep(5 * 1000);
                        System.out.println("future 1 call done");
                        return 1;
                    }
                }
        );
        ListenableFuture<Integer> listenableFuture2 = (ListenableFuture<Integer>) executorService.submit(
                new Callable<Integer>() {
                    @Override
                    public Integer call() throws Exception {
                        System.out.println("future 2 call done");
                        return 2;
                    }
                }
        );
        listenableFuture2.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    System.out.println(listenableFuture1.get());
                    System.out.println(listenableFuture2.get());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }
                System.out.println("future 2 listener done.");
                executorService.shutdownNow();
            }
        }, executorService);

        System.out.println("main is done.");
    }
}
