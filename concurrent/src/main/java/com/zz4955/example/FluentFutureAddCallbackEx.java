package com.zz4955.example;

import com.zz4955.concurrent.FluentFuture;
import com.zz4955.concurrent.FutureCallback;
import com.zz4955.concurrent.ListenableFuture;
import com.zz4955.concurrent.MoreExecutors;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FluentFutureAddCallbackEx {

    public static void main(String[] args) {
        ExecutorService executorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(5)); // 这个后面可以换成下面的例子中的线程池：
        /* // example
        // 多线程编程学习五(线程池的创建)：https://www.cnblogs.com/jmcui/p/8017473.html
        ThreadFactory namedThreadFactory = new ThreadFactoryBuilder()
                .setNameFormat("demo-pool-%d").build();

        //Common Thread Pool
        ExecutorService pool = new ThreadPoolExecutor(5, 200,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>(1024), namedThreadFactory, new ThreadPoolExecutor.AbortPolicy());

        pool.execute(()-> System.out.println(Thread.currentThread().getName()));
        pool.shutdown();//gracefully shutdown
        */

        ListenableFuture<Integer> listenableFuture1 = (ListenableFuture<Integer>) executorService.submit(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                throw new MyExceptionForEx("test");
//                return 1;
            }
        });
        FluentFuture.from(listenableFuture1)
                .addCallback(new FutureCallback<Integer>() {
                    @Override
                    public void onSuccess(Integer result) {
                        System.out.println("onSuccess: " + result);
                        executorService.shutdownNow();
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        System.out.println("onFailure: " + t.getMessage());
                        executorService.shutdownNow();
                    }
                }, executorService);

        System.out.println("main is done.");
    }
}
