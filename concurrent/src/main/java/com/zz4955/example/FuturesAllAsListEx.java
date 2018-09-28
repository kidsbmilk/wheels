package com.zz4955.example;

import com.zz4955.concurrent.FluentFuture;
import com.zz4955.concurrent.Futures;
import com.zz4955.concurrent.ListenableFuture;
import com.zz4955.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FuturesAllAsListEx {

    public static void main(String[] args) {
        ExecutorService executorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(6));
        ListenableFuture<Integer> listenableFuture1 = (ListenableFuture<Integer>)executorService.submit(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
//                return 1;
                throw new MyExceptionForEx("test");
            }
        });
        ListenableFuture<Integer> listenableFuture2 = (ListenableFuture<Integer>)executorService.submit(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
//                return 2;
                throw new MyExceptionForEx("test");
            }
        });
        ListenableFuture<Integer> listenableFuture3 = (ListenableFuture<Integer>)executorService.submit(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
//                return 2;
                throw new MyExceptionForEx("test");
            }
        });
//        ListenableFuture<List<Integer>> listListenableFuture = Futures.allAsList(listenableFuture1, listenableFuture2);
        List<ListenableFuture<Integer>> listenableFutureList = new ArrayList<>();
        listenableFutureList.add(listenableFuture1);
        listenableFutureList.add(listenableFuture2);
        listenableFutureList.add(listenableFuture3);
        ListenableFuture<List<Integer>> listListenableFuture = Futures.allAsList(listenableFutureList);

        listListenableFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    System.out.println(listListenableFuture.get());
                } catch (InterruptedException e) {
//                    e.printStackTrace();
                    System.out.println(e.getMessage());
                } catch (ExecutionException e) {
//                    e.printStackTrace();
                    System.out.println(e.getMessage());
                }
                executorService.shutdownNow();
            }
        }, executorService);

        System.out.println("main is done.");
    }
}
