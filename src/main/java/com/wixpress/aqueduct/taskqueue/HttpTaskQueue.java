package com.wixpress.aqueduct.taskqueue;

import com.wixpress.aqueduct.httpclient.HttpClient;
import com.wixpress.aqueduct.task.HttpTask;
import com.wixpress.aqueduct.task.HttpTaskFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;

import static com.wixpress.aqueduct.task.HttpConstants.HttpVerb;
import static java.lang.String.format;

/**
 * Created by IntelliJ IDEA.
 * User: evg
 * Date: 09/11/11
 * Time: 16:34
 */
public class HttpTaskQueue
{
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpTaskQueue.class);

    private TaskStorage taskStorage;
    private ManualResetEvent newTaskEvent = new ManualResetEvent();
    private HttpClient httpClient;

    private HttpTaskQueueThread taskQueueThread;
    private HttpTaskResultListener resultListener;
    private boolean notifyIfFailed = false;

    public HttpTaskQueue(String appID){
        this(appID, new DefaultTaskMarshaller());
//        this(appID, new HttpTask.DefaultMarshaler());
    }
    
    public HttpTaskQueue(String appID, TaskMarshaller taskMarshaller) {
        this.taskStorage = new TaskStorage(appID.concat(".db"), taskMarshaller);
        httpClient = new HttpClient(new TaskCompletedListener());

        taskQueueThread = new HttpTaskQueueThread(taskStorage, newTaskEvent, httpClient);
        Executors.newSingleThreadExecutor().submit(taskQueueThread);

        // need shutdown hook in order to terminate all working threads properly
        Runtime.getRuntime().addShutdownHook(new ShutdownHook());
    }

    public void queue(HttpTask task) throws Exception {

        taskStorage.addTask(task.sanitize());
        
        // Notify worker thread about new task 
        newTaskEvent.signal();
    }
    
    public HttpTask createGetTask(String url) throws Exception {
        return HttpTaskFactory.create(HttpVerb.GET, url, true);
    }

    public HttpTask createPostTask(String url) throws Exception {
        return HttpTaskFactory.create(HttpVerb.POST, url, true);
    }

    public HttpTask createPutTask(String url) throws Exception {
        return HttpTaskFactory.create(HttpVerb.PUT, url, true);
    }

    public HttpTask createDeleteTask(String url) throws Exception {
        return HttpTaskFactory.create(HttpVerb.DELETE, url, true);
    }

    public void purgeTasks() {
        try {
            taskStorage.purge();
        } catch (Exception e) {
        }
    }
    
    public List<HttpTask> getPendingTasks(){
        try {
            return taskStorage.getPendingTasks();
        } catch (Exception e) {
            
        }
        return null;
    }
    
    public List<HttpTask> getActiveTasks(){
        try {
            return taskStorage.getActiveTasks();
        } catch (Exception e) {
            
        }
        
        return null;
    }

    public void shutdown() {
        taskQueueThread.stop();
    }

    public void addListener(HttpTaskResultListener resultListener) {
        addListener(resultListener, false);
    }

    public void addListener(HttpTaskResultListener resultListener, boolean notifyIfFailed) {
        this.notifyIfFailed = notifyIfFailed;
        this.resultListener = resultListener;
    }

    private class TaskCompletedListener implements HttpTaskResultListener {

        public void taskComplete(HttpTask task) {
            LOGGER.debug(format("Task (%d) for %s completed with status %d",
                    task.getTaskID(), task.getUri().toASCIIString(), task.lastResult().getStatus()));
            try {
                if(task.isSuccess()){
                    taskStorage.deleteTask(task);
                } else {
                    task.triedOnce();
                    if(task.getMaxRetries() <= task.getRetryCount()){
                        LOGGER.debug(format("Task (%d) exceeded max retry count, giving up...", task.getTaskID()));
                        taskStorage.giveUpTask(task);
                    } else {
                        LOGGER.debug(format("Task (%d) failed, queueing for retry (%d of %d)...",
                                                    task.getTaskID(), task.getRetryCount(), task.getMaxRetries()));
                        taskStorage.saveTask(task);
                    }
                }

                if (null != resultListener) {
                    if (task.isSuccess() || notifyIfFailed) {
                        LOGGER.debug("Notifying listener...");
                        resultListener.taskComplete(task);
                    }
                }
            } catch (Exception e) {
                LOGGER.error(format("Error completing task (%d)", task.getTaskID()), e);
            }
        }
    }

    private class ShutdownHook extends Thread {
        @Override
        public void run() {
            shutdown();
        }
    }
}
