package com.wixpress.aqueduct.httpclient;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpResponse;
import com.wixpress.aqueduct.task.HttpTask;
import com.wixpress.aqueduct.task.HttpTaskResult;

import java.util.List;
import java.util.Map;


/**
 * @author evg
 */

class HttpResponseHandler extends SimpleChannelUpstreamHandler {

    private HttpResponseCompletedListener responseCompletedListener;


    public HttpResponseHandler(HttpResponseCompletedListener responseCompletedListener) {
        this.responseCompletedListener = responseCompletedListener;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {

        HttpResponse response = (HttpResponse) e.getMessage();
        HttpTask task = (HttpTask) ctx.getAttachment();

        HttpTaskResult result = new HttpTaskResult();

        int responseStatus = response.getStatus().getCode();
        result.setStatus(responseStatus);

        for (int successCode : task.getSuccessResponseCodes()) {
            if ((successCode == responseStatus) || (responseStatus >= 200 && responseStatus < 300))
                task.setSuccess(true);
        }

       copyHeaders(response.getHeaders(), result);

        ChannelBuffer content = response.getContent();
        if (content.readable()) {
            byte[] resultContent = new byte[content.capacity()];
            content.getBytes(0, resultContent);
            result.setContent(resultContent);
        }

        task.addResult(result);
        responseCompletedListener.responseCompleted(task, ctx.getChannel());
    }

    public void exceptionCaught(
            ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {

        HttpTask task = (HttpTask) ctx.getAttachment();
        if (null != task) {
            task.setSuccess(false);

            HttpTaskResult result = new HttpTaskResult();
            result.setCause(e.getCause());
            task.addResult(result);

            responseCompletedListener.responseCompleted(task, ctx.getChannel());
        }
    }

    private void copyHeaders(List<Map.Entry<String,String>> headers, HttpTaskResult result){

        if(null == headers || headers.isEmpty()) return;

        for(Map.Entry<String, String> entry : headers){
            result.getHeaders().addHeader(entry.getKey(), entry.getValue());
        }
    }
}

