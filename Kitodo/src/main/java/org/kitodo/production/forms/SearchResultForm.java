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

package org.kitodo.production.forms;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import javax.enterprise.context.SessionScoped;
import javax.inject.Named;

import org.kitodo.data.database.enums.TaskStatus;
import org.kitodo.data.exceptions.DataException;
import org.kitodo.production.dto.ProcessDTO;
import org.kitodo.production.dto.ProjectDTO;
import org.kitodo.production.dto.TaskDTO;
import org.kitodo.production.helper.Helper;
import org.kitodo.production.services.ServiceManager;
import org.kitodo.production.services.data.ProcessService;

@Named("SearchResultForm")
@SessionScoped
public class SearchResultForm extends BaseForm {

    private String searchQuery;
    private List<ProcessDTO> filteredList = new ArrayList<>();
    private List<ProcessDTO> resultList;
    private String currentTaskFilter;
    private Integer currentProjectFilter;
    private Integer currentTaskStatusFilter;

    private String searchResultListPath = MessageFormat.format(REDIRECT_PATH, "searchResult");

    /**
     * Searches for processes with the entered searchQuery.
     *
     * @return The searchResultPage
     */
    public String searchForProcessesBySearchQuery() {
        ProcessService processService = ServiceManager.getProcessService();
        HashMap<Integer, ProcessDTO> resultHash = new HashMap<>();
        List<ProcessDTO> results;
        try {
            results = processService.findByAnything(searchQuery);
            for (ProcessDTO processDTO : results) {
                resultHash.put(processDTO.getId(), processDTO);
            }
            resultList = new ArrayList<>(resultHash.values());
            refreshFilteredList();
        } catch (DataException e) {
            Helper.setErrorMessage("errorOnSearch", searchQuery);
            return this.stayOnCurrentPage;
        }
        setCurrentTaskStatusFilter(null);
        setCurrentProjectFilter(null);
        setCurrentTaskFilter(null);
        return searchResultListPath;
    }

    /**
     * Filters the searchResults by project.
     */
    void filterListByProject() {
        if (Objects.nonNull(currentProjectFilter)) {
            for (ProcessDTO result : new ArrayList<>(filteredList)) {
                if (!result.getProject().getId().equals(currentProjectFilter)) {
                    filteredList.remove(result);
                }
            }
        }
    }

    /**
     * Filters the searchResults by task and status.
     */
    void filterListByTaskAndStatus() {
        if (Objects.nonNull(currentTaskFilter) && Objects.nonNull(currentTaskStatusFilter)) {
            for (ProcessDTO processDTO : new ArrayList<>(filteredList)) {
                boolean remove = true;
                for (TaskDTO task : processDTO.getTasks()) {
                    if (task.getTitle().equalsIgnoreCase(currentTaskFilter)
                            && task.getProcessingStatus().getValue().equals(currentTaskStatusFilter)) {
                        remove = false;
                        break;
                    }
                }
                if (remove) {
                    filteredList.remove(processDTO);
                }

            }
        }
    }

    /**
     * Filters the searchResultList by the selected filters.
     */
    public void filterList() {
        refreshFilteredList();

        filterListByProject();
        filterListByTaskAndStatus();
    }

    /**
     * Get all Projects assigned to the search results.
     *
     * @return A list of projects for filter list
     */
    public Collection<ProjectDTO> getProjectsForFiltering() {
        HashMap<Integer, ProjectDTO> projectsForFiltering = new HashMap<>();
        for (ProcessDTO process : resultList) {
            projectsForFiltering.put(process.getProject().getId(), process.getProject());
        }
        return projectsForFiltering.values();
    }

    /**
     * Get all current Tasks from to the search results.
     *
     * @return A list of tasks for filter list
     */
    public Collection<TaskDTO> getTasksForFiltering() {
        HashMap<String, TaskDTO> tasksForFiltering = new HashMap<>();
        for (ProcessDTO processDTO : resultList) {
            for (TaskDTO currentTask : processDTO.getTasks()) {
                tasksForFiltering.put(currentTask.getTitle(), currentTask);
            }
        }
        return tasksForFiltering.values();
    }

    /**
     * Get the values of taskStatus.
     * @return a list of status
     */
    public Collection<TaskStatus> getTaskStatusForFiltering() {
        return Arrays.asList(TaskStatus.values());
    }

    private void refreshFilteredList() {
        filteredList.clear();
        filteredList.addAll(resultList);
    }

    /**
     * Gets the filtered list.
     *
     * @return a list of ProcessDTO
     */
    public List<ProcessDTO> getFilteredList() {
        return filteredList;
    }

    /**
     * Sets the filtered list.
     *
     * @param filteredList
     *            a list of ProcessDTO
     */
    public void setFilteredList(List<ProcessDTO> filteredList) {
        this.filteredList = filteredList;
    }

    /**
     * Get currentTaskStatusFilter.
     *
     * @return value of currentTaskStatusFilter
     */
    public Integer getCurrentTaskStatusFilter() {
        return currentTaskStatusFilter;
    }

    /**
     * Set currentTaskStatusFilter.
     *
     * @param currentTaskStatusFilter as java.lang.Integer
     */
    public void setCurrentTaskStatusFilter(Integer currentTaskStatusFilter) {
        this.currentTaskStatusFilter = currentTaskStatusFilter;
    }

    /**
     * Gets the search query.
     *
     * @return the search query
     */
    public String getSearchQuery() {
        return searchQuery;
    }

    /**
     * Sets the searchQuery.
     *
     * @param searchQuery
     *            the query to search for
     */
    public void setSearchQuery(String searchQuery) {
        this.searchQuery = searchQuery;
    }

    /**
     * Gets the current task filter.
     *
     * @return a List of Task title to filter
     */
    public String getCurrentTaskFilter() {
        return currentTaskFilter;
    }

    /**
     * Sets the current task filter.
     *
     * @param currentTaskFilter
     *            the task title to filter
     */
    public void setCurrentTaskFilter(String currentTaskFilter) {
        this.currentTaskFilter = currentTaskFilter;
    }

    /**
     * Gets the current project filter.
     *
     * @return a List of project id to filter
     */
    public Integer getCurrentProjectFilter() {
        return currentProjectFilter;
    }

    /**
     * Sets the current project filter.
     *
     * @param currentProjectFilter
     *            the project id to filter
     */
    public void setCurrentProjectFilter(Integer currentProjectFilter) {
        this.currentProjectFilter = currentProjectFilter;
    }

}
