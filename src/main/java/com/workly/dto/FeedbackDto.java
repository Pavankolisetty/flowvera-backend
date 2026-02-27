package com.workly.dto;

public class FeedbackDto {
    private long id;
    private String text;
    private String submittedBy;
    private String timestamp;
    private int count;

    public FeedbackDto() {}

    public FeedbackDto(long id, String text, String submittedBy, String timestamp, int count) {
        this.id = id;
        this.text = text;
        this.submittedBy = submittedBy;
        this.timestamp = timestamp;
        this.count = count;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getSubmittedBy() {
        return submittedBy;
    }

    public void setSubmittedBy(String submittedBy) {
        this.submittedBy = submittedBy;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }
}
