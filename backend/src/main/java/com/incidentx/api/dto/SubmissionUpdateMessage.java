package com.incidentx.api.dto;

import com.incidentx.api.model.Submission;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SubmissionUpdateMessage {
    Long id;
    String status;
    String results;
    String aiFeedback;

    public static SubmissionUpdateMessage from(Submission submission) {
        return SubmissionUpdateMessage.builder()
                .id(submission.getId())
                .status(submission.getStatus())
                .results(submission.getResults())
                .aiFeedback(submission.getAiFeedback())
                .build();
    }
}
