package com.zz4955.test;

import com.zz4955.concurrent.ListenableFuture;
import com.zz4955.concurrent.MoreExecutors;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ListenableFutureTest {

    public static void main(String[] args) {
        ExecutorService executorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(5));
        ListenableFuture<String> listenableFuture = (ListenableFuture<String>) executorService.submit(
                new Callable<String>() {
                    @Override
                    public String call() throws Exception {
                        Thread.sleep(5 * 1000);
                        return "task success";
                    }
                }
        );
        listenableFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    System.out.println(listenableFuture.get());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }
                System.out.println("run success");
                executorService.shutdownNow();
            }
        }, executorService);
        System.out.println("main is done.");
    }
}
