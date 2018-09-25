package com.zz4955.example;

import com.zz4955.concurrent.FluentFuture;
import com.zz4955.concurrent.ListenableFuture;
import com.zz4955.concurrent.MoreExecutors;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FluentFutureTransformEx {

    public static void main(String[] args) {
        ExecutorService executorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(5));
        ListenableFuture<Integer> listenableFuture1 = (ListenableFuture<Integer>) executorService.submit(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                return 1;
            }
        });
        ListenableFuture<Object> listenableFuture2 = FluentFuture.from(listenableFuture1)
                .transform(i -> "aa", executorService)
                .transform(s -> 66, executorService); // 如果将返回的类型里的参数类型Object改为String，则这个转换会编译出错。
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
                executorService.shutdownNow();
            }
        }, executorService);
        System.out.println("main is done.");
    }
}
