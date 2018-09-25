package com.zz4955.concurrent;

public interface FutureCallback<V> {

    void onSuccess(V result);
    void onFailure(Throwable t);
}
