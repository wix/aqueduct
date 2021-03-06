package com.wixpress.aqueduct.taskqueue;

import com.wixpress.aqueduct.task.HttpTask;

/**
 * Created by evg.
 * Date: 18/01/12
 * Time: 00:33
 */
public interface TaskMarshaller {
    public String marshal(HttpTask task) throws Exception;
    public HttpTask unmarshal(String json) throws Exception;
}
