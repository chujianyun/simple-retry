package com.chujianyun.simpleretry;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;

/**
 * 操作封装
 *
 * @author: 明明如月 liuwangyanghudu@163.com
 * @date: 2019-04-05 02:09
 */
@Slf4j
public class SimpleRetryUtil {


    /**
     * 带重试和延时的操作执行
     *
     * @param operation   执行的操作
     * @param retryPolicy 重试策略
     * @return 返回值
     * @throws Exception 业务异常或者超过最大重试次数后的最后一次尝试抛出的异常
     */
    public static <T> T executeWithRetry(Operation<T> operation, RetryPolicy<T> retryPolicy) throws Exception {

        // 最大重试次数
        Integer maxRetries = retryPolicy.getMaxRetries();

        if (maxRetries != null && maxRetries < 0) {
            throw new IllegalArgumentException("最大重试次数不能为负数");
        }

        int retryCount = 0;
        Duration delayDuration = retryPolicy.getDelayDuration();

        while (true) {
            try {
                T result = operation.execute();

                // 不设置终止条件或者设置了且满足则返回，否则还会重试
                List<Predicate<T>> abortConditions = retryPolicy.getAbortConditions();
                /* ---------------- 不需要重试的返回值 -------------- */
                if (isInCondition(result, abortConditions)) {
                    return result;
                }

                /* ---------------- 需要重试的返回值 -------------- */
                boolean hasNext = doRetryAndHasNext(++retryCount, maxRetries, delayDuration);
                if (!hasNext) {
                    return result;
                }
            } catch (Exception e) {
                /* ---------------- 不需要重试的异常 -------------- */
                List<Class<? extends Exception>> abortExceptions = retryPolicy.getAbortExceptions();
                if (isInExceptions(e, abortExceptions)) {
                    throw e;
                }
                log.debug("SimpleRetryUtil#executeWithRetry", e);
                /* ---------------- 需要重试的异常 -------------- */
                boolean hasNext = doRetryAndHasNext(++retryCount, maxRetries, delayDuration);
                if (!hasNext) {
                    throw e;
                }

            }
        }
    }

    private static boolean doRetryAndHasNext(int retryCount, Integer maxRetries, Duration delayDuration) {
        // 有限次重试
        if (maxRetries != null) {
            if (retryCount > maxRetries) {
                return false;
            }
        }

        // 延时
        if (!delayDuration.isNegative()) {
            try {
                log.debug("延时{}毫秒", delayDuration.toMillis());
                Thread.sleep(delayDuration.toMillis());
            } catch (InterruptedException ex) {
                //ignore
            }
        }
        log.debug("第{}次重试", retryCount);
        return true;
    }


    /**
     * 是否在异常列表中
     */
    private static boolean isInExceptions(Exception e, List<Class<? extends Exception>> abortExceptions) {
        if (CollectionUtils.isEmpty(abortExceptions)) {
            return false;
        }
        for (Class<? extends Exception> clazz : abortExceptions) {
            if (clazz.isAssignableFrom(e.getClass())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 是否符合不需要终止的条件
     */
    private static <T> boolean isInCondition(T result, List<Predicate<T>> abortConditions) {
        if (CollectionUtils.isEmpty(abortConditions)) {
            return true;
        }

        for (Predicate<T> predicate : abortConditions) {
            if (predicate.test(result)) {
                return true;
            }
        }
        return false;
    }

}
