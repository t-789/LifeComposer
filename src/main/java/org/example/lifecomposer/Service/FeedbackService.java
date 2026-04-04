package org.example.lifecomposer.service;

import org.example.lifecomposer.Repository.FeedbackRepository;
import org.example.lifecomposer.entity.Feedback;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.List;

@Service
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;

    public FeedbackService(FeedbackRepository feedbackRepository) {
        this.feedbackRepository = feedbackRepository;
    }

    public boolean saveUserFeedback(Integer userId, String username, String content, String url, String userAgent) {
        Feedback feedback = new Feedback();

        // FIX: old code had a subtle bug: it first set userId=-1 when null,
        // then assigned null back immediately. Here we normalize once.
        Integer normalizedUserId = userId == null ? -1 : userId;

        feedback.setUserId(normalizedUserId);
        feedback.setUsername(username == null ? "Anonymous" : username);
        feedback.setContent(content == null || content.isBlank() ? "用户未提供具体反馈内容" : content);
        feedback.setType("user");
        feedback.setUrl(url);
        feedback.setUserAgent(userAgent);
        feedback.setCreateTime(new Timestamp(System.currentTimeMillis()));
        feedback.setResolved(false);

        return feedbackRepository.saveFeedback(feedback);
    }

    public boolean saveSystemFeedback(Integer userId, String username, String content, String url, String userAgent, String stackTrace) {
        Feedback feedback = new Feedback();
        feedback.setUserId(userId == null ? -1 : userId);
        feedback.setUsername(username == null ? "Anonymous" : username);
        feedback.setContent(content == null || content.isBlank() ? "未提供错误详情" : content);
        feedback.setType("system");
        feedback.setUrl(url);
        feedback.setUserAgent(userAgent);
        feedback.setStackTrace(stackTrace);
        feedback.setCreateTime(new Timestamp(System.currentTimeMillis()));
        feedback.setResolved(false);

        return feedbackRepository.saveFeedback(feedback);
    }

    public List<Feedback> getAllFeedback() {
        return feedbackRepository.getAllFeedback();
    }

    public List<Feedback> getFeedbackByType(String type) {
        return feedbackRepository.getFeedbackByType(type);
    }

    public boolean updateResolvedStatus(int feedbackId, boolean resolved, String resolvedBy) {
        return feedbackRepository.updateResolvedStatus(feedbackId, resolved, resolvedBy);
    }
}
