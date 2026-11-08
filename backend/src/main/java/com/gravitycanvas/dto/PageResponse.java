package com.gravitycanvas.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Generic paginated response wrapper containing data content and pagination metadata")
public class PageResponse<T> {

    @Schema(description = "List of items on the current page")
    private List<T> content;

    @Schema(description = "Current page index (0-based)", example = "0")
    private int pageNumber;

    @Schema(description = "Requested page size", example = "20")
    private int pageSize;

    @Schema(description = "Total number of elements matching the filter criteria across all pages", example = "42")
    private long totalElements;

    @Schema(description = "Total number of available pages", example = "3")
    private int totalPages;

    @Schema(description = "True if this is the first page", example = "true")
    private boolean first;

    @Schema(description = "True if this is the last page", example = "false")
    private boolean last;

    @Schema(description = "True if the page contains zero items", example = "false")
    private boolean empty;

    public PageResponse() {}

    public PageResponse(List<T> content, int pageNumber, int pageSize, long totalElements, int totalPages) {
        this.content = content != null ? content : List.of();
        this.pageNumber = pageNumber;
        this.pageSize = pageSize;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.first = pageNumber == 0;
        this.last = pageNumber >= totalPages - 1 || totalPages == 0;
        this.empty = this.content.isEmpty();
    }

    public static <T> PageResponse<T> empty(int pageNumber, int pageSize, long totalElements) {
        int totalPages = pageSize > 0 ? (int) Math.ceil((double) totalElements / pageSize) : 0;
        return new PageResponse<>(List.of(), pageNumber, pageSize, totalElements, totalPages);
    }

    public List<T> getContent() { return content; }
    public void setContent(List<T> content) { 
        this.content = content != null ? content : List.of(); 
        this.empty = this.content.isEmpty();
    }

    public int getPageNumber() { return pageNumber; }
    public void setPageNumber(int pageNumber) { this.pageNumber = pageNumber; }

    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }

    public long getTotalElements() { return totalElements; }
    public void setTotalElements(long totalElements) { this.totalElements = totalElements; }

    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }

    public boolean isFirst() { return first; }
    public void setFirst(boolean first) { this.first = first; }

    public boolean isLast() { return last; }
    public void setLast(boolean last) { this.last = last; }

    public boolean getHasNext() { return totalPages > 0 && pageNumber < totalPages - 1; }

    public boolean getHasPrevious() { return pageNumber > 0 && totalPages > 0; }

    public boolean isEmpty() { return empty; }
    public void setEmpty(boolean empty) { this.empty = empty; }
}
