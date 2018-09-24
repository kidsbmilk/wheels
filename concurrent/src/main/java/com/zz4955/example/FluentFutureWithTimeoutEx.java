package com.zz4955.example;

import com.zz4955.concurrent.FluentFuture;
import com.zz4955.concurrent.ListenableFuture;
import com.zz4955.concurrent.MoreExecutors;

import java.util.concurrent.*;

public class FluentFutureWithTimeoutEx {

    public static void main(String[] args) {
        ExecutorService executorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(5));
        ListenableFuture<Integer> listenableFuture1 = (ListenableFuture<Integer>) executorService.submit(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                System.out.println(System.currentTimeMillis());
                Thread.sleep(5 * 1000);
                return 1;
            }
        });
        ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(2);
        ListenableFuture<Integer> listenableFuture2 = FluentFuture.from(listenableFuture1)
                .withTimeout(2 * 1000, TimeUnit.MILLISECONDS, scheduledExecutor);

        listenableFuture2.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    System.out.println(listenableFuture2.get());
                } catch (InterruptedException e) {
//                    e.printStackTrace();
                } catch (ExecutionException e) {
//                    e.printStackTrace();
                }
                System.out.println(System.currentTimeMillis());
                executorService.shutdownNow();
                scheduledExecutor.shutdownNow();
            }
        }, executorService);

        System.out.println("main is done.");
    }
}
