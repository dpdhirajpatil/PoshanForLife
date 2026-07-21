export interface AppNotification {
  id: string;
  type: string;
  title: string;
  message: string;
  read: boolean;
  relatedEntityType: string | null;
  relatedEntityId: string | null;
  createdAt: string;
}

export interface NotificationListResponse {
  notifications: AppNotification[];
  unreadCount: number;
}
