package com.abissell.fixbridge;

import com.abissell.javautil.rusty.ErrType;
import com.abissell.javautil.rusty.Result;

public interface FixSessionBridge<R, E extends ErrType<E>> {
    Result<R, E> start();
    Result<R, E> sendResendRequest(int begin, int end);
    void stop();
    void stop(boolean forceDisconnect);
    boolean stopped();
    boolean loggedOn();
    int queueSize();
}
