package pe.ayni.notifications.application;

import java.util.List;

/**
 * A page of somebody's notices, with how many they have not read in all, whatever the page shows.
 */
public record MyNotices(List<NoticeView> items, int page, int size, long total, long unreadCount) {}
