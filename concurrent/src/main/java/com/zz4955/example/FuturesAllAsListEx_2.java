package com.zz4955.example;

import com.zz4955.concurrent.Futures;
import com.zz4955.concurrent.ListenableFuture;
import com.zz4955.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 有两个第一次，如果某个异常出现多次，则只有第一次会记录（记录在seenExceptions里），然后是如果有多个future出现第一次异常记录，则打印日志。
 * 在这个例子中，三个future都出现异常了，这三个异常是不同的，三次都会记录在seenExceptions里，所以三个future存在第一次记录异常，只打印后两次
 * "Got more than one input Future failure. Logging failures after the first"的记录。
 * 具体代码见RunningState.init方法。
 */
public class FuturesAllAsListEx_2 {

    public static void main(String[] args) {
        ExecutorService executorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(6));
        ListenableFuture<Integer> listenableFuture1 = (ListenableFuture<Integer>)executorService.submit(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                throw new MyExceptionForEx("test");
            }
        });
        ListenableFuture<Integer> listenableFuture2 = (ListenableFuture<Integer>)executorService.submit(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                throw new MyExceptionForEx("test");
            }
        });
        ListenableFuture<Integer> listenableFuture3 = (ListenableFuture<Integer>)executorService.submit(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
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
