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
 * 这里面打印两次异常，这两次异常是中断异常，要看清楚，其余逻辑跟FuturesAllAsListEx_1里的分析是一样的。
 * 我一直在找是哪里出现中断异常的，之前猜想是第一个执行完成后会抛出异常，然后设置AggregateFuture的异常，然后在AggregateFuture.afterDone中中断其他输入future，（流程见：
 * handleOneInputDone -> handleException -> setException -> complete -> AggregateFuture.afterDone），后来发现，AggregateFuture.afterDone中的wasInterrupted是false，且
 * localRunningState.interruptTask()的实现根本就是空的。
 *
 * 后来仔细想想，其实是我的aggregateFuture.addListener里使用了“executorService.shutdownNow();”的原因，在上面的流程中，complete -> 处理监听器，在关闭线程池时是断了睡眠的线程，
 * 才导致出现的中断异常。这里也可以看到shutdownNow的真正作用，它并不是立刻停止线程，而是等待线程执行结束，
 * 所以后面出来了"Got more than one input Future failure. Logging failures after the first"的记录。
 *
 * JAVA线程池shutdown和shutdownNow的区别：http://justsee.iteye.com/blog/999189
 *
 * 这个就引出了一个问题：那我的aggregateFuture里的listener要不要关闭线程池呢？如果要关闭，也合理，即有输入future失败了，那么关闭所有输入future，也是合理的。
 */
public class FuturesAllAsListEx_3 {

    public static void main(String[] args) {
        Exception e = new MyExceptionForEx("test");
        ExecutorService executorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(6));
        ListenableFuture<Integer> listenableFuture1 = (ListenableFuture<Integer>)executorService.submit(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                throw e;
            }
        });
        ListenableFuture<Integer> listenableFuture2 = (ListenableFuture<Integer>)executorService.submit(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                Thread.sleep(5 * 1000);
                System.out.println("5");
                throw e;
            }
        });
        ListenableFuture<Integer> listenableFuture3 = (ListenableFuture<Integer>)executorService.submit(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                Thread.sleep(2 * 1000);
                System.out.println("2");
                throw e;
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
                executorService.shutdownNow(); // 会导致其他两个睡眠的future产生中断异常，见最上面的分析。
            }
        }, executorService);

        System.out.println("main is done.");
    }
}
