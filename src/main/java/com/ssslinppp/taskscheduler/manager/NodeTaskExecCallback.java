package com.ssslinppp.taskscheduler.manager;

import com.google.common.util.concurrent.FutureCallback;
import com.ssslinppp.taskscheduler.model.NodeTask;
import com.ssslinppp.taskscheduler.model.NodeTaskResult;
import com.ssslinppp.taskscheduler.model.NodeTaskStatus;
import com.ssslinppp.taskscheduler.model.ParentTask;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Desc:
 * <p>
 * User: liulin ,Date: 2018/3/28 , Time: 9:35 <br/>
 * Email: liulin@cmss.chinamobile.com <br/>
 * To change this template use File | Settings | File Templates.
 */
@Getter
@Setter
public class NodeTaskExecCallback implements FutureCallback<NodeTaskResult> {
    private static Logger logger = LoggerFactory.getLogger(NodeTaskExecCallback.class);

    private String parentTaskId;
    private String nodeTaskId;

    public NodeTaskExecCallback(String parentTaskId, String nodeTaskId) {
        this.parentTaskId = parentTaskId;
        this.nodeTaskId = nodeTaskId;
    }

    @Override
    public void onSuccess(NodeTaskResult result) {
        try {
            if (!TaskManager.instance.updateNodeTaskStatus(parentTaskId, nodeTaskId, NodeTaskStatus.success)) {//更新失败
                logger.warn("[OnSuccess] parentTask has finish [or] any nodeTask exception,parentTaskId: {}", parentTaskId);
                return;
            }

            // 添加执行结果到 BlockingQueue
            TaskExecutor.instance.addNodeTaskResultToTail(parentTaskId, result);

            //触发任务状态监听器
            ParentTask parentTask = TaskManager.instance.getParentTask(parentTaskId);
            if (parentTask == null) {
                logger.warn("[XXX] parentTask has finish [or] any nodeTask exception,parentTaskId: {}", parentTaskId);
                return;
            }
            if (parentTask.getTaskStatusListener() != null) {
                NodeTask nodeTask = parentTask.getNodeTask(nodeTaskId);
                parentTask.getTaskStatusListener().process(parentTask.progress(), nodeTask, result);
            }

            // 判断parentTask是否执行结束
            if (TaskManager.instance.isParentTaskFailOrFinish(parentTaskId)) {
                TaskScheduleManager.instance.cancelParentTskSchedule(parentTaskId);
            }
        } catch (Exception e) {
            logger.error("NodeTask onSuccess fail, parentTaskId:" + parentTaskId);
            TaskScheduleManager.instance.cancelParentTskSchedule(parentTaskId);
        }
    }

    @Override
    public void onFailure(Throwable t) {//TODO: 考虑抛出异常
        logger.error("nodeTask exec fail , parentTaskId: {}, nodeTaskId: {}, errorMsg: {}", parentTaskId, nodeTaskId, t.getMessage());
        TaskManager.instance.updateNodeTaskStatus(parentTaskId, nodeTaskId, NodeTaskStatus.fail);
        TaskScheduleManager.instance.cancelParentTskSchedule(parentTaskId);
    }
}
