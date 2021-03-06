/*
 * (c) Kitodo. Key to digital objects e. V. <contact@kitodo.org>
 *
 * This file is part of the Kitodo project.
 *
 * It is licensed under GNU General Public License version 3 or later.
 *
 * For the full copyright and license information, please read the
 * GPL3-License.txt file that was distributed with this source code.
 */

package org.kitodo.production.services.workflow;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kitodo.api.command.CommandResult;
import org.kitodo.api.dataeditor.rulesetmanagement.RulesetManagementInterface;
import org.kitodo.api.dataformat.Workpiece;
import org.kitodo.api.validation.State;
import org.kitodo.api.validation.ValidationResult;
import org.kitodo.config.ConfigCore;
import org.kitodo.config.enums.ParameterCore;
import org.kitodo.data.database.beans.Comment;
import org.kitodo.data.database.beans.Process;
import org.kitodo.data.database.beans.Task;
import org.kitodo.data.database.beans.User;
import org.kitodo.data.database.beans.WorkflowCondition;
import org.kitodo.data.database.enums.TaskEditType;
import org.kitodo.data.database.enums.TaskStatus;
import org.kitodo.data.database.enums.WorkflowConditionType;
import org.kitodo.data.database.exceptions.DAOException;
import org.kitodo.data.exceptions.DataException;
import org.kitodo.production.helper.Helper;
import org.kitodo.production.helper.WebDav;
import org.kitodo.production.helper.metadata.ImageHelper;
import org.kitodo.production.helper.tasks.TaskManager;
import org.kitodo.production.metadata.MetadataLock;
import org.kitodo.production.services.ServiceManager;
import org.kitodo.production.services.data.TaskService;
import org.kitodo.production.thread.TaskScriptThread;
import org.kitodo.production.workflow.KitodoNamespaceContext;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class WorkflowControllerService {

    private final MetadataLock metadataLock = new MetadataLock();
    private List<Task> automaticTasks;
    private List<Task> tasksToFinish;
    private boolean flagWait = false;
    private final ReentrantLock flagWaitLock = new ReentrantLock();
    private final WebDav webDav = new WebDav();
    private static final Logger logger = LogManager.getLogger(WorkflowControllerService.class);
    private TaskService taskService = ServiceManager.getTaskService();

    /**
     * Set Task status up.
     *
     * @param task
     *            to change status up
     */
    public void setTaskStatusUp(Task task) throws DataException, IOException {
        if (task.getProcessingStatus() != TaskStatus.DONE) {
            setProcessingStatusUp(task);
            task.setEditType(TaskEditType.ADMIN);
            if (task.getProcessingStatus() == TaskStatus.DONE) {
                close(task);
            } else {
                task.setProcessingTime(new Date());
                taskService.replaceProcessingUser(task, getCurrentUser());
            }
        }
    }

    /**
     * Change Task status down.
     *
     * @param task
     *            to change status down
     */
    public void setTaskStatusDown(Task task) {
        task.setEditType(TaskEditType.ADMIN);
        task.setProcessingTime(new Date());
        taskService.replaceProcessingUser(task, getCurrentUser());
        setProcessingStatusDown(task);
    }

    /**
     * Change Task status up for list of tasks assigned to given Process.
     *
     * @param process
     *            object
     */
    public void setTasksStatusUp(Process process) throws DataException, IOException {
        List<Task> tasks = new CopyOnWriteArrayList<>(process.getTasks());

        for (Task task : tasks) {
            setTaskStatusUp(task);
        }
    }

    /**
     * Change Task status down for list of tasks assigned to given Process.
     *
     * @param process
     *            object
     */
    public void setTasksStatusDown(Process process) throws DataException {
        List<Task> tasks = new CopyOnWriteArrayList<>(process.getTasks());
        Collections.reverse(tasks);

        for (Task task : tasks) {
            // TODO: check if this behaviour is correct
            if (process.getTasks().get(0) != task && task.getProcessingStatus() != TaskStatus.LOCKED) {
                setTaskStatusDown(task);
                taskService.save(task);
                break;
            }
        }
    }

    private boolean validateMetadata(Task task) throws IOException, DataException {
        URI metadataFileUri = ServiceManager.getProcessService().getMetadataFileUri(task.getProcess());
        Workpiece workpiece = ServiceManager.getMetsService().loadWorkpiece(metadataFileUri);
        RulesetManagementInterface ruleset = ServiceManager.getRulesetManagementService().getRulesetManagement();
        ruleset.load(new File(Paths.get(
                ConfigCore.getParameter(ParameterCore.DIR_RULESETS),
                task.getProcess().getRuleset().getFile()).toString()));
        ValidationResult validationResult = ServiceManager.getMetadataValidationService().validate(workpiece, ruleset);
        if (State.ERROR.equals(validationResult.getState())) {
            Helper.setErrorMessage(Helper.getTranslation("dataEditor.validation.state.error"));
            for (String message : validationResult.getResultMessages()) {
                Helper.setErrorMessage(message);
            }
        }
        return !validationResult.getState().equals(State.ERROR);
    }

    /**
     * Close method task called by user action.
     *
     * @param task
     *            object
     */
    public void closeTaskByUser(Task task) throws DataException, IOException {
        // if the result of the task is to be verified first, then if necessary,
        // cancel the completion
        if (task.isTypeCloseVerify()) {
            // metadata validation
            if (task.isTypeMetadata()
                    && ConfigCore.getBooleanParameterOrDefaultValue(ParameterCore.USE_META_DATA_VALIDATION)
                    && !validateMetadata(task)) {
                throw new DataException("Error on metadata validation!");
            }

            // image validation
            if (task.isTypeImagesWrite()) {
                ImageHelper mih = new ImageHelper();
                URI imageFolder = ServiceManager.getProcessService().getImagesOriginDirectory(false, task.getProcess());
                if (!mih.checkIfImagesValid(task.getProcess().getTitle(), imageFolder)) {
                    throw new DataException("Error on image validation!");
                }
            }
        }

        // unlock the process
        metadataLock.setFree(task.getProcess().getId());

        // if the result of the verification is ok, then continue, otherwise it
        // is not reached
        this.webDav.uploadFromHome(task.getProcess());
        task.setEditType(TaskEditType.MANUAL_SINGLE);
        close(task);
    }

    /**
     * Close task.
     *
     * @param task
     *            as Task object
     */
    public void close(Task task) throws DataException, IOException {
        task.setProcessingStatus(TaskStatus.DONE);
        task.setProcessingTime(new Date());
        User user = null;
        if (!task.isTypeAutomatic()) {
            user = getCurrentUser();
        }
        taskService.replaceProcessingUser(task, user);
        task.setProcessingEnd(new Date());

        taskService.save(task);

        automaticTasks = new ArrayList<>();
        tasksToFinish = new ArrayList<>();

        activateTasksForClosedTask(task);
    }

    /**
     * Taken from CurrentTaskForm.
     *
     * @param task
     *            object
     */
    public void assignTaskToUser(Task task) {
        this.flagWaitLock.lock();
        try {
            if (!this.flagWait) {
                this.flagWait = true;

                task.setProcessingStatus(TaskStatus.INWORK);
                task.setEditType(TaskEditType.MANUAL_SINGLE);
                task.setProcessingTime(new Date());
                taskService.replaceProcessingUser(task, getCurrentUser());
                if (Objects.isNull(task.getProcessingBegin())) {
                    task.setProcessingBegin(new Date());
                }

                Process process = task.getProcess();

                List<Task> concurrentTasks = getConcurrentTasksForClose(process.getTasks(), task);
                for (Task concurrentTask : concurrentTasks) {
                    concurrentTask.setProcessingStatus(TaskStatus.LOCKED);
                    taskService.save(concurrentTask);
                }

                updateProcessSortHelperStatus(process);

                // if it is an image task, then download the images into the
                // user home directory
                if (task.isTypeImagesRead() || task.isTypeImagesWrite()) {
                    downloadToHome(task);
                }
            } else {
                Helper.setErrorMessage("stepInWorkError");
            }
            this.flagWait = false;
        } catch (DataException e) {
            Helper.setErrorMessage("stepSaveError", logger, e);
        } finally {
            this.flagWaitLock.unlock();
        }
    }

    /**
     * Unassing user from task.
     *
     * @param task
     *            object
     */
    public void unassignTaskFromUser(Task task) throws DataException {
        this.webDav.uploadFromHome(task.getProcess());
        task.setProcessingStatus(TaskStatus.OPEN);
        taskService.replaceProcessingUser(task, null);
        // if we have a correction task here then never remove startdate
        if (task.isCorrection()) {
            task.setProcessingBegin(null);
        }
        task.setEditType(TaskEditType.MANUAL_SINGLE);
        task.setProcessingTime(new Date());

        taskService.save(task);

        // unlock the process
        metadataLock.setFree(task.getProcess().getId());

        updateProcessSortHelperStatus(task.getProcess());
    }

    /**
     * Unified method for report problem .
     *
     * @param comment as Comment object
     */
    public void reportProblem(Comment comment) throws DataException {
        Task currentTask = comment.getCurrentTask();
        this.webDav.uploadFromHome(getCurrentUser(), comment.getProcess());
        Date date = new Date();
        currentTask.setProcessingStatus(TaskStatus.LOCKED);
        currentTask.setEditType(TaskEditType.MANUAL_SINGLE);
        currentTask.setProcessingTime(date);
        taskService.replaceProcessingUser(currentTask, getCurrentUser());
        currentTask.setProcessingBegin(null);
        taskService.save(currentTask);

        Task correctionTask = comment.getCorrectionTask();
        correctionTask.setProcessingStatus(TaskStatus.OPEN);
        correctionTask.setProcessingEnd(null);
        correctionTask.setCorrection(true);
        taskService.save(correctionTask);

        closeTasksBetweenCurrentAndCorrectionTask(currentTask, correctionTask);
        updateProcessSortHelperStatus(currentTask.getProcess());
    }

    /**
     * Unified method for solve problem.
     *
     * @param comment
     *              as Comment object
     */
    public void solveProblem(Comment comment) throws DataException {
        Date date = new Date();
        Task currentTask = comment.getCorrectionTask();
        this.webDav.uploadFromHome(currentTask.getProcess());
        currentTask.setProcessingStatus(TaskStatus.DONE);
        currentTask.setProcessingEnd(date);
        currentTask.setEditType(TaskEditType.MANUAL_SINGLE);
        currentTask.setProcessingTime(date);
        taskService.replaceProcessingUser(currentTask, getCurrentUser());
        taskService.save(currentTask);
        Task correctionTask = comment.getCurrentTask();
        closeTasksBetweenCurrentAndCorrectionTask(currentTask, correctionTask, date);
        openTaskForProcessing(correctionTask);
        comment.setCorrected(Boolean.TRUE);
        comment.setCorrectionDate(date);
        try {
            ServiceManager.getCommentService().saveToDatabase(comment);
        } catch (DAOException e) {
            Helper.setErrorMessage("errorSaving", new Object[] {"comment"}, logger, e);
        }
    }

    /**
     * Set processing status up. This method adds double check of task status.
     *
     * @param task
     *            object
     */
    private void setProcessingStatusUp(Task task) {
        if (task.getProcessingStatus() != TaskStatus.DONE) {
            TaskStatus newTaskStatus = TaskStatus.getStatusFromValue(task.getProcessingStatus().getValue() + 1);
            task.setProcessingStatus(newTaskStatus);
        }
    }

    /**
     * Set processing status down. This method adds double check of task status.
     *
     * @param task
     *            object
     */
    private void setProcessingStatusDown(Task task) {
        if (task.getProcessingStatus() != TaskStatus.LOCKED) {
            TaskStatus newTaskStatus = TaskStatus.getStatusFromValue(task.getProcessingStatus().getValue() - 1);
            task.setProcessingStatus(newTaskStatus);
        }
    }

    private void activateTasksForClosedTask(Task closedTask) throws DataException, IOException {
        Process process = closedTask.getProcess();

        // check if there are tasks that take place in parallel but are not yet
        // completed
        List<Task> tasks = process.getTasks();
        List<Task> concurrentTasksForOpen = getConcurrentTasksForOpen(tasks, closedTask);

        if (concurrentTasksForOpen.isEmpty() && !isAnotherTaskInWorkWhichBlocksOtherTasks(tasks, closedTask)) {
            if (!closedTask.isLast()) {
                activateNextTasks(getAllHigherTasks(tasks, closedTask));
            }
        } else {
            activateConcurrentTasks(concurrentTasksForOpen);
        }

        URI imagesOrigDirectory = ServiceManager.getProcessService().getImagesOriginDirectory(true, process);
        Integer numberOfFiles = ServiceManager.getFileService().getNumberOfFiles(imagesOrigDirectory);
        if (!process.getSortHelperImages().equals(numberOfFiles)) {
            process.setSortHelperImages(numberOfFiles);
            ServiceManager.getProcessService().save(process);
        }

        updateProcessSortHelperStatus(process);

        for (Task automaticTask : automaticTasks) {
            automaticTask.setProcessingBegin(new Date());
            TaskScriptThread thread = new TaskScriptThread(automaticTask);
            TaskManager.addTask(thread);
        }
        for (Task finish : tasksToFinish) {
            close(finish);
        }
    }

    private void closeTasksBetweenCurrentAndCorrectionTask(Task currentTask, Task correctionTask) throws DataException {
        List<Task> allTasksInBetween = taskService.getAllTasksInBetween(correctionTask.getOrdering(),
            currentTask.getOrdering(), currentTask.getProcess().getId());
        for (Task taskInBetween : allTasksInBetween) {
            taskInBetween.setProcessingStatus(TaskStatus.LOCKED);
            taskInBetween.setCorrection(true);
            taskInBetween.setProcessingEnd(null);
            taskService.save(taskInBetween);
        }
    }

    private void closeTasksBetweenCurrentAndCorrectionTask(Task currentTask, Task correctionTask, Date date)
            throws DataException {
        List<Task> allTasksInBetween = taskService.getAllTasksInBetween(currentTask.getOrdering(),
            correctionTask.getOrdering(), currentTask.getProcess().getId());
        for (Task taskInBetween : allTasksInBetween) {
            taskInBetween.setProcessingStatus(TaskStatus.DONE);
            taskInBetween.setProcessingEnd(date);
            taskInBetween.setCorrection(false);
            taskService.save(taskInBetween);
        }
    }

    private void openTaskForProcessing(Task correctionTask) throws DataException {
        correctionTask.setProcessingStatus(TaskStatus.OPEN);
        correctionTask.setCorrection(true);
        correctionTask.setProcessingEnd(null);
        correctionTask.setProcessingTime(new Date());
        taskService.save(correctionTask);
    }

    private List<Task> getAllHigherTasks(List<Task> tasks, Task task) {
        List<Task> allHigherTasks = new ArrayList<>();
        for (Task tempTask : tasks) {
            if (tempTask.getOrdering() > task.getOrdering()) {
                allHigherTasks.add(tempTask);
            }
        }
        return allHigherTasks;
    }

    private List<Task> getConcurrentTasksForClose(List<Task> tasks, Task task) {
        List<Task> allConcurrentTasks = new ArrayList<>();
        for (Task tempTask : tasks) {
            if (tempTask.getOrdering().equals(task.getOrdering()) && tempTask.getProcessingStatus().getValue() < 2
                    && !tempTask.getId().equals(task.getId()) && !tempTask.isConcurrent()) {
                allConcurrentTasks.add(tempTask);
            }
        }
        return allConcurrentTasks;
    }

    private List<Task> getConcurrentTasksForOpen(List<Task> tasks, Task task) {
        boolean blocksOtherTasks = isAnotherTaskInWorkWhichBlocksOtherTasks(tasks, task);

        List<Task> allConcurrentTasks = new ArrayList<>();
        for (Task tempTask : tasks) {
            if (tempTask.getOrdering().equals(task.getOrdering()) && tempTask.getProcessingStatus().getValue() < 3
                    && !tempTask.getId().equals(task.getId())) {
                if (blocksOtherTasks) {
                    if (tempTask.isConcurrent()) {
                        allConcurrentTasks.add(tempTask);
                    }
                } else {
                    allConcurrentTasks.add(tempTask);
                }
            }
        }
        return allConcurrentTasks;
    }

    private boolean isAnotherTaskInWorkWhichBlocksOtherTasks(List<Task> tasks, Task task) {
        for (Task tempTask : tasks) {
            if (tempTask.getOrdering().equals(task.getOrdering()) && tempTask.getProcessingStatus() == TaskStatus.INWORK
                    && !tempTask.getId().equals(task.getId()) && !tempTask.isConcurrent()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Activate the concurrent tasks.
     */
    private void activateConcurrentTasks(List<Task> concurrentTasks) throws DataException, IOException {
        for (Task concurrentTask : concurrentTasks) {
            if (concurrentTask.getProcessingStatus().equals(TaskStatus.LOCKED)) {
                activateTask(concurrentTask);
            }
        }
    }

    /**
     * If no open parallel tasks are available, activate the next tasks.
     */
    public void activateNextTasks(List<Task> allHigherTasks) throws DataException, IOException {
        List<Task> nextTasks = getNextTasks(allHigherTasks);

        for (Task nextTask : nextTasks) {
            activateTask(nextTask);
        }
    }

    private List<Task> getNextTasks(List<Task> allHigherTasks) {
        int ordering = 0;
        boolean matched = false;

        List<Task> nextTasks = new ArrayList<>();
        for (Task higherTask : allHigherTasks) {
            if (ordering < higherTask.getOrdering() && !matched) {
                ordering = higherTask.getOrdering();
            }

            if (ordering == higherTask.getOrdering() && higherTask.getProcessingStatus().getValue() < 2) {
                nextTasks.add(higherTask);
                matched = true;
            }
        }
        return nextTasks;
    }

    /**
     * If no open parallel tasks are available, activate the next tasks.
     */
    private void activateTask(Task task) throws DataException, IOException {
        if (isWorkflowConditionFulfilled(task.getProcess(), task.getWorkflowCondition())) {
            // activate the task if it is not fully automatic
            task.setProcessingStatus(TaskStatus.OPEN);
            task.setProcessingTime(new Date());
            task.setEditType(TaskEditType.AUTOMATIC);

            verifyTask(task);

            taskService.save(task);
        } else {
            // close task as it is not going to be executed
            task.setProcessingStatus(TaskStatus.DONE);
            task.setProcessingTime(new Date());
            task.setProcessingEnd(new Date());
            task.setEditType(TaskEditType.AUTOMATIC);

            taskService.save(task);

            activateTasksForClosedTask(task);
        }
    }

    private boolean isWorkflowConditionFulfilled(Process process, WorkflowCondition workflowCondition)
            throws IOException {
        if (Objects.isNull(workflowCondition)) {
            return true;
        } else {
            if (workflowCondition.getType().equals(WorkflowConditionType.SCRIPT)) {
                return runScriptCondition(workflowCondition.getValue());
            }

            if (workflowCondition.getType().equals(WorkflowConditionType.XPATH)) {
                return runXPathCondition(process, workflowCondition.getValue());
            }
            return true;
        }
    }

    private boolean runScriptCondition(String scriptPath) throws IOException {
        CommandResult commandResult = ServiceManager.getCommandService().runCommand(new File(scriptPath));
        return commandResult.isSuccessful();
    }

    private boolean runXPathCondition(Process process, String xpath) throws IOException {
        try (InputStream fileInputStream = ServiceManager.getFileService().readMetadataFile(process)) {
            DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
            builderFactory.setNamespaceAware(true);
            DocumentBuilder builder = builderFactory.newDocumentBuilder();
            Document xmlDocument = builder.parse(fileInputStream);

            XPath xPath = XPathFactory.newInstance().newXPath();
            xPath.setNamespaceContext(new KitodoNamespaceContext());
            NodeList nodeList = (NodeList) xPath.compile(xpath).evaluate(xmlDocument, XPathConstants.NODESET);
            return nodeList.getLength() > 0;
        } catch (ParserConfigurationException | SAXException | XPathExpressionException e) {
            logger.error(e.getMessage(), e);
            throw new IOException(e);
        }
    }

    private void verifyTask(Task task) {
        // if it is an automatic task with script
        if (task.isTypeAutomatic()) {
            task.setProcessingStatus(TaskStatus.INWORK);
            automaticTasks.add(task);
        } else if (task.isTypeAcceptClose()) {
            tasksToFinish.add(task);
        }
    }

    /**
     * Update process sort helper status.
     *
     * @param process
     *            object
     */
    private void updateProcessSortHelperStatus(Process process) throws DataException {
        try {
            process = ServiceManager.getProcessService().getById(process.getId());
        } catch (DAOException e) {
            logger.error("Refreshing of process not possible: " + e.getMessage(), e);
        }
        String value = ServiceManager.getProcessService().getProgress(process.getTasks(), null);
        process.setSortHelperStatus(value);
        ServiceManager.getProcessService().save(process);
    }

    /**
     * Download to user home directory.
     *
     * @param task
     *            object
     */
    private void downloadToHome(Task task) {
        task.setProcessingTime(new Date());
        if (ServiceManager.getSecurityAccessService().isAuthenticated()) {
            taskService.replaceProcessingUser(task, getCurrentUser());
            this.webDav.downloadToHome(task.getProcess(), !task.isTypeImagesWrite());
        }
    }

    private User getCurrentUser() {
        return ServiceManager.getUserService().getCurrentUser();
    }
}
